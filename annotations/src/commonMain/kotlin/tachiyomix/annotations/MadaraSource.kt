package tachiyomix.annotations

/**
 * Annotation to generate a Madara-based source extension.
 * This eliminates the need to manually create source files for Madara theme sites.
 * 
 * Usage:
 * ```
 * @MadaraSource(
 *     name = "ArMtl",
 *     baseUrl = "https://ar-mtl.club",
 *     lang = "ar",
 *     id = 46
 * )
 * object ArMtlConfig
 * ```
 * 
 * KSP will generate the actual source class that extends Madara.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class MadaraSource(
    val name: String,
    val baseUrl: String,
    val lang: String,
    val id: Long,
    /** Path configuration: novels path, novel path, chapter path */
    val novelsPath: String = "novel",
    val novelPath: String = "novel", 
    val chapterPath: String = "novel"
)

/**
 * Annotation to generate a source from a theme/template.
 * Generic version that works with any theme.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ThemeSource(
    val name: String,
    val baseUrl: String,
    val lang: String,
    val id: Long,
    /** Fully qualified class name of the theme to extend */
    val theme: String
)

/**
 * Annotation to customize selectors for a theme-based source.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class Selector(
    val name: String,
    val value: String
)

/**
 * Annotation to add custom date formats for parsing.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class DateFormat(
    val pattern: String,
    val locale: String = "en_US"
)
