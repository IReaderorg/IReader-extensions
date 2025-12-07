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
