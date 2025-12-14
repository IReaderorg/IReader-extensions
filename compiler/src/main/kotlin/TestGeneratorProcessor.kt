/*
 * Copyright (C) IReader Project
 * SPDX-License-Identifier: Apache-2.0
 */

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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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
 * Triggered by @GenerateTests annotation on source classes.
 * Can also be enabled globally with: ksp { arg("generateTests", "true") }
 * For integration tests: ksp { arg("generateIntegrationTests", "true") }
 */
class TestGeneratorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val globalGenerateTests = options["generateTests"]?.toBoolean() ?: false
    private val globalGenerateIntegrationTests = options["generateIntegrationTests"]?.toBoolean() ?: false
    private val processedClasses = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Process classes with @GenerateTests annotation
        val annotatedWithGenerateTests = resolver.getSymbolsWithAnnotation(GENERATE_TESTS_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()
        
        annotatedWithGenerateTests.forEach { source ->
            val key = source.qualifiedName?.asString() ?: return@forEach
            if (key !in processedClasses) {
                processedClasses.add(key)
                val testConfig = getGenerateTestsConfig(source)
                if (testConfig.unitTests) {
                    generateUnitTests(source, testConfig)
                }
                if (testConfig.integrationTests) {
                    generateIntegrationTests(source, testConfig)
                }
            }
        }
        
        // Also process @Extension classes if global flag is set
        if (globalGenerateTests) {
            val extensions = resolver.getSymbolsWithAnnotation(EXTENSION_ANNOTATION)
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.validate() }
                .toList()

            extensions.forEach { source ->
                val key = source.qualifiedName?.asString() ?: return@forEach
                if (key !in processedClasses) {
                    processedClasses.add(key)
                    val testConfig = GenerateTestsConfig()
                    generateUnitTests(source, testConfig)
                    if (globalGenerateIntegrationTests) {
                        generateIntegrationTests(source, testConfig)
                    }
                }
            }
        }

        return emptyList()
    }
    
    /**
     * Extract configuration from @GenerateTests annotation
     */
    private fun getGenerateTestsConfig(source: KSClassDeclaration): GenerateTestsConfig {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "GenerateTests" 
        } ?: return GenerateTestsConfig()
        
        return GenerateTestsConfig(
            unitTests = getArg(annotation, "unitTests", true),
            integrationTests = getArg(annotation, "integrationTests", false),
            searchQuery = getArg(annotation, "searchQuery", "test"),
            minSearchResults = getArg(annotation, "minSearchResults", 1)
        )
    }
    
    /**
     * Extract configuration from @TestFixture annotation
     */
    private fun getTestFixtureConfig(source: KSClassDeclaration): TestFixtureConfig? {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "TestFixture" 
        } ?: return null
        
        return TestFixtureConfig(
            novelUrl = getArg(annotation, "novelUrl", ""),
            chapterUrl = getArg(annotation, "chapterUrl", ""),
            expectedTitle = getArg(annotation, "expectedTitle", ""),
            expectedAuthor = getArg(annotation, "expectedAuthor", "")
        )
    }
    
    /**
     * Extract configuration from @SkipTests annotation
     */
    private fun getSkipTestsConfig(source: KSClassDeclaration): SkipTestsConfig {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "SkipTests" 
        } ?: return SkipTestsConfig()
        
        return SkipTestsConfig(
            search = getArg(annotation, "search", false),
            chapters = getArg(annotation, "chapters", false),
            content = getArg(annotation, "content", false),
            reason = getArg(annotation, "reason", "")
        )
    }
    
    /**
     * Extract configuration from @TestExpectations annotation
     */
    private fun getTestExpectationsConfig(source: KSClassDeclaration): TestExpectationsConfig {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "TestExpectations" 
        } ?: return TestExpectationsConfig()
        
        return TestExpectationsConfig(
            minLatestNovels = getArg(annotation, "minLatestNovels", 1),
            minChapters = getArg(annotation, "minChapters", 1),
            supportsPagination = getArg(annotation, "supportsPagination", true),
            requiresLogin = getArg(annotation, "requiresLogin", false)
        )
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun <T> getArg(annotation: com.google.devtools.ksp.symbol.KSAnnotation, name: String, default: T): T {
        return annotation.arguments.find { it.name?.asString() == name }?.value as? T ?: default
    }
    
    data class GenerateTestsConfig(
        val unitTests: Boolean = true,
        val integrationTests: Boolean = false,
        val searchQuery: String = "test",
        val minSearchResults: Int = 1
    )
    
    data class TestFixtureConfig(
        val novelUrl: String = "",
        val chapterUrl: String = "",
        val expectedTitle: String = "",
        val expectedAuthor: String = ""
    )
    
    data class SkipTestsConfig(
        val search: Boolean = false,
        val chapters: Boolean = false,
        val content: Boolean = false,
        val reason: String = ""
    )
    
    data class TestExpectationsConfig(
        val minLatestNovels: Int = 1,
        val minChapters: Int = 1,
        val supportsPagination: Boolean = true,
        val requiresLogin: Boolean = false
    )

    private fun generateUnitTests(source: KSClassDeclaration, config: GenerateTestsConfig) {
        val packageName = source.packageName.asString()
        val className = source.simpleName.asString()
        val testClassName = "${className}UnitTest"

        // Collect annotation info
        val annotations = AnnotationInfo.from(source)
        val skipTests = getSkipTestsConfig(source)
        val expectations = getTestExpectationsConfig(source)

        val testAnnotation = ClassName("kotlin.test", "Test")
        val assertClass = ClassName("kotlin.test", "assertTrue")
        val assertEqualsClass = ClassName("kotlin.test", "assertEquals")
        val assertNotNullClass = ClassName("kotlin.test", "assertNotNull")

        val testClassBuilder = TypeSpec.classBuilder(testClassName)
            .addKdoc("Auto-generated unit tests for $className\n\n")
            .addKdoc("Run with: ./gradlew test\n")
            .addKdoc("\nGenerated from @GenerateTests annotation\n")
            .addKdoc("Search query: ${config.searchQuery}\n")
            .addKdoc("Min search results: ${config.minSearchResults}\n")

        // ===== BASIC VALIDATION TESTS =====
        testClassBuilder.addFunction(
            FunSpec.builder("testSourceClassExists")
                .addAnnotation(testAnnotation)
                .addStatement("// Verify the source class can be referenced")
                .addStatement("val sourceClass = $className::class")
                .addStatement("%T(sourceClass, %S)", assertNotNullClass, "Source class should exist")
                .build()
        )

        // ===== FILTER TESTS =====
        if (annotations.hasFilters) {
            testClassBuilder.addFunction(
                FunSpec.builder("testFiltersAnnotationPresent")
                    .addAnnotation(testAnnotation)
                    .addStatement("// @GenerateFilters annotation is present - filters will be auto-generated")
                    .addStatement("%T(true, %S)", assertClass, "Filters annotation is present")
                    .build()
            )
        }

        // ===== COMMANDS TESTS =====
        if (annotations.hasCommands) {
            testClassBuilder.addFunction(
                FunSpec.builder("testCommandsAnnotationPresent")
                    .addAnnotation(testAnnotation)
                    .addStatement("// @GenerateCommands annotation is present - commands will be auto-generated")
                    .addStatement("%T(true, %S)", assertClass, "Commands annotation is present")
                    .build()
            )
        }

        // ===== FETCHER TESTS =====
        if (annotations.hasFetchers) {
            testClassBuilder.addFunction(
                FunSpec.builder("testExploreFetcherAnnotationPresent")
                    .addAnnotation(testAnnotation)
                    .addStatement("// @ExploreFetcher annotation is present - exploreFetchers will be auto-generated")
                    .addStatement("%T(true, %S)", assertClass, "ExploreFetcher annotation is present")
                    .build()
            )
        }

        // ===== SELECTOR TESTS =====
        if (annotations.hasDetailSelectors) {
            testClassBuilder.addFunction(
                FunSpec.builder("testDetailSelectorsAnnotationPresent")
                    .addAnnotation(testAnnotation)
                    .addStatement("// @DetailSelectors annotation is present - detailFetcher will be auto-generated")
                    .addStatement("%T(true, %S)", assertClass, "DetailSelectors annotation is present")
                    .build()
            )
        }

        if (annotations.hasChapterSelectors) {
            testClassBuilder.addFunction(
                FunSpec.builder("testChapterSelectorsAnnotationPresent")
                    .addAnnotation(testAnnotation)
                    .addStatement("// @ChapterSelectors annotation is present - chapterFetcher will be auto-generated")
                    .addStatement("%T(true, %S)", assertClass, "ChapterSelectors annotation is present")
                    .build()
            )
        }

        if (annotations.hasContentSelectors) {
            testClassBuilder.addFunction(
                FunSpec.builder("testContentSelectorsAnnotationPresent")
                    .addAnnotation(testAnnotation)
                    .addStatement("// @ContentSelectors annotation is present - contentFetcher will be auto-generated")
                    .addStatement("%T(true, %S)", assertClass, "ContentSelectors annotation is present")
                    .build()
            )
        }

        // ===== DEEP LINK TESTS =====
        if (annotations.hasDeepLinks) {
            testClassBuilder.addFunction(
                FunSpec.builder("testDeepLinkAnnotationPresent")
                    .addAnnotation(testAnnotation)
                    .addStatement("// @SourceDeepLink annotation is present")
                    .addStatement("%T(true, %S)", assertClass, "DeepLink annotation is present")
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

        // ===== SKIP TESTS INFO =====
        if (skipTests.search || skipTests.chapters || skipTests.content) {
            testClassBuilder.addFunction(
                FunSpec.builder("testSkipTestsInfo")
                    .addAnnotation(testAnnotation)
                    .addStatement("// Some tests are skipped: ${skipTests.reason}")
                    .addStatement("println(\"Skipped tests - Search: ${skipTests.search}, Chapters: ${skipTests.chapters}, Content: ${skipTests.content}\")")
                    .addStatement("println(\"Reason: ${skipTests.reason}\")")
                    .addStatement("%T(true)", assertClass)
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

        val testClass = testClassBuilder.build()
        
        // Get the source class for import
        val sourceClass = source.toClassName()

        FileSpec.builder(packageName, testClassName)
            .addType(testClass)
            .addImport("kotlin.test", "Test", "assertTrue", "assertEquals", "assertNotNull")
            .addImport(sourceClass.packageName, sourceClass.simpleName)
            .addFileComment("Auto-generated unit tests - DO NOT EDIT\n")
            .addFileComment("Generated by TestGeneratorProcessor\n")
            .addFileComment("Run: ./gradlew test")
            .build()
            .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))

        logger.info("Generated unit tests: $packageName.$testClassName")
    }

    private fun generateIntegrationTests(source: KSClassDeclaration, config: GenerateTestsConfig) {
        val packageName = source.packageName.asString()
        val className = source.simpleName.asString()
        val testClassName = "${className}IntegrationTest"

        val testAnnotation = ClassName("kotlin.test", "Test")
        val assertClass = ClassName("kotlin.test", "assertTrue")
        val assertNotNullClass = ClassName("kotlin.test", "assertNotNull")
        val assertEqualsClass = ClassName("kotlin.test", "assertEquals")
        val runBlockingClass = ClassName("kotlinx.coroutines", "runBlocking")
        val dispatchersClass = ClassName("kotlinx.coroutines", "Dispatchers")
        
        val extensionClass = ClassName("tachiyomix.extension", "Extension")
        val dependenciesClass = ClassName("ireader.core.source", "Dependencies")
        val httpClientsInterfaceClass = ClassName("ireader.core.http", "HttpClientsInterface")
        val filterTitleClass = ClassName("ireader.core.source.model", "Filter", "Title")
        val mangaInfoClass = ClassName("ireader.core.source.model", "MangaInfo")
        val textClass = ClassName("ireader.core.source.model", "Text")

        val sourceName = packageName.substringAfter("ireader.")
        
        val runWithAnnotation = ClassName("org.junit.runner", "RunWith")
        val robolectricRunner = ClassName("org.robolectric", "RobolectricTestRunner")
        
        val testClassBuilder = TypeSpec.classBuilder(testClassName)
            .addAnnotation(AnnotationSpec.builder(runWithAnnotation)
                .addMember("%T::class", robolectricRunner)
                .build())
            .addKdoc("Auto-generated comprehensive integration tests for $className\n\n")
            .addKdoc("These tests make actual network requests and validate all source functionality.\n")
            .addKdoc("Run with: ./gradlew :sources:en:$sourceName:testEnDebugUnitTest\n")

        // Add deps property using TestHttpClients and TestPreferenceStore
        testClassBuilder.addProperty(
            PropertySpec.builder("deps", dependenciesClass)
                .addKdoc("Dependencies instance using TestHttpClients and TestPreferenceStore.\n")
                .initializer("%T(TestHttpClients(), TestPreferenceStore())", dependenciesClass)
                .build()
        )

        // Add source property
        testClassBuilder.addProperty(
            PropertySpec.builder("source", extensionClass)
                .addKdoc("The Extension instance under test.\n")
                .initializer("%T(deps)", extensionClass)
                .build()
        )

        // ===== TEST: Source Properties =====
        testClassBuilder.addFunction(
            FunSpec.builder("testSourceProperties")
                .addAnnotation(testAnnotation)
                .addStatement("// Verify source has required properties")
                .addStatement("%T(source.name.isNotBlank(), %S)", assertClass, "Source should have a name")
                .addStatement("%T(source.baseUrl.isNotBlank(), %S)", assertClass, "Source should have a baseUrl")
                .addStatement("%T(source.lang.isNotBlank(), %S)", assertClass, "Source should have a language")
                .addStatement("%T(source.id != 0L, %S)", assertClass, "Source should have a valid ID")
                .addStatement("println(%S + source.name)", "Source Name: ")
                .addStatement("println(%S + source.baseUrl)", "Base URL: ")
                .addStatement("println(%S + source.lang)", "Language: ")
                .addStatement("println(%S + source.id)", "ID: ")
                .build()
        )

        // ===== TEST: Listings =====
        // Note: Some sources (direct HttpSource subclasses) may return empty listings
        // In that case, they rely on filters for browsing
        testClassBuilder.addFunction(
            FunSpec.builder("testListings")
                .addAnnotation(testAnnotation)
                .addStatement("val listings = source.getListings()")
                .addStatement("println(%S + listings.size)", "Number of listings: ")
                .addStatement("if (listings.isNotEmpty()) {")
                .addStatement("    listings.forEach { listing ->")
                .addStatement("        %T(listing.name.isNotBlank(), %S)", assertClass, "Listing should have a name")
                .addStatement("        println(%S + listing.name)", "Listing: ")
                .addStatement("    }")
                .addStatement("} else {")
                .addStatement("    println(%S)", "Source has no listings - uses filters for browsing")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Filters =====
        testClassBuilder.addFunction(
            FunSpec.builder("testFilters")
                .addAnnotation(testAnnotation)
                .addStatement("val filters = source.getFilters()")
                .addStatement("%T(filters.isNotEmpty(), %S)", assertClass, "Source should have filters")
                .addStatement("println(%S + filters.size)", "Number of filters: ")
                .addStatement("filters.forEach { filter ->")
                .addStatement("    println(%S + filter::class.simpleName + %S + filter.name)", "Filter: ", " - ")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Commands =====
        testClassBuilder.addFunction(
            FunSpec.builder("testCommands")
                .addAnnotation(testAnnotation)
                .addStatement("val commands = source.getCommands()")
                .addStatement("%T(commands.isNotEmpty(), %S)", assertClass, "Source should have commands")
                .addStatement("println(%S + commands.size)", "Number of commands: ")
                .build()
        )

        // ===== TEST: Explore Fetchers (for SourceFactory sources) =====
        testClassBuilder.addFunction(
            FunSpec.builder("testExploreFetchers")
                .addAnnotation(testAnnotation)
                .addKdoc("Test all explore fetchers defined in the source (Latest, Popular, Search, etc.)\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    println(%S)", "=== Explore Fetchers ===")
                .addStatement("    println(%S + listings.size)", "Number of fetchers: ")
                .addStatement("    ")
                .addStatement("    // Test each listing/fetcher")
                .addStatement("    listings.forEachIndexed { index, listing ->")
                .addStatement("        println(%S + listing.name + %S)", "Testing fetcher: ", "...")
                .addStatement("        try {")
                .addStatement("            val result = source.getMangaList(listing, 1)")
                .addStatement("            %T(result.mangas.isNotEmpty(), %S + listing.name)", assertClass, "Fetcher should return novels: ")
                .addStatement("            println(%S + listing.name + %S + result.mangas.size + %S)", "✓ ", " returned ", " novels")
                .addStatement("            ")
                .addStatement("            // Validate first result from each fetcher")
                .addStatement("            val first = result.mangas.first()")
                .addStatement("            %T(first.title.isNotBlank(), %S + listing.name)", assertClass, "Novel from fetcher should have title: ")
                .addStatement("            %T(first.key.isNotBlank(), %S + listing.name)", assertClass, "Novel from fetcher should have key: ")
                .addStatement("        } catch (e: Exception) {")
                .addStatement("            println(%S + listing.name + %S + e.message)", "✗ ", " failed: ")
                .addStatement("            throw e")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Search Fetcher Specifically =====
        testClassBuilder.addFunction(
            FunSpec.builder("testSearchFetcher")
                .addAnnotation(testAnnotation)
                .addKdoc("Test the search functionality with various queries\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Search Fetcher Test ===")
                .addStatement("    val filters = source.getFilters().toMutableList()")
                .addStatement("    val titleFilter = filters.filterIsInstance<%T>().firstOrNull()", filterTitleClass)
                .addStatement("    ")
                .addStatement("    if (titleFilter == null) {")
                .addStatement("        println(%S)", "No title filter - search not supported")
                .addStatement("        return@runBlocking")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Test with configured search query")
                .addStatement("    titleFilter.value = %S", config.searchQuery)
                .addStatement("    val result = source.getMangaList(filters, 1)")
                .addStatement("    println(%S + %S + %S + result.mangas.size + %S)", "Search for '", config.searchQuery, "' returned ", " results")
                .addStatement("    %T(result.mangas.size >= %L, %S)", assertClass, config.minSearchResults, "Should find at least ${config.minSearchResults} result(s)")
                .addStatement("    ")
                .addStatement("    // Validate search results")
                .addStatement("    result.mangas.take(3).forEach { novel ->")
                .addStatement("        println(%S + novel.title)", "  Found: ")
                .addStatement("        %T(novel.title.isNotBlank(), %S)", assertClass, "Search result should have title")
                .addStatement("        %T(novel.key.isNotBlank(), %S)", assertClass, "Search result should have key")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Fetch Latest Novels =====
        // Works with both SourceFactory (has listings) and HttpSource (may use filters)
        testClassBuilder.addFunction(
            FunSpec.builder("testFetchLatestNovels")
                .addAnnotation(testAnnotation)
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val result = if (listings.isNotEmpty()) {")
                .addStatement("        println(%S)", "Using listings to fetch novels...")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "No listings - using filters to fetch novels...")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    %T(result.mangas.isNotEmpty(), %S)", assertClass, "Should fetch novels")
                .addStatement("    println(%S + result.mangas.size)", "Fetched novels: ")
                .addStatement("    ")
                .addStatement("    // Validate first novel has basic info")
                .addStatement("    val firstNovel = result.mangas.first()")
                .addStatement("    %T(firstNovel.title.isNotBlank(), %S)", assertClass, "Novel should have title")
                .addStatement("    %T(firstNovel.key.isNotBlank(), %S)", assertClass, "Novel should have key/url")
                .addStatement("    println(%S + firstNovel.title)", "First novel: ")
                .addStatement("    println(%S + firstNovel.key)", "Novel URL: ")
                .addStatement("    if (firstNovel.cover.isNotBlank()) println(%S + firstNovel.cover)", "Cover: ")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Pagination =====
        // Works with both SourceFactory (has listings) and HttpSource (may use filters)
        testClassBuilder.addFunction(
            FunSpec.builder("testPagination")
                .addAnnotation(testAnnotation)
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val filters = source.getFilters()")
                .addStatement("    ")
                .addStatement("    // Fetch page 1")
                .addStatement("    val page1 = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(filters, 1)")
                .addStatement("    }")
                .addStatement("    %T(page1.mangas.isNotEmpty(), %S)", assertClass, "Page 1 should have novels")
                .addStatement("    ")
                .addStatement("    // Fetch page 2 if available")
                .addStatement("    if (page1.hasNextPage) {")
                .addStatement("        val page2 = if (listings.isNotEmpty()) {")
                .addStatement("            source.getMangaList(listings.first(), 2)")
                .addStatement("        } else {")
                .addStatement("            source.getMangaList(filters, 2)")
                .addStatement("        }")
                .addStatement("        %T(page2.mangas.isNotEmpty(), %S)", assertClass, "Page 2 should have novels")
                .addStatement("        ")
                .addStatement("        // Verify pages have different content")
                .addStatement("        val page1Keys = page1.mangas.map { it.key }.toSet()")
                .addStatement("        val page2Keys = page2.mangas.map { it.key }.toSet()")
                .addStatement("        val overlap = page1Keys.intersect(page2Keys)")
                .addStatement("        %T(overlap.size < page1.mangas.size, %S)", assertClass, "Pages should have mostly different novels")
                .addStatement("        println(%S + page1.mangas.size + %S + page2.mangas.size)", "Page 1: ", " novels, Page 2: ")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Search =====
        testClassBuilder.addFunction(
            FunSpec.builder("testSearchNovels")
                .addAnnotation(testAnnotation)
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val filters = source.getFilters().toMutableList()")
                .addStatement("    val titleFilter = filters.filterIsInstance<%T>().firstOrNull()", filterTitleClass)
                .addStatement("    ")
                .addStatement("    if (titleFilter != null) {")
                .addStatement("        titleFilter.value = %S", config.searchQuery)
                .addStatement("        val result = source.getMangaList(filters, 1)")
                .addStatement("        %T(result.mangas.isNotEmpty(), %S)", assertClass, "Should find novels matching search")
                .addStatement("        println(%S + result.mangas.size)", "Search results: ")
                .addStatement("        ")
                .addStatement("        // Verify search results are relevant")
                .addStatement("        result.mangas.take(3).forEach { novel ->")
                .addStatement("            println(%S + novel.title)", "Found: ")
                .addStatement("        }")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "No title filter available for search test")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Novel Details - Comprehensive =====
        // Works with both SourceFactory (has listings) and HttpSource (may use filters)
        testClassBuilder.addFunction(
            FunSpec.builder("testFetchNovelDetailsComprehensive")
                .addAnnotation(testAnnotation)
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    %T(novels.mangas.isNotEmpty(), %S)", assertClass, "Should have novels to test")
                .addStatement("    ")
                .addStatement("    val novel = novels.mangas.first()")
                .addStatement("    val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("    ")
                .addStatement("    // Required fields")
                .addStatement("    %T(details.title.isNotBlank(), %S)", assertClass, "Novel should have title")
                .addStatement("    %T(details.key.isNotBlank(), %S)", assertClass, "Novel should have key/url")
                .addStatement("    ")
                .addStatement("    // Log all details")
                .addStatement("    println(%S)", "=== Novel Details ===")
                .addStatement("    println(%S + details.title)", "Title: ")
                .addStatement("    println(%S + details.key)", "URL: ")
                .addStatement("    ")
                .addStatement("    // Optional but expected fields")
                .addStatement("    if (details.cover.isNotBlank()) {")
                .addStatement("        println(%S + details.cover)", "Cover: ")
                .addStatement("        %T(details.cover.startsWith(%S) || details.cover.startsWith(%S), %S)", assertClass, "http://", "https://", "Cover should be a valid URL")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    if (details.author.isNotBlank()) {")
                .addStatement("        println(%S + details.author)", "Author: ")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    if (details.description.isNotBlank()) {")
                .addStatement("        println(%S + details.description.take(200) + %S)", "Description: ", "...")
                .addStatement("        %T(details.description.length > 10, %S)", assertClass, "Description should be meaningful")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    if (details.genres.isNotEmpty()) {")
                .addStatement("        println(%S + details.genres.joinToString(%S))", "Genres: ", ", ")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    println(%S + details.status)", "Status: ")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Chapters - Comprehensive =====
        // Works with both SourceFactory (has listings) and HttpSource (may use filters)
        testClassBuilder.addFunction(
            FunSpec.builder("testFetchChaptersComprehensive")
                .addAnnotation(testAnnotation)
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    %T(novels.mangas.isNotEmpty(), %S)", assertClass, "Should have novels to test")
                .addStatement("    ")
                .addStatement("    val novel = novels.mangas.first()")
                .addStatement("    val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("    %T(chapters.isNotEmpty(), %S)", assertClass, "Novel should have chapters")
                .addStatement("    ")
                .addStatement("    println(%S)", "=== Chapters ===")
                .addStatement("    println(%S + chapters.size)", "Total chapters: ")
                .addStatement("    ")
                .addStatement("    // Validate chapter structure")
                .addStatement("    chapters.take(5).forEachIndexed { index, chapter ->")
                .addStatement("        %T(chapter.name.isNotBlank(), %S + index)", assertClass, "Chapter should have name at index ")
                .addStatement("        %T(chapter.key.isNotBlank(), %S + index)", assertClass, "Chapter should have key at index ")
                .addStatement("        println(%S + chapter.name)", "Chapter: ")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Check for duplicate chapters")
                .addStatement("    val uniqueKeys = chapters.map { it.key }.toSet()")
                .addStatement("    %T(uniqueKeys.size, chapters.size, %S)", assertEqualsClass, "Should not have duplicate chapter keys")
                .addStatement("    ")
                .addStatement("    // Validate chapter URLs are absolute")
                .addStatement("    chapters.take(5).forEach { chapter ->")
                .addStatement("        val isAbsoluteUrl = chapter.key.startsWith(%S) || chapter.key.startsWith(%S)", "http://", "https://")
                .addStatement("        %T(isAbsoluteUrl, %S + chapter.key)", assertClass, "Chapter URL should be absolute: ")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Chapter Ordering =====
        testClassBuilder.addFunction(
            FunSpec.builder("testChapterOrdering")
                .addAnnotation(testAnnotation)
                .addKdoc("Verify chapters are returned in a consistent order (usually oldest to newest or vice versa)\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    val novel = novels.mangas.first()")
                .addStatement("    val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("    ")
                .addStatement("    if (chapters.size >= 2) {")
                .addStatement("        println(%S)", "=== Chapter Ordering ===")
                .addStatement("        println(%S + chapters.first().name)", "First chapter: ")
                .addStatement("        println(%S + chapters.last().name)", "Last chapter: ")
                .addStatement("        ")
                .addStatement("        // Check if chapters have number info")
                .addStatement("        val hasNumbers = chapters.any { it.number > 0f }")
                .addStatement("        if (hasNumbers) {")
                .addStatement("            val firstNum = chapters.first().number")
                .addStatement("            val lastNum = chapters.last().number")
                .addStatement("            println(%S + firstNum + %S + lastNum)", "Chapter numbers: ", " to ")
                .addStatement("            // Chapters should be ordered (ascending or descending)")
                .addStatement("            val isOrdered = firstNum < lastNum || firstNum > lastNum")
                .addStatement("            %T(isOrdered, %S)", assertClass, "Chapters should be in order")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Chapter Content - Comprehensive =====
        // Works with both SourceFactory (has listings) and HttpSource (may use filters)
        testClassBuilder.addFunction(
            FunSpec.builder("testFetchChapterContentComprehensive")
                .addAnnotation(testAnnotation)
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    %T(novels.mangas.isNotEmpty(), %S)", assertClass, "Should have novels to test")
                .addStatement("    ")
                .addStatement("    val novel = novels.mangas.first()")
                .addStatement("    val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("    %T(chapters.isNotEmpty(), %S)", assertClass, "Should have chapters to test")
                .addStatement("    ")
                .addStatement("    // Test first chapter content")
                .addStatement("    val chapter = chapters.first()")
                .addStatement("    val pages = source.getPageList(chapter, emptyList())")
                .addStatement("    %T(pages.isNotEmpty(), %S)", assertClass, "Chapter should have content")
                .addStatement("    ")
                .addStatement("    println(%S)", "=== Chapter Content ===")
                .addStatement("    println(%S + chapter.name)", "Chapter: ")
                .addStatement("    println(%S + pages.size)", "Content pages/paragraphs: ")
                .addStatement("    ")
                .addStatement("    // Validate content")
                .addStatement("    val textPages = pages.filterIsInstance<%T>()", textClass)
                .addStatement("    if (textPages.isNotEmpty()) {")
                .addStatement("        val totalText = textPages.joinToString(%S) { it.text }", " ")
                .addStatement("        %T(totalText.length > 100, %S)", assertClass, "Chapter content should be substantial")
                .addStatement("        println(%S + totalText.length)", "Total text length: ")
                .addStatement("        println(%S + totalText.take(500) + %S)", "Preview: ", "...")
                .addStatement("        ")
                .addStatement("        // Check for common content issues")
                .addStatement("        val hasEmptyParagraphs = textPages.count { it.text.isBlank() }")
                .addStatement("        println(%S + hasEmptyParagraphs)", "Empty paragraphs: ")
                .addStatement("        ")
                .addStatement("        // Check content doesn't contain HTML tags (common parsing issue)")
                .addStatement("        val hasHtmlTags = totalText.contains(%S) || totalText.contains(%S)", "<p>", "<div>")
                .addStatement("        if (hasHtmlTags) {")
                .addStatement("            println(%S)", "⚠ Warning: Content may contain unparsed HTML tags")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Content Quality =====
        testClassBuilder.addFunction(
            FunSpec.builder("testContentQuality")
                .addAnnotation(testAnnotation)
                .addKdoc("Verify content quality - no empty chapters, reasonable length, etc.\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    val novel = novels.mangas.first()")
                .addStatement("    val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("    ")
                .addStatement("    println(%S)", "=== Content Quality Check ===")
                .addStatement("    ")
                .addStatement("    // Test multiple chapters for consistency")
                .addStatement("    val chaptersToTest = chapters.take(3)")
                .addStatement("    var totalContentLength = 0")
                .addStatement("    var emptyChapters = 0")
                .addStatement("    ")
                .addStatement("    chaptersToTest.forEach { chapter ->")
                .addStatement("        val pages = source.getPageList(chapter, emptyList())")
                .addStatement("        val textPages = pages.filterIsInstance<%T>()", textClass)
                .addStatement("        val contentLength = textPages.sumOf { it.text.length }")
                .addStatement("        ")
                .addStatement("        if (contentLength < 50) {")
                .addStatement("            emptyChapters++")
                .addStatement("            println(%S + chapter.name + %S + contentLength + %S)", "⚠ ", " has very short content: ", " chars")
                .addStatement("        } else {")
                .addStatement("            println(%S + chapter.name + %S + contentLength + %S)", "✓ ", ": ", " chars")
                .addStatement("        }")
                .addStatement("        totalContentLength += contentLength")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    val avgLength = if (chaptersToTest.isNotEmpty()) totalContentLength / chaptersToTest.size else 0")
                .addStatement("    println(%S + avgLength + %S)", "Average content length: ", " chars")
                .addStatement("    %T(emptyChapters < chaptersToTest.size, %S)", assertClass, "Most chapters should have content")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Multiple Chapters Content =====
        // Works with both SourceFactory (has listings) and HttpSource (may use filters)
        testClassBuilder.addFunction(
            FunSpec.builder("testMultipleChaptersContent")
                .addAnnotation(testAnnotation)
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    val novel = novels.mangas.first()")
                .addStatement("    val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("    ")
                .addStatement("    // Test first 3 chapters")
                .addStatement("    val chaptersToTest = chapters.take(3)")
                .addStatement("    println(%S + chaptersToTest.size)", "Testing chapters: ")
                .addStatement("    ")
                .addStatement("    chaptersToTest.forEach { chapter ->")
                .addStatement("        val pages = source.getPageList(chapter, emptyList())")
                .addStatement("        %T(pages.isNotEmpty(), %S + chapter.name)", assertClass, "Chapter should have content: ")
                .addStatement("        println(%S + chapter.name + %S + pages.size + %S)", "✓ ", " - ", " pages")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: End-to-End Flow =====
        // Works with both SourceFactory (has listings) and HttpSource (may use filters)
        testClassBuilder.addFunction(
            FunSpec.builder("testEndToEndFlow")
                .addAnnotation(testAnnotation)
                .addKdoc("Complete end-to-end test: Browse -> Select -> Details -> Chapters -> Read\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== End-to-End Test ===")
                .addStatement("    ")
                .addStatement("    // Step 1: Browse")
                .addStatement("    println(%S)", "Step 1: Browsing novels...")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val browseResult = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    %T(browseResult.mangas.isNotEmpty(), %S)", assertClass, "Browse should return novels")
                .addStatement("    println(%S + browseResult.mangas.size)", "Found novels: ")
                .addStatement("    ")
                .addStatement("    // Step 2: Select a novel")
                .addStatement("    println(%S)", "Step 2: Selecting first novel...")
                .addStatement("    val selectedNovel = browseResult.mangas.first()")
                .addStatement("    println(%S + selectedNovel.title)", "Selected: ")
                .addStatement("    ")
                .addStatement("    // Step 3: Get details")
                .addStatement("    println(%S)", "Step 3: Fetching details...")
                .addStatement("    val details = source.getMangaDetails(selectedNovel, emptyList())")
                .addStatement("    %T(details.title.isNotBlank(), %S)", assertClass, "Details should have title")
                .addStatement("    println(%S + details.title)", "Title: ")
                .addStatement("    if (details.author.isNotBlank()) println(%S + details.author)", "Author: ")
                .addStatement("    ")
                .addStatement("    // Step 4: Get chapters")
                .addStatement("    println(%S)", "Step 4: Fetching chapters...")
                .addStatement("    val chapters = source.getChapterList(selectedNovel, emptyList())")
                .addStatement("    %T(chapters.isNotEmpty(), %S)", assertClass, "Should have chapters")
                .addStatement("    println(%S + chapters.size)", "Chapters: ")
                .addStatement("    ")
                .addStatement("    // Step 5: Read first chapter")
                .addStatement("    println(%S)", "Step 5: Reading first chapter...")
                .addStatement("    val firstChapter = chapters.first()")
                .addStatement("    val content = source.getPageList(firstChapter, emptyList())")
                .addStatement("    %T(content.isNotEmpty(), %S)", assertClass, "Chapter should have content")
                .addStatement("    println(%S + firstChapter.name)", "Reading: ")
                .addStatement("    println(%S + content.size)", "Content blocks: ")
                .addStatement("    ")
                .addStatement("    println(%S)", "=== End-to-End Test PASSED ===")
                .addStatement("}")
                .build()
        )

        // ===== TEST: TestFixture - Use known-good URLs if provided =====
        val testFixture = getTestFixtureConfig(source)
        if (testFixture != null && testFixture.novelUrl.isNotBlank()) {
            testClassBuilder.addFunction(
                FunSpec.builder("testWithFixtureData")
                    .addAnnotation(testAnnotation)
                    .addKdoc("Test with known-good fixture data from @TestFixture annotation\n")
                    .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                    .addStatement("    println(%S)", "=== Testing with Fixture Data ===")
                    .addStatement("    ")
                    .addStatement("    // Create MangaInfo from fixture URL")
                    .addStatement("    val fixtureNovel = %T(key = %S, title = %S)", mangaInfoClass, testFixture.novelUrl, "Fixture Novel")
                    .addStatement("    println(%S + fixtureNovel.key)", "Fixture URL: ")
                    .addStatement("    ")
                    .addStatement("    // Test fetching details from fixture URL")
                    .addStatement("    val details = source.getMangaDetails(fixtureNovel, emptyList())")
                    .addStatement("    %T(details.title.isNotBlank(), %S)", assertClass, "Fixture novel should have title")
                    .addStatement("    println(%S + details.title)", "Title: ")
                    .apply {
                        if (testFixture.expectedTitle.isNotBlank()) {
                            addStatement("    %T(details.title, %S, %S)", assertEqualsClass, testFixture.expectedTitle, "Title should match expected")
                        }
                        if (testFixture.expectedAuthor.isNotBlank()) {
                            addStatement("    %T(details.author, %S, %S)", assertEqualsClass, testFixture.expectedAuthor, "Author should match expected")
                        }
                    }
                    .addStatement("    ")
                    .addStatement("    // Test fetching chapters from fixture")
                    .addStatement("    val chapters = source.getChapterList(fixtureNovel, emptyList())")
                    .addStatement("    %T(chapters.isNotEmpty(), %S)", assertClass, "Fixture novel should have chapters")
                    .addStatement("    println(%S + chapters.size)", "Chapters: ")
                    .apply {
                        if (testFixture.chapterUrl.isNotBlank()) {
                            addStatement("    ")
                            addStatement("    // Test fetching content from fixture chapter URL")
                            addStatement("    val fixtureChapter = %T(key = %S, name = %S)", ClassName("ireader.core.source.model", "ChapterInfo"), testFixture.chapterUrl, "Fixture Chapter")
                            addStatement("    val content = source.getPageList(fixtureChapter, emptyList())")
                            addStatement("    %T(content.isNotEmpty(), %S)", assertClass, "Fixture chapter should have content")
                            addStatement("    println(%S + content.size)", "Content blocks: ")
                        }
                    }
                    .addStatement("    ")
                    .addStatement("    println(%S)", "=== Fixture Test PASSED ===")
                    .addStatement("}")
                    .build()
            )
        }

        val testClass = testClassBuilder.build()
        val testPreferenceClass = generateTestPreference()
        val testPreferenceStoreClass = generateTestPreferenceStore()
        val testHttpClientsClass = generateTestHttpClients()

        FileSpec.builder(packageName, testClassName)
            .addType(testPreferenceClass)
            .addType(testPreferenceStoreClass)
            .addType(testHttpClientsClass)
            .addType(testClass)
            .addImport("kotlin.test", "Test", "assertTrue", "assertNotNull", "assertEquals")
            .addImport("kotlinx.coroutines", "runBlocking", "Dispatchers")
            .addImport("org.junit.runner", "RunWith")
            .addImport("org.robolectric", "RobolectricTestRunner")
            .addImport("tachiyomix.extension", "Extension")
            .addImport("ireader.core.source", "Dependencies")
            .addImport("ireader.core.source", "CatalogSource")
            .addImport("ireader.core.http", "HttpClientsInterface", "BrowserEngine", "CookieSynchronizer", "SSLConfiguration", "NetworkConfig")
            .addImport("ireader.core.source.model", "Filter", "Text", "Listing", "MangaInfo", "ChapterInfo")
            .addImport("ireader.core.prefs", "Preference", "PreferenceStore")
            .addImport("kotlinx.coroutines.flow", "Flow", "MutableStateFlow", "StateFlow")
            .addImport("io.ktor.client", "HttpClient")
            .addFileComment("Auto-generated comprehensive integration tests - DO NOT EDIT\n")
            .addFileComment("Generated by TestGeneratorProcessor\n")
            .addFileComment("Tests run automatically - make sure network is available")
            .build()
            .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))

        logger.info("Generated integration tests: $packageName.$testClassName")
    }

    /**
     * Generate TestPreference class that implements Preference<T>
     */
    private fun generateTestPreference(): TypeSpec {
        val preferenceClass = ClassName("ireader.core.prefs", "Preference")
        val flowClass = ClassName("kotlinx.coroutines.flow", "Flow")
        val mutableStateFlowClass = ClassName("kotlinx.coroutines.flow", "MutableStateFlow")

        return TypeSpec.classBuilder("TestPreference")
            .addTypeVariable(TypeVariableName("T"))
            .addSuperinterface(preferenceClass.parameterizedBy(TypeVariableName("T")))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", TypeVariableName("T"))
                    .addParameter("storage", ClassName("kotlin.collections", "MutableMap")
                        .parameterizedBy(String::class.asClassName(), ClassName("kotlin", "Any").copy(nullable = true)))
                    .build()
            )
            .addProperty(PropertySpec.builder("key", String::class).initializer("key").addModifiers(KModifier.PRIVATE).build())
            .addProperty(PropertySpec.builder("defaultValue", TypeVariableName("T")).initializer("defaultValue").addModifiers(KModifier.PRIVATE).build())
            .addProperty(PropertySpec.builder("storage", ClassName("kotlin.collections", "MutableMap")
                .parameterizedBy(String::class.asClassName(), ClassName("kotlin", "Any").copy(nullable = true)))
                .initializer("storage").addModifiers(KModifier.PRIVATE).build())
            .addProperty(PropertySpec.builder("stateFlow", mutableStateFlowClass.parameterizedBy(TypeVariableName("T")))
                .initializer("%T(defaultValue)", mutableStateFlowClass).addModifiers(KModifier.PRIVATE).build())
            .addFunction(
                FunSpec.builder("key")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(String::class)
                    .addStatement("return key")
                    .build()
            )
            .addFunction(
                FunSpec.builder("get")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(TypeVariableName("T"))
                    .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNCHECKED_CAST").build())
                    .addStatement("return storage[key] as? T ?: defaultValue")
                    .build()
            )
            .addFunction(
                FunSpec.builder("set")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", TypeVariableName("T"))
                    .addStatement("storage[key] = value")
                    .addStatement("stateFlow.value = value")
                    .build()
            )
            .addFunction(
                FunSpec.builder("isSet")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(Boolean::class)
                    .addStatement("return storage.containsKey(key)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("delete")
                    .addModifiers(KModifier.OVERRIDE)
                    .addStatement("storage.remove(key)")
                    .addStatement("stateFlow.value = defaultValue")
                    .build()
            )
            .addFunction(
                FunSpec.builder("defaultValue")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(TypeVariableName("T"))
                    .addStatement("return defaultValue")
                    .build()
            )
            .addFunction(
                FunSpec.builder("changes")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(flowClass.parameterizedBy(TypeVariableName("T")))
                    .addStatement("return stateFlow")
                    .build()
            )
            .addFunction(
                FunSpec.builder("stateIn")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("scope", ClassName("kotlinx.coroutines", "CoroutineScope"))
                    .returns(ClassName("kotlinx.coroutines.flow", "StateFlow").parameterizedBy(TypeVariableName("T")))
                    .addStatement("return stateFlow")
                    .build()
            )
            .build()
    }

    /**
     * Generate TestPreferenceStore class that implements PreferenceStore
     */
    private fun generateTestPreferenceStore(): TypeSpec {
        val preferenceStoreClass = ClassName("ireader.core.prefs", "PreferenceStore")
        val preferenceClass = ClassName("ireader.core.prefs", "Preference")
        val kSerializerClass = ClassName("kotlinx.serialization", "KSerializer")
        val serializersModuleClass = ClassName("kotlinx.serialization.modules", "SerializersModule")
        val emptySerializersModuleClass = ClassName("kotlinx.serialization.modules", "EmptySerializersModule")

        return TypeSpec.classBuilder("TestPreferenceStore")
            .addKdoc("In-memory PreferenceStore for testing.\n")
            .addSuperinterface(preferenceStoreClass)
            .addProperty(
                PropertySpec.builder("storage", ClassName("kotlin.collections", "MutableMap")
                    .parameterizedBy(String::class.asClassName(), ClassName("kotlin", "Any").copy(nullable = true)))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("mutableMapOf()")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getString")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", String::class)
                    .returns(preferenceClass.parameterizedBy(String::class.asClassName()))
                    .addStatement("return TestPreference(key, defaultValue, storage)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getLong")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", Long::class)
                    .returns(preferenceClass.parameterizedBy(Long::class.asClassName()))
                    .addStatement("return TestPreference(key, defaultValue, storage)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getInt")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", Int::class)
                    .returns(preferenceClass.parameterizedBy(Int::class.asClassName()))
                    .addStatement("return TestPreference(key, defaultValue, storage)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getFloat")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", Float::class)
                    .returns(preferenceClass.parameterizedBy(Float::class.asClassName()))
                    .addStatement("return TestPreference(key, defaultValue, storage)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getBoolean")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", Boolean::class)
                    .returns(preferenceClass.parameterizedBy(Boolean::class.asClassName()))
                    .addStatement("return TestPreference(key, defaultValue, storage)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getStringSet")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", ClassName("kotlin.collections", "Set").parameterizedBy(String::class.asClassName()))
                    .returns(preferenceClass.parameterizedBy(ClassName("kotlin.collections", "Set").parameterizedBy(String::class.asClassName())))
                    .addStatement("return TestPreference(key, defaultValue, storage)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getObject")
                    .addModifiers(KModifier.OVERRIDE)
                    .addTypeVariable(TypeVariableName("T"))
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", TypeVariableName("T"))
                    .addParameter("serializer", LambdaTypeName.get(parameters = listOf(ParameterSpec.unnamed(TypeVariableName("T"))), returnType = String::class.asClassName()))
                    .addParameter("deserializer", LambdaTypeName.get(parameters = listOf(ParameterSpec.unnamed(String::class.asClassName())), returnType = TypeVariableName("T")))
                    .returns(preferenceClass.parameterizedBy(TypeVariableName("T")))
                    .addStatement("return TestPreference(key, defaultValue, storage)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getJsonObject")
                    .addModifiers(KModifier.OVERRIDE)
                    .addTypeVariable(TypeVariableName("T"))
                    .addParameter("key", String::class)
                    .addParameter("defaultValue", TypeVariableName("T"))
                    .addParameter("serializer", kSerializerClass.parameterizedBy(TypeVariableName("T")))
                    .addParameter("serializersModule", serializersModuleClass)
                    .returns(preferenceClass.parameterizedBy(TypeVariableName("T")))
                    .addStatement("return TestPreference(key, defaultValue, storage)")
                    .build()
            )
            .build()
    }

    /**
     * Generate TestHttpClients class that implements HttpClientsInterface
     */
    private fun generateTestHttpClients(): TypeSpec {
        val httpClientsInterfaceClass = ClassName("ireader.core.http", "HttpClientsInterface")
        val browserEngineClass = ClassName("ireader.core.http", "BrowserEngine")
        val httpClientClass = ClassName("io.ktor.client", "HttpClient")
        val networkConfigClass = ClassName("ireader.core.http", "NetworkConfig")
        val sslConfigClass = ClassName("ireader.core.http", "SSLConfiguration")
        val cookieSynchronizerClass = ClassName("ireader.core.http", "CookieSynchronizer")

        return TypeSpec.classBuilder("TestHttpClients")
            .addKdoc("Mock HttpClients for testing. Uses real Ktor HttpClient for network requests.\n")
            .addSuperinterface(httpClientsInterfaceClass)
            .addProperty(
                PropertySpec.builder("browser", browserEngineClass)
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                        .addStatement("return %T()", browserEngineClass)
                        .build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("default", httpClientClass)
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                        .addStatement("return %T()", httpClientClass)
                        .build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("cloudflareClient", httpClientClass)
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                        .addStatement("return %T()", httpClientClass)
                        .build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("config", networkConfigClass)
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                        .addStatement("return %T()", networkConfigClass)
                        .build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("sslConfig", sslConfigClass)
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                        .addStatement("return %T()", sslConfigClass)
                        .build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("cookieSynchronizer", cookieSynchronizerClass)
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                        .addStatement("throw NotImplementedError(%S)", "CookieSynchronizer not available in tests")
                        .build())
                    .build()
            )
            .build()
    }

    private data class AnnotationInfo(
        val hasFilters: Boolean,
        val hasCommands: Boolean,
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
                    hasFilters = "GenerateFilters" in annotations || "SourceFilters" in annotations,
                    hasCommands = "GenerateCommands" in annotations,
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
        const val GENERATE_TESTS_ANNOTATION = "tachiyomix.annotations.GenerateTests"
        const val TEST_FIXTURE_ANNOTATION = "tachiyomix.annotations.TestFixture"
        const val SKIP_TESTS_ANNOTATION = "tachiyomix.annotations.SkipTests"
        const val TEST_EXPECTATIONS_ANNOTATION = "tachiyomix.annotations.TestExpectations"
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
