package tachiyomix.annotations

/**
 * 📋 SOURCE META - Add metadata for the repository index
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  OPTIONAL - Adds extra info shown in the app's extension browser         ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @Extension                                                              ║
 * ║  @SourceMeta(                                                            ║
 * ║      description = "Popular light novel site with daily updates",        ║
 * ║      nsfw = false,                                                       ║
 * ║      tags = ["light-novel", "web-novel", "translations"]                 ║
 * ║  )                                                                       ║
 * ║  abstract class MySource(deps: Dependencies) : SourceFactory(deps)       ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * This metadata appears in:
 *   • Extension browser in the app
 *   • Repository index (index.json)
 *   • Source details page
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SourceMeta(
    /** 
     * Short description shown in the app.
     * Keep it brief - 1-2 sentences max.
     * Example: "Official translations of popular web novels"
     */
    val description: String = "",
    
    /** 
     * Mark as true if the source contains adult/NSFW content.
     * NSFW sources are hidden by default in the app.
     */
    val nsfw: Boolean = false,
    
    /** 
     * Icon URL or asset path.
     * Leave empty to use the default icon from res/mipmap.
     */
    val icon: String = "",
    
    /** 
     * Source website URL (for attribution).
     * Example: "https://example.com"
     */
    val website: String = "",
    
    /** 
     * Tags/categories for filtering.
     * Examples: "light-novel", "web-novel", "manga", "manhwa"
     */
    val tags: Array<String> = []
)

/**
 * 🔍 SOURCE FILTERS - Generate filter UI automatically
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  OPTIONAL - Auto-generates getFilters() implementation                   ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @Extension                                                              ║
 * ║  @SourceFilters(                                                         ║
 * ║      hasTitle = true,                                                    ║
 * ║      hasSort = true,                                                     ║
 * ║      sortOptions = ["Latest", "Popular", "Rating"]                       ║
 * ║  )                                                                       ║
 * ║  abstract class MySource(deps: Dependencies) : SourceFactory(deps) {     ║
 * ║      // Use: override fun getFilters() = MySourceFilters.getGeneratedFilters()
 * ║  }                                                                       ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * GENERATED CODE:
 * ───────────────
 * KSP creates MySourceFilters.kt with:
 * ```kotlin
 * object MySourceFilters {
 *     fun getGeneratedFilters() = listOf(
 *         Filter.Title(),
 *         Filter.Sort("Sort By:", arrayOf("Latest", "Popular", "Rating"))
 *     )
 * }
 * ```
 * 
 * For complex filters, write them manually instead.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SourceFilters(
    /** Include title/name search filter (default: true) */
    val hasTitle: Boolean = true,
    
    /** Include author search filter */
    val hasAuthor: Boolean = false,
    
    /** Include genre/category filter */
    val hasGenre: Boolean = false,
    
    /** Include status filter (Ongoing/Completed) */
    val hasStatus: Boolean = false,
    
    /** Include sort dropdown */
    val hasSort: Boolean = false,
    
    /** 
     * Options for the sort dropdown.
     * Required if hasSort = true.
     * Example: ["Latest", "Popular", "A-Z", "Rating"]
     */
    val sortOptions: Array<String> = []
)

