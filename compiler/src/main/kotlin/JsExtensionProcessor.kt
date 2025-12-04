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

            // 1. Create concrete JsExtension class (platform-agnostic, works on Android too)
            val jsExtensionClass = TypeSpec.classBuilder(JS_EXTENSION_CLASS)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("deps", dependencies.toClassName())
                        .build()
                )
                .superclass(sourceClassName)
                .addSuperclassConstructorParameter("%L", "deps")
                .addKdoc("""
                    Concrete implementation of ${sourceClassName.simpleName}.
                    Generated by JsExtensionProcessor.
                    
                    On JS: Used with SourceRegistry for iOS runtime.
                    On JVM/Android: Can be used directly if needed.
                """.trimIndent())
                .build()

            // 2. Create SourceInfo object (platform-agnostic)
            val sourceInfoObject = TypeSpec.objectBuilder("${sourceName}Info")
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

            // 3. Create createSource function (platform-agnostic)
            val createSourceFunction = FunSpec.builder("createSource")
                .addParameter("deps", dependencies.toClassName())
                .returns(ClassName(jsPackage, JS_EXTENSION_CLASS))
                .addKdoc("""
                    Create a source instance directly.
                    
                    @param deps Dependencies object (HttpClients, PreferenceStore)
                    @return The source instance
                """.trimIndent())
                .addStatement("return %T(deps)", ClassName(jsPackage, JS_EXTENSION_CLASS))
                .build()

            // Build the platform-agnostic file (works on Android, JVM, JS)
            val fileSpec = FileSpec.builder(jsPackage, "JsInit")
                .addFileComment("""
                    Source initialization for ${arguments.name}.
                    Generated by JsExtensionProcessor - DO NOT EDIT.
                    
                    This file provides:
                    - JsExtension: Concrete implementation class
                    - ${sourceName}Info: Source metadata
                    - createSource(): Create source instance
                    
                    For JS/iOS usage, see the js-sources module which compiles
                    these sources with JS-specific registration code.
                """.trimIndent())
                .addType(jsExtensionClass)
                .addType(sourceInfoObject)
                .addFunction(createSourceFunction)
                .build()

            fileSpec.writeTo(codeGenerator, Dependencies(false, classDeclaration.containingFile!!))
            
            logger.info("Generated JS init file for ${arguments.name} at $jsPackage.JsInit")
            
            // Also generate JS-specific registration file for js-sources module
            generateJsRegistration(classDeclaration, jsPackage, sourceName, sourceId)
        }
        
        private fun generateJsRegistration(
            classDeclaration: KSClassDeclaration,
            jsPackage: String,
            sourceName: String,
            sourceId: Long
        ) {
            val sourceNameLower = sourceName.lowercase()
            
            // JS-specific registration code
            val jsRegistrationCode = """
                |/**
                | * JS-specific registration for ${arguments.name} source.
                | * Generated by JsExtensionProcessor - DO NOT EDIT.
                | * 
                | * This file is only compiled for JS target in js-sources module.
                | */
                |@file:OptIn(ExperimentalJsExport::class)
                |@file:Suppress("UNUSED_VARIABLE")
                |
                |package ireader.js.generated
                |
                |import ireader.core.source.Dependencies
                |import $jsPackage.JsExtension as ${sourceName}Extension
                |import $jsPackage.${sourceName}Info
                |import kotlin.js.ExperimentalJsExport
                |import kotlin.js.JsExport
                |import kotlin.js.JsName
                |
                |/**
                | * Factory function to create ${arguments.name} source.
                | * This ensures the class is included in the bundle.
                | */
                |@JsExport
                |@JsName("create$sourceName")
                |fun create$sourceName(deps: Dependencies): ${sourceName}Extension {
                |    return ${sourceName}Extension(deps)
                |}
                |
                |/**
                | * Get the source class reference (ensures it's included in bundle)
                | */
                |@JsExport
                |@JsName("${sourceName}Class")
                |val ${sourceNameLower}Class: Any = ${sourceName}Extension::class
                |
                |/**
                | * Initialize ${arguments.name} source for iOS/JS runtime.
                | */
                |@JsExport
                |fun init${sourceName}(): dynamic {
                |    // Reference the class to ensure it's included
                |    val sourceClass = ${sourceName}Extension::class
                |    
                |    console.log("${arguments.name}: Initializing source...")
                |    js(${'"'}${'"'}${'"'}
                |        if (typeof SourceRegistry !== 'undefined') {
                |            SourceRegistry.register('$sourceNameLower', function(deps) {
                |                return new $jsPackage.JsExtension(deps);
                |            });
                |            console.log('${arguments.name}: Registered with SourceRegistry');
                |        } else {
                |            console.warn('${arguments.name}: SourceRegistry not found. Load runtime.js first.');
                |        }
                |    ${'"'}${'"'}${'"'})
                |    return js(${'"'}${'"'}${'"'}({
                |        id: "$sourceId",
                |        name: "${arguments.name}",
                |        lang: "${arguments.lang}",
                |        registered: typeof SourceRegistry !== 'undefined'
                |    })${'"'}${'"'}${'"'})
                |}
                |
                |/**
                | * Get source info for ${arguments.name}.
                | */
                |@JsExport
                |fun get${sourceName}Info(): dynamic = js(${'"'}${'"'}${'"'}({
                |    id: "$sourceId",
                |    name: "${arguments.name}",
                |    lang: "${arguments.lang}"
                |})${'"'}${'"'}${'"'})
            """.trimMargin()
            
            // Write to a file that will be picked up by js-sources module
            // Using a special path that js-sources can include
            try {
                codeGenerator.createNewFile(
                    Dependencies(false, classDeclaration.containingFile!!),
                    "ireader.js.generated",
                    "${sourceName}JsRegistration"
                ).bufferedWriter().use { writer ->
                    writer.write(jsRegistrationCode)
                }
                logger.info("Generated JS registration file for ${arguments.name}")
            } catch (e: Exception) {
                logger.warn("Could not generate JS registration file: ${e.message}")
            }
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
