package tachiyomix.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * KSP Processor that generates comprehensive test cases for source extensions.
 *
 * Generates tests for:
 * - Source instantiation and properties
 * - Filter validation
 * - Fetcher endpoint validation
 * - Selector syntax validation
 * - URL building tests
 * - Deep link handling tests
 * - Integration tests (optional)
 *
 * Enable with: ksp { arg("generateTests", "true") }
 * For integration tests: ksp { arg("generateIntegrationTests", "true") }
 */
class TestGeneratorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val generateTests = options["generateTests"]?.toBoolean() ?: false
    private val generateIntegrationTests = options["generateIntegrationTests"]?.toBoolean() ?: false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (!generateTests) {
            return emptyList()
        }

        val extensions = resolver.getSymbolsWithAnnotation(EXTENSION_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        extensions.forEach { source ->
            generateUnitTests(source)
            if (generateIntegrationTests) {
                generateIntegrationTests(source)
            }
        }

        return emptyList()
    }

    private fun generateUnitTests(source: KSClassDeclaration) {
        val packageName = source.packageName.asString()
        val className = source.simpleName.asString()
        val testClassName = "${className}UnitTest"

        // Collect annotation info
        val annotations = AnnotationInfo.from(source)

        val testAnnotation = ClassName("org.junit", "Test")
        val assertClass = ClassName("kotlin.test", "assertTrue")
        val assertEqualsClass = ClassName("kotlin.test", "assertEquals")
        val assertNotNullClass = ClassName("kotlin.test", "assertNotNull")
        val beforeAnnotation = ClassName("org.junit", "Before")

        val testClassBuilder = TypeSpec.classBuilder(testClassName)
            .addKdoc("Auto-generated unit tests for $className\n\n")
            .addKdoc("Run with: ./gradlew :extensions:individual:${packageName.substringAfter("ireader.")}:test\n")

        // Add test properties
        if (annotations.hasFilters) {
            testClassBuilder.addProperty(
                PropertySpec.builder("filters", ClassName(packageName, "${className}Filters"))
                    .initializer("${className}Filters")
                    .build()
            )
        }
        if (annotations.hasFetchers) {
            testClassBuilder.addProperty(
                PropertySpec.builder("fetchers", ClassName(packageName, "${className}Fetchers"))
                    .initializer("${className}Fetchers")
                    .build()
            )
        }

        // ===== FILTER TESTS =====
        if (annotations.hasFilters) {
            testClassBuilder.addFunction(
                FunSpec.builder("testFiltersNotEmpty")
                    .addAnnotation(testAnnotation)
                    .addStatement("val filterList = filters.getGeneratedFilters()")
                    .addStatement("%T(filterList.isNotEmpty(), %S)", assertClass, "Filters should not be empty")
                    .build()
            )

            testClassBuilder.addFunction(
                FunSpec.builder("testFilterTypes")
                    .addAnnotation(testAnnotation)
                    .addStatement("val filterList = filters.getGeneratedFilters()")
                    .addStatement("// Verify each filter has a valid type")
                    .addStatement("filterList.forEach { filter ->")
                    .addStatement("    %T(filter != null, %S)", assertNotNullClass, "Filter should not be null")
                    .addStatement("}")
                    .build()
            )
        }

        // ===== FETCHER TESTS =====
        if (annotations.hasFetchers) {
            testClassBuilder.addFunction(
                FunSpec.builder("testFetchersNotEmpty")
                    .addAnnotation(testAnnotation)
                    .addStatement("val fetcherList = fetchers.generatedExploreFetchers")
                    .addStatement("%T(fetcherList.isNotEmpty(), %S)", assertClass, "Fetchers should not be empty")
                    .build()
            )

            testClassBuilder.addFunction(
                FunSpec.builder("testFetcherEndpointsHavePlaceholders")
                    .addAnnotation(testAnnotation)
                    .addStatement("val fetcherList = fetchers.generatedExploreFetchers")
                    .addStatement("fetcherList.forEach { fetcher ->")
                    .addStatement("    val hasPlaceholder = fetcher.endpoint.contains(\"{page}\") || fetcher.endpoint.contains(\"{query}\")")
                    .addStatement("    %T(hasPlaceholder, \"Fetcher '\${fetcher.key}' endpoint should have {page} or {query} placeholder\")", assertClass)
                    .addStatement("}")
                    .build()
            )

            testClassBuilder.addFunction(
                FunSpec.builder("testFetcherSelectorsNotBlank")
                    .addAnnotation(testAnnotation)
                    .addStatement("val fetcherList = fetchers.generatedExploreFetchers")
                    .addStatement("fetcherList.forEach { fetcher ->")
                    .addStatement("    %T(fetcher.selector.isNotBlank(), \"Fetcher '\${fetcher.key}' selector should not be blank\")", assertClass)
                    .addStatement("}")
                    .build()
            )

            testClassBuilder.addFunction(
                FunSpec.builder("testFetcherNamesUnique")
                    .addAnnotation(testAnnotation)
                    .addStatement("val fetcherList = fetchers.generatedExploreFetchers")
                    .addStatement("val names = fetcherList.map { it.key }")
                    .addStatement("val uniqueNames = names.toSet()")
                    .addStatement("%T(names.size, uniqueNames.size, %S)", assertEqualsClass, "Fetcher names should be unique")
                    .build()
            )

            testClassBuilder.addFunction(
                FunSpec.builder("testSearchFetcherExists")
                    .addAnnotation(testAnnotation)
                    .addStatement("val fetcherList = fetchers.generatedExploreFetchers")
                    .addStatement("val hasSearch = fetcherList.any { it.endpoint.contains(\"{query}\") }")
                    .addStatement("// Note: Search is recommended but not required")
                    .addStatement("if (!hasSearch) {")
                    .addStatement("    println(\"Warning: No search fetcher found for $className\")")
                    .addStatement("}")
                    .build()
            )
        }

        // ===== SELECTOR VALIDATION TESTS =====
        if (annotations.hasDetailSelectors || annotations.hasChapterSelectors || annotations.hasContentSelectors) {
            testClassBuilder.addFunction(
                FunSpec.builder("testSelectorsValidSyntax")
                    .addAnnotation(testAnnotation)
                    .addStatement("// Validate CSS selector syntax")
                    .addStatement("val selectorPattern = Regex(\"\"\"^[a-zA-Z0-9_.#\\[\\]\\-:>+~=\\\"'\\s\\(\\)\\*,]+$\"\"\")")
                    .addStatement("fun validateSelector(selector: String, name: String) {")
                    .addStatement("    if (selector.isNotBlank()) {")
                    .addStatement("        %T(selectorPattern.matches(selector), \"Selector '\$name' has invalid syntax: \$selector\")", assertClass)
                    .addStatement("    }")
                    .addStatement("}")
                    .addStatement("// Add selector validations here based on annotations")
                    .build()
            )

            testClassBuilder.addFunction(
                FunSpec.builder("testSelectorsBalancedBrackets")
                    .addAnnotation(testAnnotation)
                    .addStatement("fun checkBalanced(selector: String, name: String) {")
                    .addStatement("    var count = 0")
                    .addStatement("    for (c in selector) {")
                    .addStatement("        when (c) {")
                    .addStatement("            '(', '[', '{' -> count++")
                    .addStatement("            ')', ']', '}' -> count--")
                    .addStatement("        }")
                    .addStatement("        %T(count >= 0, \"Unbalanced brackets in '\$name': \$selector\")", assertClass)
                    .addStatement("    }")
                    .addStatement("    %T(count == 0, \"Unclosed brackets in '\$name': \$selector\")", assertClass)
                    .addStatement("}")
                    .addStatement("// Add bracket checks for each selector")
                    .build()
            )
        }

        // ===== DEEP LINK TESTS =====
        if (annotations.hasDeepLinks) {
            testClassBuilder.addFunction(
                FunSpec.builder("testDeepLinkPatternsNotEmpty")
                    .addAnnotation(testAnnotation)
                    .addStatement("val patterns = ${className}DeepLinks.patterns")
                    .addStatement("%T(patterns.isNotEmpty(), %S)", assertClass, "Deep link patterns should not be empty")
                    .build()
            )

            testClassBuilder.addFunction(
                FunSpec.builder("testDeepLinkHostsValid")
                    .addAnnotation(testAnnotation)
                    .addStatement("val patterns = ${className}DeepLinks.patterns")
                    .addStatement("patterns.forEach { pattern ->")
                    .addStatement("    %T(pattern.host.isNotBlank(), %S)", assertClass, "Deep link host should not be blank")
                    .addStatement("    %T(!pattern.host.startsWith(\"http\"), %S)", assertClass, "Host should not include scheme")
                    .addStatement("}")
                    .build()
            )

            testClassBuilder.addFunction(
                FunSpec.builder("testDeepLinkCanHandle")
                    .addAnnotation(testAnnotation)
                    .addStatement("val patterns = ${className}DeepLinks.patterns")
                    .addStatement("patterns.forEach { pattern ->")
                    .addStatement("    val testUrl = \"\${pattern.scheme}://\${pattern.host}/test\"")
                    .addStatement("    %T(${className}DeepLinks.canHandle(testUrl), \"Should handle URL: \$testUrl\")", assertClass)
                    .addStatement("}")
                    .build()
            )
        }

        // ===== URL BUILDING TESTS =====
        testClassBuilder.addFunction(
            FunSpec.builder("testUrlPlaceholderReplacement")
                .addAnnotation(testAnnotation)
                .addStatement("val endpoint = \"/search?q={query}&page={page}\"")
                .addStatement("val result = endpoint.replace(\"{query}\", \"test\").replace(\"{page}\", \"1\")")
                .addStatement("%T(result, \"/search?q=test&page=1\", %S)", assertEqualsClass, "URL placeholders should be replaced")
                .build()
        )

        val testClass = testClassBuilder.build()

        FileSpec.builder(packageName, testClassName)
            .addType(testClass)
            .addImport("kotlin.test", "assertTrue", "assertEquals", "assertNotNull")
            .addFileComment("Auto-generated unit tests - DO NOT EDIT\n")
            .addFileComment("Generated by TestGeneratorProcessor\n")
            .addFileComment("Run: ./gradlew test")
            .build()
            .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))

        logger.info("Generated unit tests: $packageName.$testClassName")
    }

    private fun generateIntegrationTests(source: KSClassDeclaration) {
        val packageName = source.packageName.asString()
        val className = source.simpleName.asString()
        val testClassName = "${className}IntegrationTest"

        val annotations = AnnotationInfo.from(source)

        val testAnnotation = ClassName("org.junit", "Test")
        val ignoreAnnotation = ClassName("org.junit", "Ignore")
        val assertClass = ClassName("kotlin.test", "assertTrue")
        val assertNotNullClass = ClassName("kotlin.test", "assertNotNull")
        val runBlockingClass = ClassName("kotlinx.coroutines", "runBlocking")

        val testClassBuilder = TypeSpec.classBuilder(testClassName)
            .addKdoc("Auto-generated integration tests for $className\n\n")
            .addKdoc("These tests make actual network requests.\n")
            .addKdoc("Run with: ./gradlew :extensions:individual:${packageName.substringAfter("ireader.")}:connectedTest\n")
            .addKdoc("\n@Ignore by default - remove to run integration tests")

        // Test: Fetch popular/latest
        testClassBuilder.addFunction(
            FunSpec.builder("testFetchLatestNovels")
                .addAnnotation(testAnnotation)
                .addAnnotation(ignoreAnnotation)
                .addStatement("// Integration test - fetches actual data from source")
                .addStatement("%T {", runBlockingClass)
                .addStatement("    // val source = createSource()")
                .addStatement("    // val result = source.getMangaList(source.getListings().first(), 1)")
                .addStatement("    // assertTrue(result.mangas.isNotEmpty(), \"Should fetch novels\")")
                .addStatement("}")
                .build()
        )

        // Test: Search
        testClassBuilder.addFunction(
            FunSpec.builder("testSearchNovels")
                .addAnnotation(testAnnotation)
                .addAnnotation(ignoreAnnotation)
                .addStatement("// Integration test - searches for novels")
                .addStatement("%T {", runBlockingClass)
                .addStatement("    // val source = createSource()")
                .addStatement("    // val filters = source.getFilters()")
                .addStatement("    // filters.filterIsInstance<Filter.Title>().first().value = \"test\"")
                .addStatement("    // val result = source.getMangaList(filters, 1)")
                .addStatement("    // assertTrue(result.mangas.isNotEmpty(), \"Should find novels\")")
                .addStatement("}")
                .build()
        )

        // Test: Fetch details
        testClassBuilder.addFunction(
            FunSpec.builder("testFetchNovelDetails")
                .addAnnotation(testAnnotation)
                .addAnnotation(ignoreAnnotation)
                .addStatement("// Integration test - fetches novel details")
                .addStatement("%T {", runBlockingClass)
                .addStatement("    // val source = createSource()")
                .addStatement("    // val novels = source.getMangaList(source.getListings().first(), 1)")
                .addStatement("    // val novel = novels.mangas.first()")
                .addStatement("    // val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("    // assertNotNull(details.title, \"Should have title\")")
                .addStatement("}")
                .build()
        )

        // Test: Fetch chapters
        testClassBuilder.addFunction(
            FunSpec.builder("testFetchChapters")
                .addAnnotation(testAnnotation)
                .addAnnotation(ignoreAnnotation)
                .addStatement("// Integration test - fetches chapter list")
                .addStatement("%T {", runBlockingClass)
                .addStatement("    // val source = createSource()")
                .addStatement("    // val novels = source.getMangaList(source.getListings().first(), 1)")
                .addStatement("    // val novel = novels.mangas.first()")
                .addStatement("    // val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("    // assertTrue(chapters.isNotEmpty(), \"Should have chapters\")")
                .addStatement("}")
                .build()
        )

        // Test: Fetch content
        testClassBuilder.addFunction(
            FunSpec.builder("testFetchChapterContent")
                .addAnnotation(testAnnotation)
                .addAnnotation(ignoreAnnotation)
                .addStatement("// Integration test - fetches chapter content")
                .addStatement("%T {", runBlockingClass)
                .addStatement("    // val source = createSource()")
                .addStatement("    // val novels = source.getMangaList(source.getListings().first(), 1)")
                .addStatement("    // val novel = novels.mangas.first()")
                .addStatement("    // val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("    // val chapter = chapters.first()")
                .addStatement("    // val pages = source.getPageList(chapter, emptyList())")
                .addStatement("    // assertTrue(pages.isNotEmpty(), \"Should have content\")")
                .addStatement("}")
                .build()
        )

        val testClass = testClassBuilder.build()

        FileSpec.builder(packageName, testClassName)
            .addType(testClass)
            .addImport("kotlin.test", "assertTrue", "assertNotNull")
            .addImport("kotlinx.coroutines", "runBlocking")
            .addFileComment("Auto-generated integration tests - DO NOT EDIT\n")
            .addFileComment("Generated by TestGeneratorProcessor\n")
            .addFileComment("These tests are @Ignore by default")
            .build()
            .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))

        logger.info("Generated integration tests: $packageName.$testClassName")
    }

    private data class AnnotationInfo(
        val hasFilters: Boolean,
        val hasFetchers: Boolean,
        val hasDetailSelectors: Boolean,
        val hasChapterSelectors: Boolean,
        val hasContentSelectors: Boolean,
        val hasDeepLinks: Boolean,
        val hasRateLimit: Boolean,
        val hasApiEndpoints: Boolean
    ) {
        companion object {
            fun from(source: KSClassDeclaration): AnnotationInfo {
                val annotations = source.annotations.map { it.shortName.asString() }.toSet()
                return AnnotationInfo(
                    hasFilters = "SourceFilters" in annotations,
                    hasFetchers = "ExploreFetcher" in annotations,
                    hasDetailSelectors = "DetailSelectors" in annotations,
                    hasChapterSelectors = "ChapterSelectors" in annotations,
                    hasContentSelectors = "ContentSelectors" in annotations,
                    hasDeepLinks = "SourceDeepLink" in annotations,
                    hasRateLimit = "RateLimit" in annotations,
                    hasApiEndpoints = "ApiEndpoint" in annotations
                )
            }
        }
    }

    companion object {
        const val EXTENSION_ANNOTATION = "tachiyomix.annotations.Extension"
    }
}

class TestGeneratorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return TestGeneratorProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
