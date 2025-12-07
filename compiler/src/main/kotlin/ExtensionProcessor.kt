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
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/*
    Copyright (C) 2018 The IReader Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * KSP Processor that generates the final Extension class from @Extension annotated sources.
 *
 * This processor:
 * 1. Finds classes annotated with @Extension
 * 2. Validates they are open/abstract and implement Source
 * 3. Generates a concrete Extension class with name, lang, id properties
 *
 * Supports multi-round processing for sources that reference generated code
 * (e.g., SunovelsGenerated from SourceFactoryProcessor).
 */
class ExtensionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        val allExtensions = resolver.getSymbolsWithAnnotation(EXTENSION_FQ_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        if (allExtensions.isEmpty()) {
            val extensionGenerated = resolver.getClassDeclarationByName(EXTENSION_FQ_CLASS) != null
            if (!extensionGenerated) {
                return emptyList()
            }
            processed = true
            return emptyList()
        }

        val validExtensions = allExtensions.filter { it.validate() }
        val deferredExtensions = allExtensions.filterNot { it.validate() }

        if (validExtensions.isEmpty() && deferredExtensions.isNotEmpty()) {
            return deferredExtensions
        }

        val extension = getClassToGenerate(validExtensions)

        if (extension == null) {
            if (deferredExtensions.isNotEmpty()) {
                return deferredExtensions
            }
            val extensionGenerated = resolver.getClassDeclarationByName(EXTENSION_FQ_CLASS) != null
            check(extensionGenerated) {
                "No extension found. Please ensure at least one Source is annotated with @Extension"
            }
            processed = true
            return emptyList()
        }

        val extensionType = extension.asStarProjectedType()

        val buildDir = getBuildDir()
        val variant = getVariant(buildDir)
        val arguments = parseArguments(variant)

        check(extension.isOpen()) {
            "[$extension] must be open or abstract"
        }

        val sourceClass = resolver.getClassDeclarationByName(SOURCE_FQ_CLASS)
            ?: throw Exception("This class is not implementing the Source interface")

        check(sourceClass.asStarProjectedType().isAssignableFrom(extensionType)) {
            "$extension doesn't implement $sourceClass"
        }

        if (arguments.hasDeeplinks) {
            val deepLinkClass = resolver.getClassDeclarationByName(DEEPLINKSOURCE_FQ_CLASS)!!
            check(deepLinkClass.asStarProjectedType().isAssignableFrom(extensionType)) {
                "Deep links of $extension were defined but the extension doesn't implement $deepLinkClass"
            }
        }

        checkMatchesPkgName(extension, buildDir)

        val dependencies = resolver.getClassDeclarationByName(DEPENDENCIES_FQ_CLASS)!!
        extension.accept(SourceVisitor(arguments, dependencies), Unit)

        processed = true
        return deferredExtensions
    }

    private fun getClassToGenerate(extensions: List<KSClassDeclaration>): KSClassDeclaration? {
        return when (extensions.size) {
            0 -> null
            1 -> extensions.first()
            else -> {
                val candidate = extensions.find { candidate ->
                    val type = candidate.asStarProjectedType()
                    extensions.all { it === candidate || it.asStarProjectedType().isAssignableFrom(type) }
                }
                checkNotNull(candidate) {
                    "Found [${extensions.joinToString()}] annotated with @Extension but they don't" +
                        " inherit each other. Only one class can be generated"
                }
                candidate
            }
        }
    }

    private fun checkMatchesPkgName(source: KSClassDeclaration, buildDir: String) {
        if ("/sources/multisrc" in buildDir || "\\sources\\multisrc" in buildDir) return

        val pkgName = source.packageName.asString()
        val normalizedBuildDir = buildDir.replace("\\", "/")
        val sourceDir = normalizedBuildDir.substringBeforeLast("/build/").substringAfterLast("/")
        val expectedPkgName = "ireader.$sourceDir"

        val isValidPackage = Regex("^$expectedPkgName\\.?.*?").matches(pkgName)
        check(isValidPackage) {
            "The package name of the extension $source must start with \"$expectedPkgName\" which right now is \"$pkgName\""
        }
    }

    private inner class SourceVisitor(
        val arguments: Arguments,
        val dependencies: KSClassDeclaration,
    ) : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val packageName = classDeclaration.packageName.asString()
            val className = classDeclaration.simpleName.asString()
            val generatedHelperClass = ClassName(packageName, "${className}Generated")

            // Check for @GenerateFilters and @GenerateCommands annotations
            val hasGenerateFilters = classDeclaration.annotations.any {
                it.shortName.asString() == "GenerateFilters"
            }
            val hasGenerateCommands = classDeclaration.annotations.any {
                it.shortName.asString() == "GenerateCommands"
            }

            // Parse annotation parameters for inline generation
            val filtersAnnotation = classDeclaration.annotations.find {
                it.shortName.asString() == "GenerateFilters"
            }
            val commandsAnnotation = classDeclaration.annotations.find {
                it.shortName.asString() == "GenerateCommands"
            }

            val classSpecBuilder = TypeSpec.classBuilder(EXTENSION_CLASS)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("deps", dependencies.toClassName())
                        .build()
                )
                .superclass(classDeclaration.toClassName())
                .addSuperclassConstructorParameter("%L", "deps")
                .addProperty(
                    PropertySpec.builder("name", String::class, KModifier.OVERRIDE)
                        .initializer("%S", arguments.name)
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("lang", String::class, KModifier.OVERRIDE)
                        .initializer("%S", arguments.lang)
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("id", Long::class, KModifier.OVERRIDE)
                        .initializer("%L", arguments.id)
                        .build()
                )

            // Add getFilters() override if @GenerateFilters is present
            if (hasGenerateFilters && filtersAnnotation != null) {
                val filtersFunc = generateFiltersFunction(filtersAnnotation)
                classSpecBuilder.addFunction(filtersFunc)
            }

            // Add getCommands() override if @GenerateCommands is present
            if (hasGenerateCommands && commandsAnnotation != null) {
                val commandsFunc = generateCommandsFunction(commandsAnnotation)
                classSpecBuilder.addFunction(commandsFunc)
            }

            // Check for declarative selector annotations
            val hasExploreFetchers = classDeclaration.annotations.any {
                it.shortName.asString() == "ExploreFetcher"
            }
            val hasDetailSelectors = classDeclaration.annotations.any {
                it.shortName.asString() == "DetailSelectors"
            }
            val hasChapterSelectors = classDeclaration.annotations.any {
                it.shortName.asString() == "ChapterSelectors"
            }
            val hasContentSelectors = classDeclaration.annotations.any {
                it.shortName.asString() == "ContentSelectors"
            }

            // Add exploreFetchers override if @ExploreFetcher is present
            if (hasExploreFetchers) {
                val exploreFetchersProp = generateExploreFetchersProperty(classDeclaration)
                if (exploreFetchersProp != null) {
                    classSpecBuilder.addProperty(exploreFetchersProp)
                }
            }

            // Add detailFetcher override if @DetailSelectors is present
            if (hasDetailSelectors) {
                val detailAnnotation = classDeclaration.annotations.find {
                    it.shortName.asString() == "DetailSelectors"
                }
                if (detailAnnotation != null) {
                    val detailProp = generateDetailFetcherProperty(detailAnnotation)
                    classSpecBuilder.addProperty(detailProp)
                }
            }

            // Add chapterFetcher override if @ChapterSelectors is present
            if (hasChapterSelectors) {
                val chapterAnnotation = classDeclaration.annotations.find {
                    it.shortName.asString() == "ChapterSelectors"
                }
                if (chapterAnnotation != null) {
                    val chapterProp = generateChapterFetcherProperty(chapterAnnotation)
                    classSpecBuilder.addProperty(chapterProp)
                }
            }

            // Add contentFetcher override if @ContentSelectors is present
            if (hasContentSelectors) {
                val contentAnnotation = classDeclaration.annotations.find {
                    it.shortName.asString() == "ContentSelectors"
                }
                if (contentAnnotation != null) {
                    val contentProp = generateContentFetcherProperty(contentAnnotation)
                    classSpecBuilder.addProperty(contentProp)
                }
            }

            val fileSpecBuilder = FileSpec.builder(EXTENSION_PACKAGE, EXTENSION_CLASS)
                .addType(classSpecBuilder.build())

            // Add necessary imports for filters and commands
            if (hasGenerateFilters || hasGenerateCommands) {
                fileSpecBuilder.addImport("ireader.core.source.model", "Filter")
                fileSpecBuilder.addImport("ireader.core.source.model", "Command")
            }

            // Add imports for SourceFactory types
            if (hasExploreFetchers || hasDetailSelectors || hasChapterSelectors || hasContentSelectors) {
                fileSpecBuilder.addImport("ireader.core.source", "SourceFactory")
            }

            fileSpecBuilder.build()
                .writeTo(codeGenerator, Dependencies(false, classDeclaration.containingFile!!))
        }

        /**
         * Generate getFilters() function based on @GenerateFilters annotation parameters
         */
        private fun generateFiltersFunction(annotation: KSAnnotation): FunSpec {
            val hasTitle = getArg(annotation, "title", true)
            val hasAuthor = getArg(annotation, "author", false)
            val hasSort = getArg(annotation, "sort", false)
            val sortOptions = getArrayArg(annotation, "sortOptions")
            val hasGenre = getArg(annotation, "genre", false)
            val genreOptions = getArrayArg(annotation, "genreOptions")
            val hasStatus = getArg(annotation, "status", false)

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
                filters.add("Filter.Select(\"Genre:\", arrayOf($options))")
            }
            if (hasStatus) {
                filters.add("Filter.Select(\"Status:\", arrayOf(\"All\", \"Ongoing\", \"Completed\"))")
            }

            val filtersCode = if (filters.isEmpty()) {
                "Filter.Title()"
            } else {
                filters.joinToString(",\n        ")
            }

            return FunSpec.builder("getFilters")
                .addModifiers(KModifier.OVERRIDE)
                .returns(filterListType)
                .addStatement("return listOf(\n        $filtersCode\n    )")
                .build()
        }

        /**
         * Generate getCommands() function based on @GenerateCommands annotation parameters
         */
        private fun generateCommandsFunction(annotation: KSAnnotation): FunSpec {
            val detailFetch = getArg(annotation, "detailFetch", true)
            val contentFetch = getArg(annotation, "contentFetch", true)
            val chapterFetch = getArg(annotation, "chapterFetch", true)
            val webView = getArg(annotation, "webView", false)

            val commandListType = ClassName("ireader.core.source.model", "CommandList")

            val commands = mutableListOf<String>()
            if (detailFetch) commands.add("Command.Detail.Fetch()")
            if (chapterFetch) commands.add("Command.Chapter.Fetch()")
            if (contentFetch) commands.add("Command.Content.Fetch()")
            if (webView) commands.add("Command.WebView()")

            val commandsCode = if (commands.isEmpty()) {
                "Command.Detail.Fetch(),\n        Command.Chapter.Fetch(),\n        Command.Content.Fetch()"
            } else {
                commands.joinToString(",\n        ")
            }

            return FunSpec.builder("getCommands")
                .addModifiers(KModifier.OVERRIDE)
                .returns(commandListType)
                .addStatement("return listOf(\n        $commandsCode\n    )")
                .build()
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> getArg(annotation: KSAnnotation, name: String, default: T): T {
            return annotation.arguments.find { it.name?.asString() == name }?.value as? T ?: default
        }

        @Suppress("UNCHECKED_CAST")
        private fun getArrayArg(annotation: KSAnnotation, name: String): List<String> {
            val value = annotation.arguments.find { it.name?.asString() == name }?.value
            return (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        }

        /**
         * Generate exploreFetchers property from @ExploreFetcher annotations
         */
        private fun generateExploreFetchersProperty(classDeclaration: KSClassDeclaration): PropertySpec? {
            val annotations = classDeclaration.annotations.filter {
                it.shortName.asString() == "ExploreFetcher"
            }.toList()

            if (annotations.isEmpty()) return null

            val baseExploreFetcherClass = ClassName("ireader.core.source", "SourceFactory", "BaseExploreFetcher")
            val listType = ClassName("kotlin.collections", "List").parameterizedBy(baseExploreFetcherClass)

            val fetchersCode = annotations.mapIndexed { index, ann ->
                val name = getArg(ann, "name", "")
                val endpoint = getArg(ann, "endpoint", "")
                val selector = getArg(ann, "selector", "")
                val nameSelector = getArg(ann, "nameSelector", "")
                val linkSelector = getArg(ann, "linkSelector", "")
                val coverSelector = getArg(ann, "coverSelector", "")
                val isSearch = getArg(ann, "isSearch", false)
                val typeValue = if (isSearch) "SourceFactory.Type.Search" else "SourceFactory.Type.Others"

                """SourceFactory.BaseExploreFetcher(
        "$name",
        endpoint = "$endpoint",
        selector = "$selector",
        nameSelector = "$nameSelector",
        linkSelector = "$linkSelector",
        linkAtt = "href",
        coverSelector = "$coverSelector",
        coverAtt = "src",
        addBaseUrlToLink = true,
        addBaseurlToCoverLink = true,
        type = $typeValue
    )"""
            }.joinToString(",\n    ")

            return PropertySpec.builder("exploreFetchers", listType)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder()
                    .addStatement("return listOf(\n    $fetchersCode\n)")
                    .build())
                .build()
        }

        /**
         * Generate detailFetcher property from @DetailSelectors annotation
         */
        private fun generateDetailFetcherProperty(annotation: KSAnnotation): PropertySpec {
            val detailClass = ClassName("ireader.core.source", "SourceFactory", "Detail")

            val title = getArg(annotation, "title", "")
            val cover = getArg(annotation, "cover", "")
            val author = getArg(annotation, "author", "")
            val description = getArg(annotation, "description", "")
            val genres = getArg(annotation, "genres", "")
            val status = getArg(annotation, "status", "")

            return PropertySpec.builder("detailFetcher", detailClass)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder()
                    .addStatement("""return SourceFactory.Detail(
        nameSelector = "$title",
        coverSelector = "$cover",
        coverAtt = "src",
        authorBookSelector = "$author",
        descriptionSelector = "$description",
        categorySelector = "$genres",
        statusSelector = "$status",
        addBaseurlToCoverLink = true
    )""")
                    .build())
                .build()
        }

        /**
         * Generate chapterFetcher property from @ChapterSelectors annotation
         */
        private fun generateChapterFetcherProperty(annotation: KSAnnotation): PropertySpec {
            val chaptersClass = ClassName("ireader.core.source", "SourceFactory", "Chapters")

            val list = getArg(annotation, "list", "")
            val name = getArg(annotation, "name", "")
            val link = getArg(annotation, "link", "")
            val date = getArg(annotation, "date", "")
            val reversed = getArg(annotation, "reversed", false)

            val dateSelector = if (date.isNotEmpty()) """uploadDateSelector = "$date",""" else ""

            return PropertySpec.builder("chapterFetcher", chaptersClass)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder()
                    .addStatement("""return SourceFactory.Chapters(
        selector = "$list",
        nameSelector = "$name",
        linkSelector = "$link",
        linkAtt = "href",
        $dateSelector
        addBaseUrlToLink = true,
        reverseChapterList = $reversed
    )""")
                    .build())
                .build()
        }

        /**
         * Generate contentFetcher property from @ContentSelectors annotation
         */
        private fun generateContentFetcherProperty(annotation: KSAnnotation): PropertySpec {
            val contentClass = ClassName("ireader.core.source", "SourceFactory", "Content")

            val content = getArg(annotation, "content", "")
            val title = getArg(annotation, "title", "")

            val titleSelector = if (title.isNotEmpty()) """pageTitleSelector = "$title",""" else ""

            return PropertySpec.builder("contentFetcher", contentClass)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder()
                    .addStatement("""return SourceFactory.Content(
        $titleSelector
        pageContentSelector = "$content"
    )""")
                    .build())
                .build()
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
        return codeGenerator.collectVariantName("").convertToOsPath()
    }

    private fun getVariant(buildDir: String): String {
        val build = buildDir.substringAfterLast("/ksp/").substringBefore('/')
        return build.removeSuffix("Debug").removeSuffix("Release")
    }

    private fun parseArguments(variant: String): Arguments {
        return Arguments(
            name = options["${variant}_name"]!!,
            lang = options["${variant}_lang"]!!,
            id = options["${variant}_id"]!!.toLong(),
            hasDeeplinks = options["${variant}_has_deeplinks"].toBoolean()
        )
    }

    private data class Arguments(
        val name: String,
        val lang: String,
        val id: Long,
        val hasDeeplinks: Boolean
    )

    private companion object {
        const val SOURCE_FQ_CLASS = "ireader.core.source.Source"
        const val DEEPLINKSOURCE_FQ_CLASS = "ireader.core.source.DeepLinkSource"
        const val DEPENDENCIES_FQ_CLASS = "ireader.core.source.Dependencies"
        const val EXTENSION_FQ_ANNOTATION = "tachiyomix.annotations.Extension"
        const val EXTENSION_PACKAGE = "tachiyomix.extension"
        const val EXTENSION_CLASS = "Extension"
        const val EXTENSION_FQ_CLASS = "$EXTENSION_PACKAGE.$EXTENSION_CLASS"
    }
}

class ExtensionProcessorFactory : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ExtensionProcessor(environment.codeGenerator, environment.logger, environment.options)
    }
}
