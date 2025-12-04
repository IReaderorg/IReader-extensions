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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * KSP Processor that generates JavaScript initialization files for iOS/Web support.
 * 
 * This processor generates TWO types of files:
 * 
 * 1. Platform-agnostic code (always generated):
 *    - JsExtension: Concrete implementation class
 *    - SourceInfo: Metadata object
 *    - createSource(): Factory function
 * 
 * 2. JS-specific registration code (only when target is JS):
 *    - @JsExport annotated init functions
 *    - SourceRegistry registration
 *    - Dynamic type usage
 * 
 * The processor detects the target platform by examining the build directory path
 * and KSP options to determine if it's running for JS or Android compilation.
 */
class JsExtensionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()
        
        val extensions = resolver.getSymbolsWithAnnotation(EXTENSION_FQ_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        if (extensions.isEmpty()) {
            return emptyList()
        }

        val extension = getClassToGenerate(extensions) ?: return emptyList()

        val buildDir = getBuildDir()
        val variant = getVariant(buildDir)
        
        // Only generate for release builds to avoid duplicates
        if (!buildDir.contains("Release", ignoreCase = true) && 
            !buildDir.contains("release", ignoreCase = true)) {
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

        val dependencies = resolver.getClassDeclarationByName(DEPENDENCIES_FQ_CLASS)
        if (dependencies == null) {
            logger.warn("Dependencies class not found, skipping JS generation")
            return emptyList()
        }

        // Detect if we're building for JS target
        val isJsTarget = detectJsTarget(buildDir)
        
        logger.info("Generating JS files for ${arguments.name} (isJsTarget=$isJsTarget)")
        extension.accept(JsSourceVisitor(arguments, dependencies, isJsTarget), Unit)
        processed = true

        return emptyList()
    }

    /**
     * Detect if we're building for a JavaScript target.
     * 
     * JS builds typically have paths containing:
     * - "jsMain", "jsTest" (Kotlin/JS source sets)
     * - "js/" or "/js" in the KSP output path
     * - "compileKotlinJs" task indicators
     * 
     * Android builds have paths containing:
     * - "Debug", "Release" with Android variant names
     * - "android" in the path
     */
    private fun detectJsTarget(buildDir: String): Boolean {
        val normalizedPath = buildDir.lowercase().replace("\\", "/")
        
        // Positive indicators for JS target
        val jsIndicators = listOf(
            "/js/",
            "/jsmain/",
            "/jstest/",
            "compilekotlinjs",
            "/kotlin/js/"
        )
        
        // Negative indicators (definitely not JS)
        val androidIndicators = listOf(
            "/android/",
            "assembledebug",
            "assemblerelease",
            "ksp/endebug",
            "ksp/enrelease",
            "ksp/ardebug",
            "ksp/arrelease"
        )
        
        // Check for Android indicators first (more specific)
        if (androidIndicators.any { normalizedPath.contains(it) }) {
            return false
        }
        
        // Check for JS indicators
        return jsIndicators.any { normalizedPath.contains(it) }
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
        val isJsTarget: Boolean
    ) : KSVisitorVoid() {
        
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val sourceClassName = classDeclaration.toClassName()
            val sourcePackage = sourceClassName.packageName
            val jsPackage = "$sourcePackage.js"
            val sourceName = arguments.name.replace(" ", "").replace("(", "").replace(")", "")

            // Always generate platform-agnostic code (compiled by both Android and JS)
            generatePlatformAgnosticCode(classDeclaration, jsPackage, sourceName)
            
            // Generate JS-specific code to a separate directory
            // This will be picked up by js-sources module but not compiled by Android
            generateJsSpecificCode(classDeclaration, jsPackage, sourceName)
        }
        
        /**
         * Generate platform-agnostic code that works on Android, JVM, and JS.
         * This includes the concrete JsExtension class, SourceInfo, and createSource function.
         */
        private fun generatePlatformAgnosticCode(
            classDeclaration: KSClassDeclaration,
            jsPackage: String,
            sourceName: String
        ) {
            val sourceClassName = classDeclaration.toClassName()
            val sourceId = arguments.id

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
                    Concrete implementation of ${sourceClassName.simpleName}.
                    Generated by JsExtensionProcessor.
                    
                    On JS: Used with SourceRegistry for iOS runtime.
                    On JVM/Android: Can be used directly if needed.
                """.trimIndent())
                .build()

            // 2. Create SourceInfo object
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

            // 3. Create createSource function
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

            // Build the platform-agnostic file
            val fileSpec = FileSpec.builder(jsPackage, "JsInit")
                .addFileComment("""
                    Source initialization for ${arguments.name}.
                    Generated by JsExtensionProcessor - DO NOT EDIT.
                    
                    This file provides platform-agnostic code:
                    - JsExtension: Concrete implementation class
                    - ${sourceName}Info: Source metadata
                    - createSource(): Create source instance
                    
                    For JS/iOS, additional registration code is generated
                    in JsRegistration.kt when building for JS target.
                """.trimIndent())
                .addType(jsExtensionClass)
                .addType(sourceInfoObject)
                .addFunction(createSourceFunction)
                .build()

            fileSpec.writeTo(codeGenerator, Dependencies(false, classDeclaration.containingFile!!))
            
            logger.info("Generated platform-agnostic JS init file for ${arguments.name}")
        }
        
        /**
         * Generate JS-specific registration code with @JsExport annotations.
         * 
         * This code uses JS-only constructs (dynamic, js(), console) and is written
         * to a separate 'js-generated' directory that:
         * - Is included by js-sources module for JS compilation
         * - Is NOT included by Android source sets (avoiding compilation errors)
         * 
         * The file is written using createNewFileByPath to place it in a custom location.
         */
        private fun generateJsSpecificCode(
            classDeclaration: KSClassDeclaration,
            jsPackage: String,
            sourceName: String
        ) {
            val sourceId = arguments.id
            val sourceNameLower = sourceName.lowercase()
            
            // Generate JS-specific registration code as raw string
            // This uses JS-only constructs that can't be generated with KotlinPoet
            val jsRegistrationCode = buildString {
                appendLine("/**")
                appendLine(" * JS-specific registration for ${arguments.name} source.")
                appendLine(" * Generated by JsExtensionProcessor - DO NOT EDIT.")
                appendLine(" * ")
                appendLine(" * This file contains @JsExport functions for iOS/Web runtime.")
                appendLine(" * It is ONLY compiled when building for JavaScript target.")
                appendLine(" * Android builds should NOT include this file.")
                appendLine(" */")
                appendLine("@file:OptIn(ExperimentalJsExport::class)")
                appendLine("@file:Suppress(\"UNUSED_VARIABLE\")")
                appendLine()
                appendLine("package $jsPackage")
                appendLine()
                appendLine("import ireader.core.source.Dependencies")
                appendLine("import kotlin.js.ExperimentalJsExport")
                appendLine("import kotlin.js.JsExport")
                appendLine("import kotlin.js.JsName")
                appendLine()
                appendLine("/**")
                appendLine(" * Initialize ${arguments.name} source for iOS/JS runtime.")
                appendLine(" * Registers the source with SourceRegistry if available.")
                appendLine(" */")
                appendLine("@JsExport")
                appendLine("@JsName(\"init$sourceName\")")
                appendLine("fun init$sourceName(): dynamic {")
                appendLine("    // Reference the class to ensure it's included in bundle")
                appendLine("    val sourceClass = JsExtension::class")
                appendLine("    ")
                appendLine("    console.log(\"${arguments.name}: Initializing source...\")")
                appendLine("    js(\"\"\"")
                appendLine("        if (typeof SourceRegistry !== 'undefined') {")
                appendLine("            SourceRegistry.register('$sourceNameLower', function(deps) {")
                appendLine("                return new $jsPackage.JsExtension(deps);")
                appendLine("            });")
                appendLine("            console.log('${arguments.name}: Registered with SourceRegistry');")
                appendLine("        } else {")
                appendLine("            console.warn('${arguments.name}: SourceRegistry not found. Load runtime.js first.');")
                appendLine("        }")
                appendLine("    \"\"\")")
                appendLine("    return js(\"\"\"({")
                appendLine("        id: \"$sourceId\",")
                appendLine("        name: \"${arguments.name}\",")
                appendLine("        lang: \"${arguments.lang}\",")
                appendLine("        registered: typeof SourceRegistry !== 'undefined'")
                appendLine("    })\"\"\")")
                appendLine("}")
                appendLine()
                appendLine("/**")
                appendLine(" * Factory function to create ${arguments.name} source.")
                appendLine(" */")
                appendLine("@JsExport")
                appendLine("@JsName(\"create$sourceName\")")
                appendLine("fun create${sourceName}Js(deps: Dependencies): JsExtension {")
                appendLine("    return JsExtension(deps)")
                appendLine("}")
                appendLine()
                appendLine("/**")
                appendLine(" * Get source info for ${arguments.name}.")
                appendLine(" */")
                appendLine("@JsExport")
                appendLine("@JsName(\"get${sourceName}Info\")")
                appendLine("fun get${sourceName}InfoJs(): dynamic = js(\"\"\"({")
                appendLine("    id: \"$sourceId\",")
                appendLine("    name: \"${arguments.name}\",")
                appendLine("    lang: \"${arguments.lang}\"")
                appendLine("})\"\"\")")
            }
            
            // Write JS-specific registration file to a separate js-only directory
            // This uses resources output which is NOT compiled by Kotlin
            // The js-sources module will include this directory for JS compilation
            try {
                val packagePath = jsPackage.replace(".", "/")
                codeGenerator.createNewFileByPath(
                    Dependencies(false, classDeclaration.containingFile!!),
                    "js-only/$packagePath/JsRegistration",
                    "kt.txt"  // Use .kt.txt extension so Android doesn't compile it
                ).bufferedWriter().use { writer ->
                    writer.write(jsRegistrationCode)
                }
                logger.info("Generated JS-specific registration file for ${arguments.name}")
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
