/*
 * Copyright (C) IReader Project
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomix.annotations

/**
 * 🆔 AUTO SOURCE ID - Never manually manage source IDs again!
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  SIMPLE USAGE (99% of cases):                                   │
 * │                                                                 │
 * │    @Extension                                                   │
 * │    @AutoSourceId  // That's it! ID is auto-generated.           │
 * │    abstract class MySource(deps: Dependencies) : ...            │
 * │                                                                 │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * HOW IT WORKS:
 * - KSP generates a stable ID from your source name + language
 * - Same name + lang = same ID (always!)
 * - Different name or lang = different ID
 * 
 * AFTER BUILDING, you can use the generated constant:
 *   override val id: Long get() = MySourceSourceId.ID
 * 
 * ─────────────────────────────────────────────────────────────────
 * ADVANCED: Migrating from manual IDs
 * ─────────────────────────────────────────────────────────────────
 * If you're renaming a source but need to keep the same ID:
 * 
 *   @AutoSourceId(seed = "OldSourceName")
 * 
 * This generates the ID using "OldSourceName" instead of the class name.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AutoSourceId(
    /**
     * Seed for ID generation. Leave empty to use the source name (default).
     * 
     * Only set this if you're renaming a source and need backward compatibility.
     * Example: @AutoSourceId(seed = "OldName") keeps the old ID after rename.
     */
    val seed: String = "",
    
    /**
     * Version for ID generation. Default is 1.
     * 
     * Only increment this if you need a completely new ID for the same source
     * (rare - usually for major rewrites that break user data).
     */
    val version: Int = 1
)

/**
 * 📝 SOURCE CONFIG - Define source properties in one place (OPTIONAL)
 * 
 * This is an ADVANCED annotation. Most sources don't need it.
 * Just use @AutoSourceId for ID generation.
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @SourceConfig(                                                 │
 * │      name = "My Source",                                        │
 * │      baseUrl = "https://example.com",                           │
 * │      lang = "en"                                                │
 * │  )                                                              │
 * │  // Generates: MySourceConfig.NAME, .BASE_URL, .LANG, .ID       │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SourceConfig(
    /** Display name shown in the app */
    val name: String,
    /** Website URL (e.g., "https://example.com") */
    val baseUrl: String,
    /** Language code: "en", "es", "ja", etc. */
    val lang: String,
    /** Explicit ID (leave as -1 for auto-generation) */
    val id: Long = -1L,
    /** Seed for ID generation (advanced, usually leave empty) */
    val idSeed: String = "",
    /** ID version (advanced, usually leave as 1) */
    val idVersion: Int = 1
)

/**
 * 📦 VALIDATE PACKAGE - Auto-check package name matches directory
 * 
 * Automatically enabled for all @Extension classes.
 * You don't need to add this manually.
 * 
 * If your package is wrong (e.g., "ireader.dao" in "daonovel" folder),
 * you'll see a warning during build with instructions to fix it.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ValidatePackage

/**
 * 🔍 GENERATE FILTERS - Auto-generate common filters (OPTIONAL)
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @Extension                                                     │
 * │  @GenerateFilters(                                              │
 * │      title = true,                    // Title search           │
 * │      sort = true,                     // Sort dropdown          │
 * │      sortOptions = ["Latest", "Popular", "Rating"]              │
 * │  )                                                              │
 * │  abstract class MySource(deps: Dependencies) : SourceFactory(deps) │
 * │                                                                 │
 * │  // getFilters() is AUTOMATICALLY implemented - no override needed! │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * The KSP processor automatically generates the getFilters() override
 * in the Extension class. You don't need to write any code!
 * 
 * Skip this if you have custom/complex filters - just write them manually.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateFilters(
    /** Include title search filter (default: true) */
    val title: Boolean = true,
    /** Include author search filter */
    val author: Boolean = false,
    /** Include sort dropdown */
    val sort: Boolean = false,
    /** Options for sort dropdown (required if sort = true) */
    val sortOptions: Array<String> = [],
    /** Include genre filter */
    val genre: Boolean = false,
    /** Options for genre filter (required if genre = true) */
    val genreOptions: Array<String> = [],
    /** Include status filter (Ongoing/Completed) */
    val status: Boolean = false
)

/**
 * ⚡ GENERATE COMMANDS - Auto-generate standard commands (OPTIONAL)
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @Extension                                                     │
 * │  @GenerateCommands(                                             │
 * │      detailFetch = true,              // Fetch novel details    │
 * │      contentFetch = true,             // Fetch chapter content  │
 * │      chapterFetch = true              // Fetch chapter list     │
 * │  )                                                              │
 * │  abstract class MySource(deps: Dependencies) : SourceFactory(deps) │
 * │                                                                 │
 * │  // getCommands() is AUTOMATICALLY implemented - no override needed! │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * The KSP processor automatically generates the getCommands() override
 * in the Extension class. You don't need to write any code!
 * 
 * Most sources use the same commands, so this saves repetitive code.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateCommands(
    /** Generate Command.Detail.Fetch() */
    val detailFetch: Boolean = true,
    /** Generate Command.Content.Fetch() */
    val contentFetch: Boolean = true,
    /** Generate Command.Chapter.Fetch() */
    val chapterFetch: Boolean = true,
    /** Generate Command.WebView() for sites requiring browser */
    val webView: Boolean = false
)


