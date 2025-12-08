/*
 * Copyright (C) IReader Project
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomix.annotations

/**
 * 🧪 GENERATE TESTS - Auto-generate test cases for your source
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  OPTIONAL - Automatically creates unit and integration tests             ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @Extension                                                              ║
 * ║  @GenerateTests(                                                         ║
 * ║      unitTests = true,                                                   ║
 * ║      integrationTests = false,  // Set true for network tests            ║
 * ║      searchQuery = "dragon"     // Test search with this query           ║
 * ║  )                                                                       ║
 * ║  abstract class MySource(deps: Dependencies) : SourceFactory(deps)       ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * ENABLING TEST GENERATION:
 * ─────────────────────────
 * Add to your build.gradle.kts:
 * ```kotlin
 * ksp {
 *     arg("generateTests", "true")
 *     arg("generateIntegrationTests", "true")  // Optional
 * }
 * ```
 * 
 * GENERATED TESTS:
 * ────────────────
 * Unit tests (always safe to run):
 *   • Filter validation
 *   • Selector syntax validation
 *   • URL building tests
 *   • Fetcher configuration tests
 * 
 * Integration tests (make network requests):
 *   • Fetch latest novels
 *   • Search functionality
 *   • Novel details parsing
 *   • Chapter list parsing
 *   • Content parsing
 * 
 * RUNNING TESTS:
 * ──────────────
 * ```bash
 * # Unit tests
 * ./gradlew :extensions:individual:en:mysource:test
 * 
 * # Integration tests (requires network)
 * ./gradlew :extensions:individual:en:mysource:connectedTest
 * ```
 * 
 * ─────────────────────────────────────────────────────────────────
 * NOTE: For manual/exploratory testing, use the test-extensions
 * module instead. It provides shared test infrastructure and mocks.
 * See: test-extensions/src/test/java/ireader/app/guide.md
 * ─────────────────────────────────────────────────────────────────
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateTests(
    /** Generate unit tests (no network, always safe) */
    val unitTests: Boolean = true,
    
    /** Generate integration tests (makes real network requests) */
    val integrationTests: Boolean = false,
    
    /** Search query to use in search tests */
    val searchQuery: String = "test",
    
    /** Minimum expected results from search (fails if fewer) */
    val minSearchResults: Int = 1
)

/**
 * 📦 TEST FIXTURE - Provide known-good test data
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  Provide URLs and expected values for more reliable tests                ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @Extension                                                              ║
 * ║  @GenerateTests(integrationTests = true)                                 ║
 * ║  @TestFixture(                                                           ║
 * ║      novelUrl = "https://example.com/novel/my-novel/",                   ║
 * ║      chapterUrl = "https://example.com/novel/my-novel/chapter-1/",       ║
 * ║      expectedTitle = "My Novel Title",                                   ║
 * ║      expectedAuthor = "Author Name"                                      ║
 * ║  )                                                                       ║
 * ║  abstract class MySource(deps: Dependencies) : SourceFactory(deps)       ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * WHY USE THIS:
 * ─────────────
 * • Tests are more reliable with known-good URLs
 * • Can verify exact expected values
 * • Catches regressions when site structure changes
 * 
 * TIPS:
 * ─────
 * • Choose a popular/stable novel that's unlikely to be removed
 * • Use a novel with many chapters for better coverage
 * • Update fixtures if the site changes
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class TestFixture(
    /** Known working novel URL for testing */
    val novelUrl: String = "",
    
    /** Known working chapter URL for testing */
    val chapterUrl: String = "",
    
    /** Expected novel title (test fails if different) */
    val expectedTitle: String = "",
    
    /** Expected author name (test fails if different) */
    val expectedAuthor: String = ""
)

/**
 * ⏭️ SKIP TESTS - Skip specific tests for a source
 * 
 * Use when certain features don't work or aren't applicable:
 * 
 * ```kotlin
 * @Extension
 * @GenerateTests()
 * @SkipTests(
 *     search = true,   // Site doesn't have search
 *     reason = "This site doesn't support search functionality"
 * )
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SkipTests(
    /** Skip search tests */
    val search: Boolean = false,
    
    /** Skip chapter list tests */
    val chapters: Boolean = false,
    
    /** Skip content/reader tests */
    val content: Boolean = false,
    
    /** Reason for skipping (shown in test output) */
    val reason: String = ""
)

/**
 * ✅ TEST EXPECTATIONS - Define expected behavior
 * 
 * Set minimum thresholds for test validation:
 * 
 * ```kotlin
 * @Extension
 * @GenerateTests()
 * @TestExpectations(
 *     minLatestNovels = 10,    // Expect at least 10 novels in latest
 *     minChapters = 5,         // Expect at least 5 chapters per novel
 *     supportsPagination = true,
 *     requiresLogin = false
 * )
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class TestExpectations(
    /** Minimum novels expected from latest listing */
    val minLatestNovels: Int = 1,
    
    /** Minimum chapters expected per novel */
    val minChapters: Int = 1,
    
    /** Whether source supports pagination */
    val supportsPagination: Boolean = true,
    
    /** Whether source requires login for some features */
    val requiresLogin: Boolean = false
)
