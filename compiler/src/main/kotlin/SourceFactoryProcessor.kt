package tachiyomix.compiler

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
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * 🏭 SourceFactoryProcessor - Generates SourceFactory implementations from annotations
 * 
 * This processor handles:
 * - @ExploreFetcher → generates exploreFetchers list
 * - @DetailSelectors → generates detailFetcher
 * - @ChapterSelectors → generates chapterFetcher  
 * - @ContentSelectors → generates contentFetcher
 * - @GenerateFilters → generates getFilters() function
 * - @GenerateCommands → generates getCommands() function
 * 
 * The generated code creates helper objects that can be used in the source class.
 * 
 * NOTE: This processor does NOT use validate() because the source class may reference
 * the generated code (e.g., SunovelsGenerated), creating a circular dependency.
 * The annotations themselves are always valid.
 */
class SourceFactoryProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val processedClasses = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = mutableSetOf<KSClassDeclaration>()
        
        SUPPORTED_ANNOTATIONS.forEach { annotation ->
            resolver.getSymbolsWithAnnotation(annotation)
                .filterIsInstance<KSClassDeclaration>()
                .forEach { annotatedClasses.add(it) }
        }

        annotatedClasses.forEach { classDecl ->
            val key = classDecl.qualifiedName?.asString() ?: return@forEach
            if (key !in processedClasses) {
                processedClasses.add(key)
                generateSourceHelpers(classDecl)
            }
        }

        return emptyList()
    }

    private fun generateSourceHelpers(source: KSClassDeclaration) {
        val packageName = source.packageName.asString()
        val className = source.simpleName.asString()
        val generatedClassName = "${className}Generated"

        val objectBuilder = TypeSpec.objectBuilder(generatedClassName)
            .addKdoc("Generated helpers for $className\n\nUsage:\n")

        var hasContent = false

        val exploreFetchers = processExploreFetchers(source)
        if (exploreFetchers != null) {
            objectBuilder.addProperty(exploreFetchers)
            objectBuilder.addKdoc("- override val exploreFetchers = $generatedClassName.exploreFetchers\n")
            hasContent = true
        }

        val detailFetcher = processDetailSelectors(source)
        if (detailFetcher != null) {
            objectBuilder.addProperty(detailFetcher)
            objectBuilder.addKdoc("- override val detailFetcher = $generatedClassName.detailFetcher\n")
            hasContent = true
        }

        val chapterFetcher = processChapterSelectors(source)
        if (chapterFetcher != null) {
            objectBuilder.addProperty(chapterFetcher)
            objectBuilder.addKdoc("- override val chapterFetcher = $generatedClassName.chapterFetcher\n")
            hasContent = true
        }

        val contentFetcher = processContentSelectors(source)
        if (contentFetcher != null) {
            objectBuilder.addProperty(contentFetcher)
            objectBuilder.addKdoc("- override val contentFetcher = $generatedClassName.contentFetcher\n")
            hasContent = true
        }

        val filtersFunc = processGenerateFilters(source)
        if (filtersFunc != null) {
            objectBuilder.addFunction(filtersFunc)
            objectBuilder.addKdoc("- override fun getFilters() = $generatedClassName.getFilters()\n")
            hasContent = true
        }

        val commandsFunc = processGenerateCommands(source)
        if (commandsFunc != null) {
            objectBuilder.addFunction(commandsFunc)
            objectBuilder.addKdoc("- override fun getCommands() = $generatedClassName.getCommands()\n")
            hasContent = true
        }

        if (hasContent) {
            try {
                FileSpec.builder(packageName, generatedClassName)
                    .addType(objectBuilder.build())
                    .addImport("ireader.core.source", "SourceFactory")
                    .addImport("ireader.core.source.model", "Filter")
                    .addImport("ireader.core.source.model", "Command")
                    .addFileComment("Generated by SourceFactoryProcessor - DO NOT EDIT\n\nThis file contains generated helpers for $className")
                    .build()
                    .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))
                
                logger.info("SourceFactoryProcessor: Generated $generatedClassName for $className")
            } catch (e: Exception) {
                logger.warn("SourceFactoryProcessor: Could not generate helpers for $className: ${e.message}")
            }
        }
    }

    private fun processExploreFetchers(source: KSClassDeclaration): PropertySpec? {
        val annotations = source.annotations.filter { 
            it.shortName.asString() == "ExploreFetcher" 
        }.toList()

        if (annotations.isEmpty()) return null

        val fetchers = annotations.map { ann ->
            ExploreFetcherConfig(
                name = getArg(ann, "name", ""),
                endpoint = getArg(ann, "endpoint", ""),
                selector = getArg(ann, "selector", ""),
                nameSelector = getArg(ann, "nameSelector", ""),
                linkSelector = getArg(ann, "linkSelector", ""),
                coverSelector = getArg(ann, "coverSelector", ""),
                isSearch = getArg(ann, "isSearch", false)
            )
        }

        val baseExploreFetcherClass = ClassName("ireader.core.source", "SourceFactory", "BaseExploreFetcher")
        val listType = LIST.parameterizedBy(baseExploreFetcherClass)

        val codeBlock = CodeBlock.builder()
            .add("listOf(\n")
        
        fetchers.forEachIndexed { index, fetcher ->
            val typeValue = if (fetcher.isSearch) "SourceFactory.Type.Search" else "SourceFactory.Type.Others"
            codeBlock.add("    %T(\n", baseExploreFetcherClass)
            codeBlock.add("        %S,\n", fetcher.name)
            codeBlock.add("        endpoint = %S,\n", fetcher.endpoint)
            codeBlock.add("        selector = %S,\n", fetcher.selector)
            codeBlock.add("        nameSelector = %S,\n", fetcher.nameSelector)
            codeBlock.add("        linkSelector = %S,\n", fetcher.linkSelector)
            codeBlock.add("        linkAtt = %S,\n", "href")
            codeBlock.add("        coverSelector = %S,\n", fetcher.coverSelector)
            codeBlock.add("        coverAtt = %S,\n", "src")
            codeBlock.add("        type = %L\n", typeValue)
            codeBlock.add("    )")
            if (index < fetchers.size - 1) codeBlock.add(",")
            codeBlock.add("\n")
        }
        codeBlock.add(")")

        return PropertySpec.builder("exploreFetchers", listType)
            .initializer(codeBlock.build())
            .build()
    }

    private fun processDetailSelectors(source: KSClassDeclaration): PropertySpec? {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "DetailSelectors" 
        } ?: return null

        val detailClass = ClassName("ireader.core.source", "SourceFactory", "Detail")

        val codeBlock = CodeBlock.builder()
            .add("%T(\n", detailClass)
            .add("    nameSelector = %S,\n", getArg(annotation, "title", ""))
            .add("    coverSelector = %S,\n", getArg(annotation, "cover", ""))
            .add("    coverAtt = %S,\n", "src")
            .add("    authorBookSelector = %S,\n", getArg(annotation, "author", ""))
            .add("    descriptionSelector = %S,\n", getArg(annotation, "description", ""))
            .add("    categorySelector = %S,\n", getArg(annotation, "genres", ""))
            .add("    statusSelector = %S\n", getArg(annotation, "status", ""))
            .add(")")
            .build()

        return PropertySpec.builder("detailFetcher", detailClass)
            .initializer(codeBlock)
            .build()
    }

    private fun processChapterSelectors(source: KSClassDeclaration): PropertySpec? {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "ChapterSelectors" 
        } ?: return null

        val chaptersClass = ClassName("ireader.core.source", "SourceFactory", "Chapters")
        val reversed = getArg(annotation, "reversed", false)
        val date = getArg(annotation, "date", "")

        val codeBlock = CodeBlock.builder()
            .add("%T(\n", chaptersClass)
            .add("    selector = %S,\n", getArg(annotation, "list", ""))
            .add("    nameSelector = %S,\n", getArg(annotation, "name", ""))
            .add("    linkSelector = %S,\n", getArg(annotation, "link", ""))
            .add("    linkAtt = %S,\n", "href")
        
        if (date.isNotEmpty()) {
            codeBlock.add("    uploadDateSelector = %S,\n", date)
        }
        
        codeBlock.add("    reverseChapterList = %L\n", reversed)
            .add(")")

        return PropertySpec.builder("chapterFetcher", chaptersClass)
            .initializer(codeBlock.build())
            .build()
    }

    private fun processContentSelectors(source: KSClassDeclaration): PropertySpec? {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "ContentSelectors" 
        } ?: return null

        val contentClass = ClassName("ireader.core.source", "SourceFactory", "Content")
        val title = getArg(annotation, "title", "")

        val codeBlock = CodeBlock.builder()
            .add("%T(\n", contentClass)
        
        if (title.isNotEmpty()) {
            codeBlock.add("    pageTitleSelector = %S,\n", title)
        }
        
        codeBlock.add("    pageContentSelector = %S\n", getArg(annotation, "content", ""))
            .add(")")

        return PropertySpec.builder("contentFetcher", contentClass)
            .initializer(codeBlock.build())
            .build()
    }

    private fun processGenerateFilters(source: KSClassDeclaration): FunSpec? {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "GenerateFilters" 
        } ?: return null

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
            filters.add("Filter.Genre(\"Genre:\", arrayOf($options))")
        }
        if (hasStatus) filters.add("Filter.Status()")

        val filtersCode = filters.joinToString(",\n        ")

        return FunSpec.builder("getFilters")
            .returns(filterListType)
            .addStatement("return listOf(\n        $filtersCode\n    )")
            .build()
    }

    private fun processGenerateCommands(source: KSClassDeclaration): FunSpec? {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "GenerateCommands" 
        } ?: return null

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

        val commandsCode = commands.joinToString(",\n        ")

        return FunSpec.builder("getCommands")
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

    private data class ExploreFetcherConfig(
        val name: String,
        val endpoint: String,
        val selector: String,
        val nameSelector: String,
        val linkSelector: String,
        val coverSelector: String,
        val isSearch: Boolean
    )

    companion object {
        private val LIST = List::class.asClassName()
        
        private val SUPPORTED_ANNOTATIONS = listOf(
            "tachiyomix.annotations.ExploreFetcher",
            "tachiyomix.annotations.DetailSelectors",
            "tachiyomix.annotations.ChapterSelectors",
            "tachiyomix.annotations.ContentSelectors",
            "tachiyomix.annotations.GenerateFilters",
            "tachiyomix.annotations.GenerateCommands"
        )
    }
}

class SourceFactoryProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SourceFactoryProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