/**
 * 🧪 GENERATE TESTS - Auto-generate integration tests for the source
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @Extension                                                     │
 * │  @GenerateTests(                                                │
 * │      unitTests = true,                                          │
 * │      integrationTests = true,                                   │
 * │      searchQuery = "test",                                      │
 * │      minSearchResults = 1                                       │
 * │  )                                                              │
 * │  abstract class MySource(deps: Dependencies) : SourceFactory(deps) │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * Generates test classes that validate:
 * - Selectors return expected results
 * - URLs are valid and accessible
 * - Search returns results
 * - Chapter content is parseable
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateTests(
    /** Generate unit tests for selectors */
    val unitTests: Boolean = true,
    /** Generate integration tests (requires network) */
    val integrationTests: Boolean = true,
    /** Search query to test */
    val searchQuery: String = "",
    /** Minimum expected search results */
    val minSearchResults: Int = 1
)

/**
 * 📌 TEST FIXTURE - Define test URLs and expected values
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @TestFixture(                                                  │
 * │      novelUrl = "https://example.com/novel/123",                │
 * │      chapterUrl = "https://example.com/novel/123/chapter-1",    │
 * │      expectedTitle = "My Novel Title",                          │
 * │      expectedAuthor = "Author Name"                             │
 * │  )                                                              │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * Used by:
 * - Integration tests to validate selectors
 * - Source health check system to detect broken selectors
 * - Snapshot generation for automated validation
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class TestFixture(
    /** URL of a novel detail page for testing */
    val novelUrl: String,
    /** URL of a chapter content page for testing */
    val chapterUrl: String,
    /** Expected novel title (for validation) */
    val expectedTitle: String = "",
    /** Expected author name (for validation) */
    val expectedAuthor: String = "",
    /** Expected minimum chapter count */
    val expectedMinChapters: Int = 1
)

/**
 * 📊 TEST EXPECTATIONS - Define expected behavior for tests
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @TestExpectations(                                             │
 * │      minLatestNovels = 10,                                      │
 * │      minChapters = 50,                                          │
 * │      supportsPagination = true,                                 │
 * │      requiresLogin = false                                      │
 * │  )                                                              │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * Defines expected behavior that tests will validate.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class TestExpectations(
    /** Minimum novels expected in latest listing */
    val minLatestNovels: Int = 10,
    /** Minimum chapters expected for test novel */
    val minChapters: Int = 1,
    /** Whether the source supports pagination */
    val supportsPagination: Boolean = true,
    /** Whether the source requires login */
    val requiresLogin: Boolean = false,
    /** Whether the source requires JavaScript */
    val requiresJs: Boolean = false
)

/**
 * 🔗 URL VALIDATION - Define URL patterns for validation
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @UrlValidation(                                                │
 * │      novelPattern = "^https://example\\.com/novel/\\d+$",       │
 * │      chapterPattern = "^https://example\\.com/novel/\\d+/\\d+$",│
 * │      coverPattern = "^https?://.*\\.(jpg|png|webp)$"            │
 * │  )                                                              │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * Used by integration tests to validate that URLs returned by
 * selectors match expected patterns.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class UrlValidation(
    /** Regex pattern for valid novel URLs */
    val novelPattern: String = "",
    /** Regex pattern for valid chapter URLs */
    val chapterPattern: String = "",
    /** Regex pattern for valid cover image URLs */
    val coverPattern: String = ""
)

/**
 * 📸 SELECTOR SNAPSHOT - Define expected selector results for health checks
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @SelectorSnapshot(                                             │
 * │      name = "titleSelector",                                    │
 * │      selector = "h1.title",                                     │
 * │      pageType = "detail",                                       │
 * │      expectedValue = "My Novel Title",                          │
 * │      expectedMinCount = 1                                       │
 * │  )                                                              │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * Multiple @SelectorSnapshot annotations can be added to a source.
 * These are used by the source health check system to:
 * - Validate selectors still work
 * - Detect when website structure changes
 * - Suggest fixes using AI when selectors break
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class SelectorSnapshot(
    /** Name of the selector (e.g., "titleSelector", "coverSelector") */
    val name: String,
    /** CSS selector string */
    val selector: String,
    /** Page type: "explore", "detail", "chapters", "content" */
    val pageType: String,
    /** Attribute to extract (empty for text content) */
    val attribute: String = "",
    /** Expected exact value (for validation) */
    val expectedValue: String = "",
    /** Expected regex pattern (alternative to exact value) */
    val expectedPattern: String = "",
    /** Minimum expected match count */
    val expectedMinCount: Int = 1,
    /** Minimum expected text length */
    val expectedMinLength: Int = 0
)
