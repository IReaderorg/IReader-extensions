/*
 * Copyright (C) IReader Project
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomix.annotations

/**
 * 🎨 MADARA SOURCE - Create a Madara-based source with ZERO code!
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  Perfect for sites using the Madara WordPress theme!                     ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @MadaraSource(                                                          ║
 * ║      name = "My Novel Site",                                             ║
 * ║      baseUrl = "https://mynovelsite.com",                                ║
 * ║      lang = "en",                                                        ║
 * ║      id = 12345                                                          ║
 * ║  )                                                                       ║
 * ║  object MyNovelSiteConfig  // That's it! No class body needed!           ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * HOW TO IDENTIFY A MADARA SITE:
 * ──────────────────────────────
 * Look for these signs:
 *   • URL pattern: /novel/novel-name/chapter-1/
 *   • "Starter Sites" or "flavor starter" in footer
 *   • Similar layout to other Madara sites
 *   • WordPress admin panel (/wp-admin/)
 * 
 * WHAT KSP GENERATES:
 * ───────────────────
 * A complete source class that:
 *   • Extends the Madara base class
 *   • Handles all scraping automatically
 *   • Supports search, latest, popular listings
 *   • Parses chapters and content
 * 
 * CUSTOMIZING PATHS:
 * ──────────────────
 * Some Madara sites use different URL paths:
 * 
 * ```kotlin
 * @MadaraSource(
 *     name = "Custom Site",
 *     baseUrl = "https://customsite.com",
 *     lang = "en",
 *     id = 12345,
 *     novelsPath = "series",      // Default: "novel"
 *     novelPath = "series",       // Default: "novel"
 *     chapterPath = "series"      // Default: "novel"
 * )
 * object CustomSiteConfig
 * ```
 * 
 * EXAMPLE - MINIMAL:
 * ──────────────────
 * ```kotlin
 * // File: sources/en/mysite/main/src/ireader/mysite/MySite.kt
 * package ireader.mysite
 * 
 * import tachiyomix.annotations.MadaraSource
 * 
 * @MadaraSource(
 *     name = "My Site",
 *     baseUrl = "https://mysite.com",
 *     lang = "en",
 *     id = 12345
 * )
 * object MySiteConfig
 * // Done! KSP generates the rest!
 * ```
 * 
 * NEED MORE CUSTOMIZATION?
 * ────────────────────────
 * If the site needs custom selectors or behavior, use @ThemeSource
 * or create a full source with @Extension instead.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class MadaraSource(
    /** Display name shown in the app (e.g., "Novel Updates") */
    val name: String,
    
    /** Website URL without trailing slash (e.g., "https://example.com") */
    val baseUrl: String,
    
    /** Language code: "en", "es", "ja", "ko", etc. */
    val lang: String,
    
    /** 
     * Unique source ID. Use ./gradlew generateSourceId to get one.
     * Or use @AutoSourceId on a regular @Extension class instead.
     */
    val id: Long,
    
    /** 
     * URL path for novel listings (default: "novel")
     * Example: If novels are at /series/, set this to "series"
     */
    val novelsPath: String = "novel",
    
    /** 
     * URL path for individual novels (default: "novel")
     * Example: If novel pages are at /book/title/, set this to "book"
     */
    val novelPath: String = "novel",
    
    /** 
     * URL path for chapters (default: "novel")
     * Example: If chapters are at /read/title/ch-1/, set this to "read"
     */
    val chapterPath: String = "novel"
)

/**
 * 🎨 THEME SOURCE - Create a source from ANY theme/template
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  For sites using themes OTHER than Madara                                ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @ThemeSource(                                                           ║
 * ║      name = "My Site",                                                   ║
 * ║      baseUrl = "https://mysite.com",                                     ║
 * ║      lang = "en",                                                        ║
 * ║      id = 12345,                                                         ║
 * ║      theme = "ireader.themes.BoxNovel"  // Theme class to extend         ║
 * ║  )                                                                       ║
 * ║  object MySiteConfig                                                     ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * AVAILABLE THEMES:
 * ─────────────────
 * Check the multisrc/ folder for available themes:
 *   • ireader.madara.Madara - WordPress Madara theme
 *   • ireader.themes.BoxNovel - BoxNovel-style sites
 *   • (Add more as they're created)
 * 
 * CUSTOMIZING WITH @Selector:
 * ───────────────────────────
 * Override specific selectors:
 * 
 * ```kotlin
 * @ThemeSource(...)
 * @Selector(name = "novelTitle", value = "h1.custom-title")
 * @Selector(name = "chapterList", value = "div.chapters a")
 * object MySiteConfig
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ThemeSource(
    /** Display name shown in the app */
    val name: String,
    
    /** Website URL without trailing slash */
    val baseUrl: String,
    
    /** Language code: "en", "es", "ja", etc. */
    val lang: String,
    
    /** Unique source ID */
    val id: Long,
    
    /** 
     * Fully qualified class name of the theme to extend.
     * Example: "ireader.madara.Madara" or "ireader.themes.BoxNovel"
     */
    val theme: String
)

/**
 * 🔧 SELECTOR - Override a specific CSS selector for a theme source
 * 
 * Use with @ThemeSource or @MadaraSource to customize selectors:
 * 
 * ```kotlin
 * @ThemeSource(...)
 * @Selector(name = "novelTitle", value = "h1.entry-title")
 * @Selector(name = "chapterContent", value = "div.text-left p")
 * object MySiteConfig
 * ```
 * 
 * Common selector names (depends on theme):
 *   • novelTitle, novelCover, novelDescription
 *   • chapterList, chapterName, chapterLink
 *   • chapterContent, nextPage
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class Selector(
    /** Selector name (defined by the theme) */
    val name: String,
    /** CSS selector value */
    val value: String
)

/**
 * 📅 DATE FORMAT - Add custom date parsing formats
 * 
 * Use when a site has non-standard date formats:
 * 
 * ```kotlin
 * @ThemeSource(...)
 * @DateFormat(pattern = "dd/MM/yyyy")
 * @DateFormat(pattern = "MMMM d, yyyy", locale = "en_US")
 * object MySiteConfig
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class DateFormat(
    /** Date pattern (Java SimpleDateFormat syntax) */
    val pattern: String,
    /** Locale for parsing (default: "en_US") */
    val locale: String = "en_US"
)
