package tachiyomix.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * KSP Processor that generates JavaScript initialization files for iOS/Web support.
 * 
 * For each source annotated with @Extension, this processor generates:
 * 1. A concrete implementation class (JsExtension) that extends the abstract source
 * 2. An init function with @JsExport that registers the source with SourceRegistry
 * 3. Helper functions for standalone usage
 * 
 * Generated file structure:
 * ```
 * package <source.package>.js
 * 
 * class JsExtension(deps: Dependencies) : <SourceClass>(deps)
 * 
 * @JsExport
 * fun init<SourceName>(): dynamic { ... }
 * 
 * @JsExport
 * fun createSource(deps: Dependencies): JsExtension { ... }
 * 
 * @JsExport
 * fun getSourceInfo(): dynamic { ... }
 * ```
 */
class JsExtensionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()
        
        // Get all classes annotated with the Extension annotation
        val extensions = resolver.getSymbolsWithAnnotation(EXTENSION_FQ_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        if (extensions.isEmpty()) {
            return emptyList()
        }

        // Find the most derived extension class
        val extension = getClassToGenerate(extensions) ?: return emptyList()

        val buildDir = getBuildDir()
        val variant = getVariant(buildDir)
        
        // Only generate for release builds to avoid duplicates
        if (!buildDir.contains("Release", ignoreCase = true) && 
            !buildDir.contains("release", ignoreCase = true)) {
            // Still mark as processed to avoid re-running
            processed = true
            return emptyList()
        }

        val arguments = parseArguments(variant)

        // Check if JS generation is enabled for this extension
        if (!arguments.enableJs) {
            logger.info("JS generation disabled for ${arguments.name}, skipping")
            processed = true
            return emptyList()
        }

        // Check that the extension is open or abstract
        if (!extension.isOpen()) {
            logger.warn("[$extension] is not open or abstract, skipping JS generation")
            return emptyList()
        }

        // Generate the JS extension file
        val dependencies = resolver.getClassDeclarationByName(DEPENDENCIES_FQ_CLASS)
        if (dependencies == null) {
            logger.warn("Dependencies class not found, skipping JS generation")
            return emptyList()
        }

        logger.info("Generating JS init file for ${arguments.name}")
        extension.accept(JsSourceVisitor(arguments, dependencies), Unit)
        processed = true

        return emptyList()
    }

    /**
     * Returns the source class to generate, or null if more than one was detected and they don't
     * inherit each other.
     */
    private fun getClassToGenerate(candidates: List<KSClassDeclaration>): KSClassDeclaration? {
        return when (candidates.size) {
            0 -> null
            1 -> candidates.first()
            else -> {
                candidates.find { candidate ->
                    val type = candidate.asStarProjectedType()
                    candidates.all { it === candidate || it.asStarProjectedType().isAssignableFrom(type) }
                }
            }
        }
    }

    private inner class JsSourceVisitor(
        val arguments: Arguments,
        val dependencies: KSClassDeclaration,
    ) : KSVisitorVoid() {
        
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val sourceClassName = classDeclaration.toClassName()
            val sourcePackage = sourceClassName.packageName
            val jsPackage = "$sourcePackage.js"
            val sourceName = arguments.name.replace(" ", "").replace("(", "").replace(")", "")
            val sourceId = arguments.id
            
            // Annotations
            val jsExportAnnotation = AnnotationSpec.builder(ClassName("kotlin.js", "JsExport")).build()
            val optInAnnotation = AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                .addMember("%T::class", ClassName("kotlin.js", "ExperimentalJsExport"))
                .build()

            // 1. Create concrete JsExtension class
            val jsExtensionClass = TypeSpec.classBuilder(JS_EXTENSION_CLASS)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("deps", dependencies.toClassName())
                        .build()
                )
                .superclass(sourceClassName)
                .addSuperclassConstructorParameter("%L", "deps")
                .addKdoc("""
                    Concrete implementation of ${sourceClassName.simpleName} for JS runtime.
                    Generated by JsExtensionProcessor.
                """.trimIndent())
                .build()

            // 2. Create SourceInfo object (use String for id to avoid Long/BigInt issues)
            val sourceInfoObject = TypeSpec.objectBuilder("${sourceName}Info")
                .addAnnotation(jsExportAnnotation)
                .addProperty(
                    PropertySpec.builder("id", String::class)
                        .initializer("%S", sourceId.toString())
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("name", String::class)
                        .initializer("%S", arguments.name)
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("lang", String::class)
                        .initializer("%S", arguments.lang)
                        .build()
                )
                .addKdoc("Source metadata for ${arguments.name}")
                .build()

            // 3. Create init function
            val initFunction = FunSpec.builder("init$sourceName")
                .addAnnotation(jsExportAnnotation)
                .returns(ClassName("kotlin", "Any").copy(nullable = true))
                .addKdoc("""
                    Initialize ${arguments.name} source for iOS/JS runtime.
                    
                    Call this function after loading the JS bundle to register the source.
                    Requires runtime.js from source-runtime-js to be loaded first.
                    
                    @return Source info object with registration status
                """.trimIndent())
                .addStatement("console.log(%S)", "${arguments.name}: Initializing source...")
                .addCode("""
                    |js(${'"'}${'"'}${'"'}
                    |    if (typeof SourceRegistry !== 'undefined') {
                    |        SourceRegistry.register('${sourceName.lowercase()}', function(deps) {
                    |            return new $jsPackage.$JS_EXTENSION_CLASS(deps);
                    |        });
                    |        console.log('${arguments.name}: Registered with SourceRegistry');
                    |    } else {
                    |        console.warn('${arguments.name}: SourceRegistry not found. Load runtime.js first.');
                    |    }
                    |${'"'}${'"'}${'"'})
                    |
                """.trimMargin())
                .addStatement("""
                    |return js(${'"'}${'"'}${'"'}({
                    |    id: "$sourceId",
                    |    name: "${arguments.name}",
                    |    lang: "${arguments.lang}",
                    |    registered: typeof SourceRegistry !== 'undefined'
                    |})${'"'}${'"'}${'"'})
                """.trimMargin())
                .build()

            // 4. Create createSource function for standalone usage (not exported due to non-exportable types)
            val createSourceFunction = FunSpec.builder("createSource")
                .addParameter("deps", dependencies.toClassName())
                .returns(ClassName(jsPackage, JS_EXTENSION_CLASS))
                .addKdoc("""
                    Create a source instance directly (for standalone use without SourceRegistry).
                    Note: Not exported to JS due to non-exportable parameter types.
                    Use init${sourceName}() instead for JS interop.
                    
                    @param deps Dependencies object (HttpClients, PreferenceStore)
                    @return The source instance
                """.trimIndent())
                .addStatement("return %T(deps)", ClassName(jsPackage, JS_EXTENSION_CLASS))
                .build()

            // 5. Create getSourceInfo function
            val getSourceInfoFunction = FunSpec.builder("getSourceInfo")
                .addAnnotation(jsExportAnnotation)
                .returns(ClassName("kotlin", "Any").copy(nullable = true))
                .addKdoc("""
                    Get source metadata without initializing.
                    Useful for displaying source info before loading.
                    
                    @return Source info object
                """.trimIndent())
                .addStatement("""
                    |return js(${'"'}${'"'}${'"'}({
                    |    id: "$sourceId",
                    |    name: "${arguments.name}",
                    |    lang: "${arguments.lang}"
                    |})${'"'}${'"'}${'"'})
                """.trimMargin())
                .build()

            // Build the file
            val fileSpec = FileSpec.builder(jsPackage, "JsInit")
                .addFileComment("""
                    JavaScript initialization for ${arguments.name} source.
                    Generated by JsExtensionProcessor - DO NOT EDIT.
                    
                    This file provides:
                    - JsExtension: Concrete implementation for JS runtime
                    - init$sourceName(): Register source with SourceRegistry
                    - createSource(): Create source instance directly
                    - getSourceInfo(): Get source metadata
                    
                    Usage from iOS:
                    1. Load runtime.js (from source-runtime-js)
                    2. Load this source's JS bundle
                    3. Call init$sourceName() to register
                    4. Use SourceBridge to interact with the source
                """.trimIndent())
                .addAnnotation(
                    AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                        .addMember("%T::class", ClassName("kotlin.js", "ExperimentalJsExport"))
                        .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                        .build()
                )
                .addType(jsExtensionClass)
                .addType(sourceInfoObject)
                .addFunction(initFunction)
                .addFunction(createSourceFunction)
                .addFunction(getSourceInfoFunction)
                .build()

            fileSpec.writeTo(codeGenerator, Dependencies(false, classDeclaration.containingFile!!))
            
            logger.info("Generated JS init file for ${arguments.name} at $jsPackage.JsInit")
        }
    }

    private fun String.convertToOsPath(): String {
        return if (System.getProperty("os.name").contains("win", true)) {
            this.replace("\\", "/")
        } else {
            this
        }
    }

    private fun CodeGenerator.collectVariantName(fileName: String): String {
        createNewFileByPath(Dependencies(false), fileName, "txt")
        return generatedFile.first().run {
            this.path.substringBefore("\\resources\\").substringBefore("/resources/")
        }
    }

    private fun getBuildDir(): String {
        return codeGenerator.collectVariantName("js_").convertToOsPath()
    }

    private fun getVariant(buildDir: String): String {
        val build = buildDir.substringAfterLast("/ksp/").substringBefore('/')
        return build.removeSuffix("Debug").removeSuffix("Release")
    }

    private fun parseArguments(variant: String): Arguments {
        return Arguments(
            name = options["${variant}_name"] ?: "Unknown",
            lang = options["${variant}_lang"] ?: "en",
            id = options["${variant}_id"]?.toLongOrNull() ?: 0L,
            hasDeeplinks = options["${variant}_has_deeplinks"].toBoolean(),
            enableJs = options["${variant}_enable_js"].toBoolean()
        )
    }

    private data class Arguments(
        val name: String,
        val lang: String,
        val id: Long,
        val hasDeeplinks: Boolean,
        val enableJs: Boolean
    )

    private companion object {
        const val DEPENDENCIES_FQ_CLASS = "ireader.core.source.Dependencies"
        const val EXTENSION_FQ_ANNOTATION = "tachiyomix.annotations.Extension"
        const val JS_EXTENSION_CLASS = "JsExtension"
    }
}

class JsExtensionProcessorFactory : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return JsExtensionProcessor(environment.codeGenerator, environment.logger, environment.options)
    }
}
