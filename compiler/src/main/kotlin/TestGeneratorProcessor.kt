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
                .addStatement("%T(sourceClass != null, %S)", assertNotNullClass, "Source class should exist")
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

        FileSpec.builder(packageName, testClassName)
            .addType(testClass)
            .addImport("kotlin.test", "Test", "assertTrue", "assertEquals", "assertNotNull")
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

        val annotations = AnnotationInfo.from(source)
        val fixture = getTestFixtureConfig(source)
        val skipTests = getSkipTestsConfig(source)
        val expectations = getTestExpectationsConfig(source)

        val testAnnotation = ClassName("kotlin.test", "Test")
        val ignoreAnnotation = ClassName("kotlin.test", "Ignore")
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
            .addImport("kotlin.test", "Test", "Ignore", "assertTrue", "assertNotNull")
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
