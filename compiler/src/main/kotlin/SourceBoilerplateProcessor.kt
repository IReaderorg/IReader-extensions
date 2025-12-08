/*
 * Copyright (C) IReader Project
 * SPDX-License-Identifier: Apache-2.0
 */

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
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.File
import java.security.MessageDigest

/**
 * KSP Processor that reduces boilerplate in source implementations:
 *
 * 1. Auto-generates stable source IDs from name+lang hash
 * 2. Validates and auto-corrects package names based on directory structure
 * 3. Generates common property overrides from @SourceConfig
 * 4. Generates filter and command implementations from annotations
 */
class SourceBoilerplateProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var processed = false
    private val generatedIds = mutableMapOf<String, Long>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        // Process @AutoSourceId annotations
        processAutoSourceId(resolver)

        // Process @SourceConfig annotations
        processSourceConfig(resolver)

        // Process @ValidatePackage annotations
        processPackageValidation(resolver)

        // NOTE: @GenerateFilters and @GenerateCommands are handled by SourceFactoryProcessor
        // which generates them inside the {ClassName}Generated object

        processed = true
        return emptyList()
    }

    private fun processAutoSourceId(resolver: Resolver) {
        val symbols = resolver.getSymbolsWithAnnotation(AUTO_SOURCE_ID_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        symbols.forEach { classDecl ->
            val annotation = classDecl.annotations.first {
                it.shortName.asString() == "AutoSourceId"
            }

            val seed = getAnnotationArgument(annotation, "seed", "")
            val version = getAnnotationArgument(annotation, "version", 1)

            // Extract name and lang from the class
            val (name, lang) = extractNameAndLang(classDecl)

            val idSeed = seed.ifEmpty { name }
            val sourceId = generateSourceId(idSeed, lang, version)

            // Store for reference
            val key = "${classDecl.packageName.asString()}.${classDecl.simpleName.asString()}"
            generatedIds[key] = sourceId

            logger.info("Generated ID for $name ($lang): $sourceId")

            // Generate ID provider extension
            generateIdProvider(classDecl, sourceId)
        }
    }

    private fun processSourceConfig(resolver: Resolver) {
        val symbols = resolver.getSymbolsWithAnnotation(SOURCE_CONFIG_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        symbols.forEach { classDecl ->
            val annotation = classDecl.annotations.first {
                it.shortName.asString() == "SourceConfig"
            }

            val name = getAnnotationArgument(annotation, "name", "")
            val baseUrl = getAnnotationArgument(annotation, "baseUrl", "")
            val lang = getAnnotationArgument(annotation, "lang", "en")
            val explicitId = getAnnotationArgument(annotation, "id", -1L)
            val idSeed = getAnnotationArgument(annotation, "idSeed", "")
            val idVersion = getAnnotationArgument(annotation, "idVersion", 1)

            val sourceId = if (explicitId != -1L) {
                explicitId
            } else {
                generateSourceId(idSeed.ifEmpty { name }, lang, idVersion)
            }

            logger.info("SourceConfig: $name ($lang) @ $baseUrl -> ID: $sourceId")

            // Generate the implementation class
            generateSourceConfigImpl(classDecl, name, baseUrl, lang, sourceId)
        }
    }

    private fun processPackageValidation(resolver: Resolver) {
        // Also validate all @Extension annotated classes
        val extensions = resolver.getSymbolsWithAnnotation(EXTENSION_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        extensions.forEach { classDecl ->
            validateAndCorrectPackage(classDecl)
        }
    }

    private fun validateAndCorrectPackage(classDecl: KSClassDeclaration) {
        val containingFile = classDecl.containingFile ?: return
        val filePath = containingFile.filePath.replace("\\", "/")

        // Skip multisrc
        if ("/sources/multisrc" in filePath || "/multisrc/" in filePath) return

        val currentPackage = classDecl.packageName.asString()
        val expectedPackage = extractExpectedPackage(filePath)

        if (expectedPackage != null && !currentPackage.startsWith(expectedPackage)) {
            logger.warn(
                "Package mismatch in ${classDecl.simpleName.asString()}:\n" +
                "  Current:  $currentPackage\n" +
                "  Expected: $expectedPackage (based on directory structure)\n" +
                "  File: $filePath"
            )

            // Generate a fix suggestion file
            generatePackageFixSuggestion(classDecl, currentPackage, expectedPackage, filePath)
        }
    }

    private fun extractExpectedPackage(filePath: String): String? {
        // Pattern: sources/{lang}/{sourceName}/main/src/ireader/{sourceName}/...
        // Expected package: ireader.{sourceName}

        val sourcesMatch = Regex(".*/sources/[^/]+/([^/]+)/.*").find(filePath)
        if (sourcesMatch != null) {
            val sourceDir = sourcesMatch.groupValues[1]
            return "ireader.$sourceDir"
        }

        // Pattern: sources-v5-batch/{lang}/{sourceName}/...
        val v5Match = Regex(".*/sources-v5-batch/[^/]+/([^/]+)/.*").find(filePath)
        if (v5Match != null) {
            val sourceDir = v5Match.groupValues[1]
            return "ireader.$sourceDir"
        }

        return null
    }

    private fun generatePackageFixSuggestion(
        classDecl: KSClassDeclaration,
        currentPackage: String,
        expectedPackage: String,
        filePath: String
    ) {
        val className = classDecl.simpleName.asString()

        // Generate a .fix file with the correction
        try {
            codeGenerator.createNewFileByPath(
                Dependencies(false, classDecl.containingFile!!),
                "package-fix-${className}",
                "txt"
            ).use { output ->
                val content = """
                    |PACKAGE FIX SUGGESTION
                    |======================
                    |File: $filePath
                    |Class: $className
                    |
                    |Current package:  $currentPackage
                    |Expected package: $expectedPackage
                    |
                    |To fix, change the first line of the source file from:
                    |  package $currentPackage
                    |To:
                    |  package $expectedPackage
                    |
                    |Or rename the directory to match the package name.
                """.trimMargin()
                output.write(content.toByteArray())
            }
        } catch (e: Exception) {
            // File might already exist
        }
    }

    private fun processGenerateFilters(resolver: Resolver) {
        val symbols = resolver.getSymbolsWithAnnotation(GENERATE_FILTERS_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        symbols.forEach { classDecl ->
            val annotation = classDecl.annotations.first {
                it.shortName.asString() == "GenerateFilters"
            }

            val hasTitle = getAnnotationArgument(annotation, "title", true)
            val hasAuthor = getAnnotationArgument(annotation, "author", false)
            val hasSort = getAnnotationArgument(annotation, "sort", false)
            val sortOptions = getAnnotationArrayArgument(annotation, "sortOptions")
            val hasGenre = getAnnotationArgument(annotation, "genre", false)
            val genreOptions = getAnnotationArrayArgument(annotation, "genreOptions")
            val hasStatus = getAnnotationArgument(annotation, "status", false)

            generateFiltersImpl(classDecl, hasTitle, hasAuthor, hasSort, sortOptions, hasGenre, genreOptions, hasStatus)
        }
    }

    private fun processGenerateCommands(resolver: Resolver) {
        val symbols = resolver.getSymbolsWithAnnotation(GENERATE_COMMANDS_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        symbols.forEach { classDecl ->
            val annotation = classDecl.annotations.first {
                it.shortName.asString() == "GenerateCommands"
            }

            val detailFetch = getAnnotationArgument(annotation, "detailFetch", true)
            val contentFetch = getAnnotationArgument(annotation, "contentFetch", true)
            val chapterFetch = getAnnotationArgument(annotation, "chapterFetch", true)
            val webView = getAnnotationArgument(annotation, "webView", false)

            generateCommandsImpl(classDecl, detailFetch, contentFetch, chapterFetch, webView)
        }
    }

    private fun extractNameAndLang(classDecl: KSClassDeclaration): Pair<String, String> {
        var name = classDecl.simpleName.asString()
        var lang = "en"

        classDecl.getAllProperties().forEach { prop ->
            when (prop.simpleName.asString()) {
                "name" -> {
                    // Try to extract from getter
                    prop.getter?.let { getter ->
                        // This is simplified - in practice you'd need to evaluate the expression
                    }
                }
                "lang" -> {
                    // Similar extraction
                }
            }
        }

        // Fallback: try to get from build options
        val buildDir = getBuildDir()
        val variant = getVariant(buildDir)

        options["${variant}_name"]?.let { name = it }
        options["${variant}_lang"]?.let { lang = it }

        return name to lang
    }

    private fun generateSourceId(name: String, lang: String, version: Int = 1): Long {
        val key = "${name.lowercase()}/$lang/$version"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    private fun generateIdProvider(classDecl: KSClassDeclaration, sourceId: Long) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()

        val fileSpec = FileSpec.builder(packageName, "${className}Id")
            .addProperty(
                PropertySpec.builder("${className}_GENERATED_ID", Long::class)
                    .initializer("%L", sourceId)
                    .addKdoc("Auto-generated source ID for $className")
                    .build()
            )
            .build()

        try {
            fileSpec.writeTo(codeGenerator, Dependencies(false, classDecl.containingFile!!))
        } catch (e: Exception) {
            // File might already exist
        }
    }

    private fun generateSourceConfigImpl(
        classDecl: KSClassDeclaration,
        name: String,
        baseUrl: String,
        lang: String,
        sourceId: Long
    ) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()

        val fileSpec = FileSpec.builder(packageName, "${className}Generated")
            .addType(
                TypeSpec.objectBuilder("${className}Config")
                    .addKdoc("Auto-generated configuration for $className")
                    .addProperty(
                        PropertySpec.builder("NAME", String::class)
                            .initializer("%S", name)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("BASE_URL", String::class)
                            .initializer("%S", baseUrl)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("LANG", String::class)
                            .initializer("%S", lang)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("ID", Long::class)
                            .initializer("%L", sourceId)
                            .build()
                    )
                    .build()
            )
            .build()

        try {
            fileSpec.writeTo(codeGenerator, Dependencies(false, classDecl.containingFile!!))
        } catch (e: Exception) {
            // File might already exist
        }
    }

    private fun generateFiltersImpl(
        classDecl: KSClassDeclaration,
        hasTitle: Boolean,
        hasAuthor: Boolean,
        hasSort: Boolean,
        sortOptions: List<String>,
        hasGenre: Boolean,
        genreOptions: List<String>,
        hasStatus: Boolean
    ) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()

        val filterListType = ClassName("ireader.core.source.model", "FilterList")

        val filters = mutableListOf<String>()
        if (hasTitle) filters.add("Filter.Title()")
        if (hasAuthor) filters.add("Filter.Author()")
        if (hasSort && sortOptions.isNotEmpty()) {
            val options = sortOptions.joinToString(", ") { "\"$it\"" }
            filters.add("Filter.Sort(\"Sort By:\", arrayOf($options))")
        }
        if (hasGenre && genreOptions.isNotEmpty()) {
            val options = genreOptions.joinToString(", ") { "\"$it\"" }
            filters.add("Filter.Genre(\"Genre:\", arrayOf($options))")
        }
        if (hasStatus) {
            filters.add("Filter.Status()")
        }

        val filtersCode = filters.joinToString(",\n        ")

        val fileSpec = FileSpec.builder(packageName, "${className}Filters")
            .addFunction(
                FunSpec.builder("${className.lowercase()}Filters")
                    .returns(filterListType)
                    .addStatement("return listOf(\n        $filtersCode\n    )")
                    .addKdoc("Auto-generated filters for $className")
                    .build()
            )
            .addImport("ireader.core.source.model", "Filter")
            .build()

        try {
            fileSpec.writeTo(codeGenerator, Dependencies(false, classDecl.containingFile!!))
        } catch (e: Exception) {
            // File might already exist
        }
    }

    private fun generateCommandsImpl(
        classDecl: KSClassDeclaration,
        detailFetch: Boolean,
        contentFetch: Boolean,
        chapterFetch: Boolean,
        webView: Boolean
    ) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()

        val commandListType = ClassName("ireader.core.source.model", "CommandList")

        val commands = mutableListOf<String>()
        if (detailFetch) commands.add("Command.Detail.Fetch()")
        if (contentFetch) commands.add("Command.Content.Fetch()")
        if (chapterFetch) commands.add("Command.Chapter.Fetch()")
        if (webView) commands.add("Command.WebView()")

        val commandsCode = commands.joinToString(",\n        ")

        val fileSpec = FileSpec.builder(packageName, "${className}Commands")
            .addFunction(
                FunSpec.builder("${className.lowercase()}Commands")
                    .returns(commandListType)
                    .addStatement("return listOf(\n        $commandsCode\n    )")
                    .addKdoc("Auto-generated commands for $className")
                    .build()
            )
            .addImport("ireader.core.source.model", "Command")
            .build()

        try {
            fileSpec.writeTo(codeGenerator, Dependencies(false, classDecl.containingFile!!))
        } catch (e: Exception) {
            // File might already exist
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getAnnotationArgument(annotation: KSAnnotation, name: String, default: T): T {
        return annotation.arguments.find { it.name?.asString() == name }?.value as? T ?: default
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAnnotationArrayArgument(annotation: KSAnnotation, name: String): List<String> {
        val value = annotation.arguments.find { it.name?.asString() == name }?.value
        return (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    }

    private fun String.covertToOsPath(): String {
        return if (System.getProperty("os.name").contains("win", true)) {
            this.replace("\\", "/")
        } else {
            this
        }
    }

    private fun getBuildDir(): String {
        return try {
            codeGenerator.generatedFile.firstOrNull()?.path?.substringBefore("/resources/")
                ?.substringBefore("\\resources\\")?.covertToOsPath() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getVariant(buildDir: String): String {
        val build = buildDir.substringAfterLast("/ksp/").substringBefore('/')
        return build.removeSuffix("Debug").removeSuffix("Release")
    }

    companion object {
        const val EXTENSION_ANNOTATION = "tachiyomix.annotations.Extension"
        const val AUTO_SOURCE_ID_ANNOTATION = "tachiyomix.annotations.AutoSourceId"
        const val SOURCE_CONFIG_ANNOTATION = "tachiyomix.annotations.SourceConfig"
        const val VALIDATE_PACKAGE_ANNOTATION = "tachiyomix.annotations.ValidatePackage"
        const val GENERATE_FILTERS_ANNOTATION = "tachiyomix.annotations.GenerateFilters"
        const val GENERATE_COMMANDS_ANNOTATION = "tachiyomix.annotations.GenerateCommands"
    }
}

class SourceBoilerplateProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SourceBoilerplateProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