/**
 * 🔗 EXPLORE FETCHER - Define a listing/search endpoint
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  Define endpoints declaratively instead of writing code                  ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @Extension                                                              ║
 * ║  @ExploreFetcher(                                                        ║
 * ║      name = "Latest",                                                    ║
 * ║      endpoint = "/novels/page/{page}/",                                  ║
 * ║      selector = ".novel-item",                                           ║
 * ║      nameSelector = ".title",                                            ║
 * ║      linkSelector = "a",                                                 ║
 * ║      coverSelector = "img"                                               ║
 * ║  )                                                                       ║
 * ║  @ExploreFetcher(                                                        ║
 * ║      name = "Search",                                                    ║
 * ║      endpoint = "/search?q={query}&page={page}",                         ║
 * ║      selector = ".search-result",                                        ║
 * ║      isSearch = true                                                     ║
 * ║  )                                                                       ║
 * ║  abstract class MySource(deps: Dependencies) : SourceFactory(deps)       ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * PLACEHOLDERS:
 * ─────────────
 *   {page}  - Page number (1, 2, 3...)
 *   {query} - Search query (URL encoded)
 * 
 * GENERATED CODE:
 * ───────────────
 * KSP creates MySourceFetchers.kt with BaseExploreFetcher objects.
 * Use: override val exploreFetchers = MySourceFetchers.generatedExploreFetchers
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class ExploreFetcher(
    /** 
     * Display name for this listing.
     * Examples: "Latest", "Popular", "Search", "Completed"
     */
    val name: String,
    
    /** 
     * URL endpoint with placeholders.
     * Use {page} for pagination, {query} for search.
     * Example: "/novels/page/{page}/" or "/search?s={query}"
     */
    val endpoint: String,
    
    /** 
     * CSS selector for novel items container.
     * Example: ".novel-list .novel-item" or "div.search-results > div"
     */
    val selector: String,
    
    /** CSS selector for novel title (relative to selector) */
    val nameSelector: String = "",
    
    /** CSS selector for novel link (relative to selector) */
    val linkSelector: String = "",
    
    /** CSS selector for cover image (relative to selector) */
    val coverSelector: String = "",
    
    /** 
     * Set to true if this is a search endpoint.
     * Search endpoints use {query} placeholder.
     */
    val isSearch: Boolean = false
)

/**
 * 📖 DETAIL SELECTORS - Define novel detail page selectors
 * 
 * ```kotlin
 * @Extension
 * @DetailSelectors(
 *     title = "h1.novel-title",
 *     cover = ".novel-cover img",
 *     author = ".author-name",
 *     description = ".novel-summary p",
 *     genres = ".genre-list a",
 *     status = ".novel-status"
 * )
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 * 
 * KSP validates these selectors at compile time!
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class DetailSelectors(
    /** CSS selector for novel title */
    val title: String = "",
    /** CSS selector for cover image */
    val cover: String = "",
    /** CSS selector for author name */
    val author: String = "",
    /** CSS selector for description/summary */
    val description: String = "",
    /** CSS selector for genre tags */
    val genres: String = "",
    /** CSS selector for status (Ongoing/Completed) */
    val status: String = ""
)

/**
 * 📚 CHAPTER SELECTORS - Define chapter list selectors
 * 
 * ```kotlin
 * @Extension
 * @ChapterSelectors(
 *     list = ".chapter-list li",
 *     name = ".chapter-title",
 *     link = "a",
 *     date = ".chapter-date",
 *     reversed = true  // If chapters are newest-first
 * )
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ChapterSelectors(
    /** CSS selector for chapter list items */
    val list: String = "",
    /** CSS selector for chapter name (relative to list item) */
    val name: String = "",
    /** CSS selector for chapter link (relative to list item) */
    val link: String = "",
    /** CSS selector for chapter date (relative to list item) */
    val date: String = "",
    /** Set true if chapters are in reverse order (newest first) */
    val reversed: Boolean = false
)

/**
 * 📄 CONTENT SELECTORS - Define chapter content selectors
 * 
 * ```kotlin
 * @Extension
 * @ContentSelectors(
 *     content = ".chapter-content p",
 *     title = ".chapter-title",
 *     removeSelectors = [".ads", ".author-note", "script"]
 * )
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ContentSelectors(
    /** CSS selector for chapter text content */
    val content: String = "",
    /** CSS selector for chapter title */
    val title: String = "",
    /** 
     * Selectors for elements to REMOVE before extracting content.
     * Useful for removing ads, scripts, author notes, etc.
     */
    val removeSelectors: Array<String> = []
)
