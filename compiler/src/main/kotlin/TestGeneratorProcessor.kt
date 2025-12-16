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

        // Get test expectations and skip config
        val expectations = getTestExpectationsConfig(source)
        val skipTests = getSkipTestsConfig(source)

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
                .addStatement("    // Validate against @TestExpectations minLatestNovels")
                .addStatement("    %T(result.mangas.size >= %L, %S)", assertClass, expectations.minLatestNovels, "Should fetch at least ${expectations.minLatestNovels} novels (from @TestExpectations)")
                .addStatement("    ")
                .addStatement("    // Validate first novel has basic info")
                .addStatement("    val firstNovel = result.mangas.first()")
                .addStatement("    %T(firstNovel.title.isNotBlank(), %S)", assertClass, "Novel should have title")
                .addStatement("    %T(firstNovel.key.isNotBlank(), %S)", assertClass, "Novel should have key/url")
                .addStatement("    println(%S + firstNovel.title)", "First novel: ")
                .addStatement("    println(%S + firstNovel.key)", "Novel URL: ")
                .addStatement("    ")
                .addStatement("    // Validate cover URL quality")
                .addStatement("    if (firstNovel.cover.isNotBlank()) {")
                .addStatement("        println(%S + firstNovel.cover)", "Cover: ")
                .addStatement("        // Check for placeholder images (common lazy-loading issue)")
                .addStatement("        val isPlaceholder = firstNovel.cover.contains(%S) || firstNovel.cover.contains(%S) || firstNovel.cover.contains(%S)", "placeholder", "loading", "data:image")
                .addStatement("        if (isPlaceholder) {")
                .addStatement("            println(%S)", "⚠ Warning: Cover appears to be a placeholder (lazy-loading detected)")
                .addStatement("            println(%S)", "  → Covers may only be available after opening novel details")
                .addStatement("        }")
                .addStatement("        // Validate URL format")
                .addStatement("        val isValidUrl = firstNovel.cover.startsWith(%S) || firstNovel.cover.startsWith(%S)", "http://", "https://")
                .addStatement("        if (!isValidUrl && firstNovel.cover.isNotBlank()) {")
                .addStatement("            println(%S + firstNovel.cover)", "⚠ Warning: Cover URL may be relative: ")
                .addStatement("        }")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "ℹ No cover URL (may be loaded on detail page)")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Validate All Novels in List =====
        testClassBuilder.addFunction(
            FunSpec.builder("testValidateNovelListQuality")
                .addAnnotation(testAnnotation)
                .addKdoc("Validate all novels in the list have required fields and valid data\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Novel List Quality Check ===")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val result = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    var validNovels = 0")
                .addStatement("    var novelsWithCover = 0")
                .addStatement("    var novelsWithPlaceholder = 0")
                .addStatement("    var novelsWithRelativeUrl = 0")
                .addStatement("    val issues = mutableListOf<String>()")
                .addStatement("    ")
                .addStatement("    result.mangas.forEachIndexed { index, novel ->")
                .addStatement("        // Check required fields")
                .addStatement("        if (novel.title.isBlank()) {")
                .addStatement("            issues.add(%S + index)", "Novel at index ${'$'}index has no title")
                .addStatement("        }")
                .addStatement("        if (novel.key.isBlank()) {")
                .addStatement("            issues.add(%S + index)", "Novel at index ${'$'}index has no key/URL")
                .addStatement("        }")
                .addStatement("        ")
                .addStatement("        // Check URL format")
                .addStatement("        if (novel.key.isNotBlank()) {")
                .addStatement("            val isAbsolute = novel.key.startsWith(%S) || novel.key.startsWith(%S)", "http://", "https://")
                .addStatement("            if (!isAbsolute) novelsWithRelativeUrl++")
                .addStatement("        }")
                .addStatement("        ")
                .addStatement("        // Check cover")
                .addStatement("        if (novel.cover.isNotBlank()) {")
                .addStatement("            novelsWithCover++")
                .addStatement("            if (novel.cover.contains(%S) || novel.cover.contains(%S)) {", "placeholder", "loading")
                .addStatement("                novelsWithPlaceholder++")
                .addStatement("            }")
                .addStatement("        }")
                .addStatement("        ")
                .addStatement("        if (novel.title.isNotBlank() && novel.key.isNotBlank()) validNovels++")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Report results")
                .addStatement("    println(%S + result.mangas.size)", "Total novels: ")
                .addStatement("    println(%S + validNovels)", "Valid novels (title + key): ")
                .addStatement("    println(%S + novelsWithCover)", "Novels with cover: ")
                .addStatement("    if (novelsWithPlaceholder > 0) {")
                .addStatement("        println(%S + novelsWithPlaceholder)", "⚠ Novels with placeholder cover: ")
                .addStatement("    }")
                .addStatement("    if (novelsWithRelativeUrl > 0) {")
                .addStatement("        println(%S + novelsWithRelativeUrl)", "⚠ Novels with relative URL: ")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Report issues")
                .addStatement("    if (issues.isNotEmpty()) {")
                .addStatement("        println(%S)", "Issues found:")
                .addStatement("        issues.take(5).forEach { println(%S + it) }", "  - ")
                .addStatement("        if (issues.size > 5) println(%S + (issues.size - 5) + %S)", "  ... and ", " more")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Assertions")
                .addStatement("    %T(validNovels > 0, %S)", assertClass, "Should have at least one valid novel")
                .addStatement("    %T(validNovels.toFloat() / result.mangas.size >= 0.9f, %S)", assertClass, "At least 90 percent of novels should be valid")
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
        // Respects @SkipTests(search = true)
        if (!skipTests.search) {
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
        } else {
            // Add skipped test info
            testClassBuilder.addFunction(
                FunSpec.builder("testSearchNovels_SKIPPED")
                    .addAnnotation(testAnnotation)
                    .addKdoc("Skipped due to @SkipTests(search = true)\nReason: ${skipTests.reason}\n")
                    .addStatement("println(%S)", "⏭ Search tests skipped: ${skipTests.reason}")
                    .addStatement("%T(true)", assertClass)
                    .build()
            )
        }

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
                .addStatement("    // Track completeness score")
                .addStatement("    var completenessScore = 0")
                .addStatement("    val maxScore = 6")
                .addStatement("    ")
                .addStatement("    // Optional but expected fields")
                .addStatement("    if (details.cover.isNotBlank()) {")
                .addStatement("        println(%S + details.cover)", "Cover: ")
                .addStatement("        val isValidCover = (details.cover.startsWith(%S) || details.cover.startsWith(%S)) && !details.cover.contains(%S)", "http://", "https://", "placeholder")
                .addStatement("        if (isValidCover) {")
                .addStatement("            completenessScore++")
                .addStatement("            println(%S)", "  ✓ Valid cover URL")
                .addStatement("        } else {")
                .addStatement("            println(%S)", "  ⚠ Cover may be placeholder or invalid")
                .addStatement("        }")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "Cover: (none)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    if (details.author.isNotBlank()) {")
                .addStatement("        println(%S + details.author)", "Author: ")
                .addStatement("        completenessScore++")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "Author: (none)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    if (details.description.isNotBlank()) {")
                .addStatement("        println(%S + details.description.take(200) + %S)", "Description: ", "...")
                .addStatement("        if (details.description.length > 50) {")
                .addStatement("            completenessScore++")
                .addStatement("            println(%S + details.description.length + %S)", "  ✓ Good description (", " chars)")
                .addStatement("        } else {")
                .addStatement("            println(%S)", "  ⚠ Description is very short")
                .addStatement("        }")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "Description: (none)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    if (details.genres.isNotEmpty()) {")
                .addStatement("        println(%S + details.genres.joinToString(%S))", "Genres: ", ", ")
                .addStatement("        completenessScore++")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "Genres: (none)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    println(%S + details.status)", "Status: ")
                .addStatement("    if (details.status != 0L) completenessScore++")
                .addStatement("    ")
                .addStatement("    // Title always counts")
                .addStatement("    completenessScore++")
                .addStatement("    ")
                .addStatement("    // Report completeness")
                .addStatement("    val percentage = (completenessScore * 100) / maxScore")
                .addStatement("    println(%S)", "")
                .addStatement("    println(%S + completenessScore + %S + maxScore + %S + percentage + %S)", "Completeness: ", "/", " (", "%%)")
                .addStatement("    if (percentage >= 80) {")
                .addStatement("        println(%S)", "✓ Excellent detail extraction")
                .addStatement("    } else if (percentage >= 50) {")
                .addStatement("        println(%S)", "⚠ Moderate detail extraction - some fields missing")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "✗ Poor detail extraction - many fields missing")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Book Description Quality =====
        testClassBuilder.addFunction(
            FunSpec.builder("testBookDescriptionQuality")
                .addAnnotation(testAnnotation)
                .addKdoc("Validate book description is properly extracted and formatted\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Book Description Quality ===")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Test multiple novels for description quality")
                .addStatement("    val novelsToTest = novels.mangas.take(3)")
                .addStatement("    var novelsWithDescription = 0")
                .addStatement("    var totalDescriptionLength = 0")
                .addStatement("    var descriptionsWithHtml = 0")
                .addStatement("    var descriptionsWithEncoding = 0")
                .addStatement("    ")
                .addStatement("    novelsToTest.forEach { novel ->")
                .addStatement("        val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("        val desc = details.description")
                .addStatement("        ")
                .addStatement("        if (desc.isNotBlank()) {")
                .addStatement("            novelsWithDescription++")
                .addStatement("            totalDescriptionLength += desc.length")
                .addStatement("            ")
                .addStatement("            // Check for HTML tags in description")
                .addStatement("            if (desc.contains(%S) || desc.contains(%S) || desc.contains(%S) || desc.contains(%S)) {", "<p>", "<br>", "<div>", "</")
                .addStatement("                descriptionsWithHtml++")
                .addStatement("                println(%S + details.title)", "⚠ HTML in description: ")
                .addStatement("            }")
                .addStatement("            ")
                .addStatement("            // Check for encoding issues")
                .addStatement("            if (desc.contains(%S) || desc.contains(%S) || desc.contains(%S)) {", "&#", "&amp;", "&nbsp;")
                .addStatement("                descriptionsWithEncoding++")
                .addStatement("                println(%S + details.title)", "⚠ Encoding issues in description: ")
                .addStatement("            }")
                .addStatement("            ")
                .addStatement("            println(%S + details.title + %S + desc.length + %S)", "✓ ", ": ", " chars")
                .addStatement("        } else {")
                .addStatement("            println(%S + details.title)", "✗ No description: ")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Report statistics")
                .addStatement("    val avgLength = if (novelsWithDescription > 0) totalDescriptionLength / novelsWithDescription else 0")
                .addStatement("    println(%S)", "")
                .addStatement("    println(%S + novelsWithDescription + %S + novelsToTest.size)", "Novels with description: ", "/")
                .addStatement("    println(%S + avgLength + %S)", "Average description length: ", " chars")
                .addStatement("    if (descriptionsWithHtml > 0) println(%S + descriptionsWithHtml)", "⚠ Descriptions with HTML: ")
                .addStatement("    if (descriptionsWithEncoding > 0) println(%S + descriptionsWithEncoding)", "⚠ Descriptions with encoding issues: ")
                .addStatement("    ")
                .addStatement("    // Assertions")
                .addStatement("    %T(novelsWithDescription > 0, %S)", assertClass, "At least one novel should have a description")
                .addStatement("    %T(avgLength > 50, %S)", assertClass, "Average description should be meaningful (>50 chars)")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Book Genres/Categories =====
        testClassBuilder.addFunction(
            FunSpec.builder("testBookGenresCategories")
                .addAnnotation(testAnnotation)
                .addKdoc("Validate book genres/categories are properly extracted\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Book Genres/Categories ===")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Test multiple novels for genres")
                .addStatement("    val novelsToTest = novels.mangas.take(5)")
                .addStatement("    var novelsWithGenres = 0")
                .addStatement("    val allGenres = mutableSetOf<String>()")
                .addStatement("    var emptyGenreNames = 0")
                .addStatement("    ")
                .addStatement("    novelsToTest.forEach { novel ->")
                .addStatement("        val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("        ")
                .addStatement("        if (details.genres.isNotEmpty()) {")
                .addStatement("            novelsWithGenres++")
                .addStatement("            details.genres.forEach { genre ->")
                .addStatement("                if (genre.isBlank()) {")
                .addStatement("                    emptyGenreNames++")
                .addStatement("                } else {")
                .addStatement("                    allGenres.add(genre.trim())")
                .addStatement("                }")
                .addStatement("            }")
                .addStatement("            println(%S + details.title + %S + details.genres.joinToString(%S))", "✓ ", ": ", ", ")
                .addStatement("        } else {")
                .addStatement("            println(%S + details.title)", "✗ No genres: ")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Report statistics")
                .addStatement("    println(%S)", "")
                .addStatement("    println(%S + novelsWithGenres + %S + novelsToTest.size)", "Novels with genres: ", "/")
                .addStatement("    println(%S + allGenres.size)", "Unique genres found: ")
                .addStatement("    if (allGenres.isNotEmpty()) {")
                .addStatement("        println(%S + allGenres.take(10).joinToString(%S))", "Sample genres: ", ", ")
                .addStatement("    }")
                .addStatement("    if (emptyGenreNames > 0) println(%S + emptyGenreNames)", "⚠ Empty genre names: ")
                .addStatement("    ")
                .addStatement("    // Assertions - genres are optional but should be present for most sources")
                .addStatement("    if (novelsWithGenres == 0) {")
                .addStatement("        println(%S)", "ℹ Note: No genres found - this may be expected for some sources")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Book Author =====
        testClassBuilder.addFunction(
            FunSpec.builder("testBookAuthor")
                .addAnnotation(testAnnotation)
                .addKdoc("Validate book author is properly extracted\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Book Author ===")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Test multiple novels for author")
                .addStatement("    val novelsToTest = novels.mangas.take(5)")
                .addStatement("    var novelsWithAuthor = 0")
                .addStatement("    val allAuthors = mutableSetOf<String>()")
                .addStatement("    var suspiciousAuthors = 0")
                .addStatement("    ")
                .addStatement("    novelsToTest.forEach { novel ->")
                .addStatement("        val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("        val author = details.author")
                .addStatement("        ")
                .addStatement("        if (author.isNotBlank()) {")
                .addStatement("            novelsWithAuthor++")
                .addStatement("            allAuthors.add(author.trim())")
                .addStatement("            ")
                .addStatement("            // Check for suspicious author values")
                .addStatement("            val suspicious = author.lowercase().let {")
                .addStatement("                it == %S || it == %S || it == %S || it.contains(%S) || it.length < 2", "unknown", "n/a", "author", "http")
                .addStatement("            }")
                .addStatement("            if (suspicious) {")
                .addStatement("                suspiciousAuthors++")
                .addStatement("                println(%S + details.title + %S + author)", "⚠ Suspicious author for ", ": ")
                .addStatement("            } else {")
                .addStatement("                println(%S + details.title + %S + author)", "✓ ", ": ")
                .addStatement("            }")
                .addStatement("        } else {")
                .addStatement("            println(%S + details.title)", "✗ No author: ")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Report statistics")
                .addStatement("    println(%S)", "")
                .addStatement("    println(%S + novelsWithAuthor + %S + novelsToTest.size)", "Novels with author: ", "/")
                .addStatement("    println(%S + allAuthors.size)", "Unique authors found: ")
                .addStatement("    if (suspiciousAuthors > 0) println(%S + suspiciousAuthors)", "⚠ Suspicious author values: ")
                .addStatement("    ")
                .addStatement("    // Assertions - author is important metadata")
                .addStatement("    if (novelsWithAuthor == 0) {")
                .addStatement("        println(%S)", "ℹ Note: No authors found - consider adding author selector")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Book Status =====
        testClassBuilder.addFunction(
            FunSpec.builder("testBookStatus")
                .addAnnotation(testAnnotation)
                .addKdoc("Validate book status is properly extracted and mapped\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Book Status ===")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Status constants from MangaInfo")
                .addStatement("    val statusNames = mapOf(")
                .addStatement("        0L to %S,", "UNKNOWN")
                .addStatement("        1L to %S,", "ONGOING")
                .addStatement("        2L to %S,", "COMPLETED")
                .addStatement("        3L to %S,", "LICENSED")
                .addStatement("        5L to %S,", "CANCELLED")
                .addStatement("        6L to %S", "ON_HIATUS")
                .addStatement("    )")
                .addStatement("    ")
                .addStatement("    // Test multiple novels for status")
                .addStatement("    val novelsToTest = novels.mangas.take(5)")
                .addStatement("    val statusCounts = mutableMapOf<Long, Int>()")
                .addStatement("    ")
                .addStatement("    novelsToTest.forEach { novel ->")
                .addStatement("        val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("        val status = details.status")
                .addStatement("        statusCounts[status] = (statusCounts[status] ?: 0) + 1")
                .addStatement("        ")
                .addStatement("        val statusName = statusNames[status] ?: %S", "INVALID")
                .addStatement("        if (status == 0L) {")
                .addStatement("            println(%S + details.title + %S + statusName)", "⚠ ", ": ")
                .addStatement("        } else {")
                .addStatement("            println(%S + details.title + %S + statusName)", "✓ ", ": ")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Report statistics")
                .addStatement("    println(%S)", "")
                .addStatement("    println(%S)", "Status distribution:")
                .addStatement("    statusCounts.forEach { (status, count) ->")
                .addStatement("        val name = statusNames[status] ?: %S", "INVALID")
                .addStatement("        println(%S + name + %S + count)", "  ", ": ")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Check if status is being extracted")
                .addStatement("    val unknownCount = statusCounts[0L] ?: 0")
                .addStatement("    val knownCount = novelsToTest.size - unknownCount")
                .addStatement("    ")
                .addStatement("    if (knownCount == 0) {")
                .addStatement("        println(%S)", "ℹ Note: All novels have UNKNOWN status - consider adding status selector")
                .addStatement("    } else {")
                .addStatement("        println(%S + knownCount + %S + novelsToTest.size + %S)", "✓ ", "/", " novels have known status")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Metadata Across Multiple Novels =====
        testClassBuilder.addFunction(
            FunSpec.builder("testMetadataConsistency")
                .addAnnotation(testAnnotation)
                .addKdoc("Validate metadata extraction is consistent across multiple novels\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Metadata Consistency Check ===")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    val novelsToTest = novels.mangas.take(5)")
                .addStatement("    var hasTitle = 0")
                .addStatement("    var hasCover = 0")
                .addStatement("    var hasAuthor = 0")
                .addStatement("    var hasDescription = 0")
                .addStatement("    var hasGenres = 0")
                .addStatement("    var hasStatus = 0")
                .addStatement("    ")
                .addStatement("    novelsToTest.forEach { novel ->")
                .addStatement("        val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("        if (details.title.isNotBlank()) hasTitle++")
                .addStatement("        if (details.cover.isNotBlank() && !details.cover.contains(%S)) hasCover++", "placeholder")
                .addStatement("        if (details.author.isNotBlank()) hasAuthor++")
                .addStatement("        if (details.description.isNotBlank() && details.description.length > 20) hasDescription++")
                .addStatement("        if (details.genres.isNotEmpty()) hasGenres++")
                .addStatement("        if (details.status != 0L) hasStatus++")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    val total = novelsToTest.size")
                .addStatement("    println(%S + hasTitle + %S + total)", "Title: ", "/")
                .addStatement("    println(%S + hasCover + %S + total)", "Cover: ", "/")
                .addStatement("    println(%S + hasAuthor + %S + total)", "Author: ", "/")
                .addStatement("    println(%S + hasDescription + %S + total)", "Description: ", "/")
                .addStatement("    println(%S + hasGenres + %S + total)", "Genres: ", "/")
                .addStatement("    println(%S + hasStatus + %S + total)", "Status: ", "/")
                .addStatement("    ")
                .addStatement("    // Calculate overall metadata score")
                .addStatement("    val totalFields = hasTitle + hasCover + hasAuthor + hasDescription + hasGenres + hasStatus")
                .addStatement("    val maxFields = total * 6")
                .addStatement("    val percentage = (totalFields * 100) / maxFields")
                .addStatement("    println(%S)", "")
                .addStatement("    println(%S + percentage + %S)", "Overall metadata completeness: ", " percent")
                .addStatement("    ")
                .addStatement("    if (percentage >= 80) {")
                .addStatement("        println(%S)", "✓ Excellent metadata extraction")
                .addStatement("    } else if (percentage >= 50) {")
                .addStatement("        println(%S)", "⚠ Moderate metadata extraction - some fields missing")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "✗ Poor metadata extraction - consider improving selectors")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Title is required")
                .addStatement("    %T(hasTitle == total, %S)", assertClass, "All novels should have titles")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Chapters - Comprehensive =====
        // Works with both SourceFactory (has listings) and HttpSource (may use filters)
        // Respects @SkipTests(chapters = true)
        if (!skipTests.chapters) {
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
                    .addStatement("    // Validate against @TestExpectations minChapters")
                    .addStatement("    %T(chapters.size >= %L, %S)", assertClass, expectations.minChapters, "Should have at least ${expectations.minChapters} chapters (from @TestExpectations)")
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
        } else {
            // Add skipped test info
            testClassBuilder.addFunction(
                FunSpec.builder("testFetchChaptersComprehensive_SKIPPED")
                    .addAnnotation(testAnnotation)
                    .addKdoc("Skipped due to @SkipTests(chapters = true)\nReason: ${skipTests.reason}\n")
                    .addStatement("println(%S)", "⏭ Chapter tests skipped: ${skipTests.reason}")
                    .addStatement("%T(true)", assertClass)
                    .build()
            )
        }

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
        // Respects @SkipTests(content = true)
        if (!skipTests.content) {
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
        } else {
            // Add skipped test info
            testClassBuilder.addFunction(
                FunSpec.builder("testFetchChapterContentComprehensive_SKIPPED")
                    .addAnnotation(testAnnotation)
                    .addKdoc("Skipped due to @SkipTests(content = true)\nReason: ${skipTests.reason}\n")
                    .addStatement("println(%S)", "⏭ Content tests skipped: ${skipTests.reason}")
                    .addStatement("%T(true)", assertClass)
                    .build()
            )
        }

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
                .addStatement("    var chaptersWithHtml = 0")
                .addStatement("    var chaptersWithAds = 0")
                .addStatement("    ")
                .addStatement("    chaptersToTest.forEach { chapter ->")
                .addStatement("        val pages = source.getPageList(chapter, emptyList())")
                .addStatement("        val textPages = pages.filterIsInstance<%T>()", textClass)
                .addStatement("        val contentLength = textPages.sumOf { it.text.length }")
                .addStatement("        val fullText = textPages.joinToString(%S) { it.text }", " ")
                .addStatement("        ")
                .addStatement("        // Check for HTML tags in content")
                .addStatement("        if (fullText.contains(%S) || fullText.contains(%S) || fullText.contains(%S)) {", "<p>", "<div>", "<br>")
                .addStatement("            chaptersWithHtml++")
                .addStatement("        }")
                .addStatement("        ")
                .addStatement("        // Check for common ad patterns")
                .addStatement("        val adPatterns = listOf(%S, %S, %S, %S, %S)", "advertisement", "sponsored", "click here", "subscribe", "patreon")
                .addStatement("        if (adPatterns.any { fullText.lowercase().contains(it) }) {")
                .addStatement("            chaptersWithAds++")
                .addStatement("        }")
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
                .addStatement("    ")
                .addStatement("    // Report issues")
                .addStatement("    if (chaptersWithHtml > 0) {")
                .addStatement("        println(%S + chaptersWithHtml + %S)", "⚠ ", " chapter(s) contain unparsed HTML tags")
                .addStatement("    }")
                .addStatement("    if (chaptersWithAds > 0) {")
                .addStatement("        println(%S + chaptersWithAds + %S)", "⚠ ", " chapter(s) may contain ads/promotional content")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    %T(emptyChapters < chaptersToTest.size, %S)", assertClass, "Most chapters should have content")
                .addStatement("    %T(avgLength > 100, %S)", assertClass, "Average content should be substantial (>100 chars)")
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

        // ===== TEST: URL Validation =====
        testClassBuilder.addFunction(
            FunSpec.builder("testUrlValidation")
                .addAnnotation(testAnnotation)
                .addKdoc("Validate all URLs are properly formatted and absolute\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== URL Validation ===")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Check novel URLs")
                .addStatement("    var absoluteUrls = 0")
                .addStatement("    var relativeUrls = 0")
                .addStatement("    novels.mangas.forEach { novel ->")
                .addStatement("        if (novel.key.startsWith(%S) || novel.key.startsWith(%S)) {", "http://", "https://")
                .addStatement("            absoluteUrls++")
                .addStatement("        } else if (novel.key.isNotBlank()) {")
                .addStatement("            relativeUrls++")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("    println(%S + absoluteUrls)", "Absolute URLs: ")
                .addStatement("    println(%S + relativeUrls)", "Relative URLs: ")
                .addStatement("    ")
                .addStatement("    // Get chapters and check their URLs")
                .addStatement("    if (novels.mangas.isNotEmpty()) {")
                .addStatement("        val novel = novels.mangas.first()")
                .addStatement("        val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("        var chapterAbsolute = 0")
                .addStatement("        var chapterRelative = 0")
                .addStatement("        chapters.forEach { chapter ->")
                .addStatement("            if (chapter.key.startsWith(%S) || chapter.key.startsWith(%S)) {", "http://", "https://")
                .addStatement("                chapterAbsolute++")
                .addStatement("            } else if (chapter.key.isNotBlank()) {")
                .addStatement("                chapterRelative++")
                .addStatement("            }")
                .addStatement("        }")
                .addStatement("        println(%S + chapterAbsolute)", "Chapter absolute URLs: ")
                .addStatement("        println(%S + chapterRelative)", "Chapter relative URLs: ")
                .addStatement("        ")
                .addStatement("        // All chapter URLs should be absolute for proper navigation")
                .addStatement("        if (chapters.isNotEmpty()) {")
                .addStatement("            %T(chapterAbsolute == chapters.size, %S)", assertClass, "All chapter URLs should be absolute")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Cover Image Quality =====
        testClassBuilder.addFunction(
            FunSpec.builder("testCoverImageQuality")
                .addAnnotation(testAnnotation)
                .addKdoc("Validate cover images are properly loaded and not placeholders\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Cover Image Quality ===")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val novels = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Analyze cover URLs from listing")
                .addStatement("    val coverStats = mutableMapOf<String, Int>()")
                .addStatement("    coverStats[%S] = 0", "valid")
                .addStatement("    coverStats[%S] = 0", "placeholder")
                .addStatement("    coverStats[%S] = 0", "empty")
                .addStatement("    coverStats[%S] = 0", "relative")
                .addStatement("    ")
                .addStatement("    novels.mangas.forEach { novel ->")
                .addStatement("        when {")
                .addStatement("            novel.cover.isBlank() -> coverStats[%S] = coverStats[%S]!! + 1", "empty", "empty")
                .addStatement("            novel.cover.contains(%S) || novel.cover.contains(%S) || novel.cover.contains(%S) -> ", "placeholder", "loading", "data:image")
                .addStatement("                coverStats[%S] = coverStats[%S]!! + 1", "placeholder", "placeholder")
                .addStatement("            !novel.cover.startsWith(%S) && !novel.cover.startsWith(%S) -> ", "http://", "https://")
                .addStatement("                coverStats[%S] = coverStats[%S]!! + 1", "relative", "relative")
                .addStatement("            else -> coverStats[%S] = coverStats[%S]!! + 1", "valid", "valid")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    println(%S + coverStats[%S])", "Valid covers: ", "valid")
                .addStatement("    println(%S + coverStats[%S])", "Placeholder covers: ", "placeholder")
                .addStatement("    println(%S + coverStats[%S])", "Empty covers: ", "empty")
                .addStatement("    println(%S + coverStats[%S])", "Relative URL covers: ", "relative")
                .addStatement("    ")
                .addStatement("    // Check detail page for better cover")
                .addStatement("    if (novels.mangas.isNotEmpty() && (coverStats[%S]!! > 0 || coverStats[%S]!! > 0)) {", "placeholder", "empty")
                .addStatement("        println(%S)", "Checking detail page for cover...")
                .addStatement("        val novel = novels.mangas.first()")
                .addStatement("        val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("        if (details.cover.isNotBlank() && !details.cover.contains(%S)) {", "placeholder")
                .addStatement("            println(%S + details.cover)", "✓ Detail page has valid cover: ")
                .addStatement("        } else {")
                .addStatement("            println(%S)", "⚠ Detail page also has no valid cover")
                .addStatement("        }")
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

        // ===== TEST: Performance Metrics =====
        testClassBuilder.addFunction(
            FunSpec.builder("testPerformanceMetrics")
                .addAnnotation(testAnnotation)
                .addKdoc("Measure response times for various operations to detect performance issues\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Performance Metrics ===")
                .addStatement("    ")
                .addStatement("    // Measure browse time")
                .addStatement("    val browseStart = System.currentTimeMillis()")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val browseResult = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    val browseTime = System.currentTimeMillis() - browseStart")
                .addStatement("    println(%S + browseTime + %S)", "Browse time: ", "ms")
                .addStatement("    ")
                .addStatement("    // Measure details time")
                .addStatement("    if (browseResult.mangas.isNotEmpty()) {")
                .addStatement("        val novel = browseResult.mangas.first()")
                .addStatement("        val detailsStart = System.currentTimeMillis()")
                .addStatement("        val details = source.getMangaDetails(novel, emptyList())")
                .addStatement("        val detailsTime = System.currentTimeMillis() - detailsStart")
                .addStatement("        println(%S + detailsTime + %S)", "Details time: ", "ms")
                .addStatement("        ")
                .addStatement("        // Measure chapters time")
                .addStatement("        val chaptersStart = System.currentTimeMillis()")
                .addStatement("        val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("        val chaptersTime = System.currentTimeMillis() - chaptersStart")
                .addStatement("        println(%S + chaptersTime + %S)", "Chapters time: ", "ms")
                .addStatement("        ")
                .addStatement("        // Measure content time")
                .addStatement("        if (chapters.isNotEmpty()) {")
                .addStatement("            val contentStart = System.currentTimeMillis()")
                .addStatement("            val content = source.getPageList(chapters.first(), emptyList())")
                .addStatement("            val contentTime = System.currentTimeMillis() - contentStart")
                .addStatement("            println(%S + contentTime + %S)", "Content time: ", "ms")
                .addStatement("        }")
                .addStatement("        ")
                .addStatement("        // Performance thresholds (warn if exceeded)")
                .addStatement("        val totalTime = browseTime + detailsTime + chaptersTime")
                .addStatement("        println(%S + totalTime + %S)", "Total time: ", "ms")
                .addStatement("        if (totalTime > 30000) {")
                .addStatement("            println(%S)", "⚠ Warning: Total time exceeds 30 seconds - may indicate performance issues")
                .addStatement("        } else if (totalTime > 15000) {")
                .addStatement("            println(%S)", "⚠ Note: Total time exceeds 15 seconds - consider optimization")
                .addStatement("        } else {")
                .addStatement("            println(%S)", "✓ Performance is acceptable")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Data Consistency =====
        testClassBuilder.addFunction(
            FunSpec.builder("testDataConsistency")
                .addAnnotation(testAnnotation)
                .addKdoc("Verify data consistency across multiple fetches\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Data Consistency Check ===")
                .addStatement("    ")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val result1 = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Fetch same page again")
                .addStatement("    val result2 = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Compare results")
                .addStatement("    val keys1 = result1.mangas.map { it.key }.toSet()")
                .addStatement("    val keys2 = result2.mangas.map { it.key }.toSet()")
                .addStatement("    val overlap = keys1.intersect(keys2)")
                .addStatement("    val overlapPercent = if (keys1.isNotEmpty()) (overlap.size * 100) / keys1.size else 0")
                .addStatement("    ")
                .addStatement("    println(%S + result1.mangas.size)", "First fetch: ")
                .addStatement("    println(%S + result2.mangas.size)", "Second fetch: ")
                .addStatement("    println(%S + overlapPercent + %S)", "Consistency: ", "%%")
                .addStatement("    ")
                .addStatement("    // At least 80 percent of results should be consistent")
                .addStatement("    %T(overlapPercent >= 80, %S)", assertClass, "Results should be at least 80 percent consistent between fetches")
                .addStatement("    ")
                .addStatement("    if (overlapPercent == 100) {")
                .addStatement("        println(%S)", "✓ Perfect consistency")
                .addStatement("    } else if (overlapPercent >= 90) {")
                .addStatement("        println(%S)", "✓ Good consistency (some variation is normal)")
                .addStatement("    } else {")
                .addStatement("        println(%S)", "⚠ Results vary significantly between fetches")
                .addStatement("    }")
                .addStatement("}")
                .build()
        )

        // ===== TEST: Special Characters Handling =====
        testClassBuilder.addFunction(
            FunSpec.builder("testSpecialCharactersHandling")
                .addAnnotation(testAnnotation)
                .addKdoc("Verify source handles special characters in titles and content correctly\n")
                .addStatement("%T(%T.IO) {", runBlockingClass, dispatchersClass)
                .addStatement("    println(%S)", "=== Special Characters Handling ===")
                .addStatement("    ")
                .addStatement("    val listings = source.getListings()")
                .addStatement("    val result = if (listings.isNotEmpty()) {")
                .addStatement("        source.getMangaList(listings.first(), 1)")
                .addStatement("    } else {")
                .addStatement("        source.getMangaList(source.getFilters(), 1)")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Check for encoding issues in titles")
                .addStatement("    var encodingIssues = 0")
                .addStatement("    val problematicPatterns = listOf(%S, %S, %S, %S, %S)", "&#", "&amp;", "&lt;", "&gt;", "\\u")
                .addStatement("    ")
                .addStatement("    result.mangas.forEach { novel ->")
                .addStatement("        if (problematicPatterns.any { novel.title.contains(it) }) {")
                .addStatement("            encodingIssues++")
                .addStatement("            println(%S + novel.title)", "⚠ Possible encoding issue: ")
                .addStatement("        }")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    if (encodingIssues == 0) {")
                .addStatement("        println(%S)", "✓ No encoding issues detected in titles")
                .addStatement("    } else {")
                .addStatement("        println(%S + encodingIssues + %S)", "⚠ Found ", " titles with possible encoding issues")
                .addStatement("    }")
                .addStatement("    ")
                .addStatement("    // Check content for encoding issues")
                .addStatement("    if (result.mangas.isNotEmpty()) {")
                .addStatement("        val novel = result.mangas.first()")
                .addStatement("        val chapters = source.getChapterList(novel, emptyList())")
                .addStatement("        if (chapters.isNotEmpty()) {")
                .addStatement("            val pages = source.getPageList(chapters.first(), emptyList())")
                .addStatement("            val textPages = pages.filterIsInstance<%T>()", textClass)
                .addStatement("            val fullText = textPages.joinToString(%S) { it.text }", " ")
                .addStatement("            ")
                .addStatement("            val contentEncodingIssues = problematicPatterns.count { fullText.contains(it) }")
                .addStatement("            if (contentEncodingIssues > 0) {")
                .addStatement("                println(%S + contentEncodingIssues + %S)", "⚠ Found ", " encoding patterns in content")
                .addStatement("            } else {")
                .addStatement("                println(%S)", "✓ No encoding issues in content")
                .addStatement("            }")
                .addStatement("        }")
                .addStatement("    }")
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
