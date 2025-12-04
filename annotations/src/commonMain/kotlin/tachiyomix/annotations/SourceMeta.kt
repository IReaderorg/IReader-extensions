package tachiyomix.annotations

/**
 * Annotation to provide additional metadata for source extensions.
 * This metadata is used to generate repository index files.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SourceMeta(
    /** Source description shown in the app */
    val description: String = "",
    /** Whether the source contains NSFW content */
    val nsfw: Boolean = false,
    /** Source icon URL or asset path */
    val icon: String = "",
    /** Source website URL (for attribution) */
    val website: String = "",
    /** Tags/categories for the source */
    val tags: Array<String> = []
)

/**
 * Annotation to define supported filters for a source.
 * KSP can generate the getFilters() implementation.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SourceFilters(
    val hasTitle: Boolean = true,
    val hasAuthor: Boolean = false,
    val hasGenre: Boolean = false,
    val hasStatus: Boolean = false,
    val hasSort: Boolean = false,
    val sortOptions: Array<String> = []
)

/**
 * Annotation to define explore/listing endpoints.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class ExploreFetcher(
    val name: String,
    val endpoint: String,
    val selector: String,
    val nameSelector: String = "",
    val linkSelector: String = "",
    val coverSelector: String = "",
    val isSearch: Boolean = false
)

/**
 * Annotation to define detail page selectors.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class DetailSelectors(
    val title: String = "",
    val cover: String = "",
    val author: String = "",
    val description: String = "",
    val genres: String = "",
    val status: String = ""
)

/**
 * Annotation to define chapter list selectors.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ChapterSelectors(
    val list: String = "",
    val name: String = "",
    val link: String = "",
    val date: String = "",
    val reversed: Boolean = false
)

/**
 * Annotation to define content/reader selectors.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ContentSelectors(
    val content: String = "",
    val title: String = "",
    /** Elements to remove before extracting content */
    val removeSelectors: Array<String> = []
)
