package tachiyomix.annotations

/**
 * Annotation to define an API endpoint for code generation.
 * KSP will generate Ktor request builder functions.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class ApiEndpoint(
    /** Endpoint name (used for function naming) */
    val name: String,
    /** URL path (can include {param} placeholders) */
    val path: String,
    /** HTTP method (GET, POST, etc.) */
    val method: String = "GET",
    /** Parameter names that appear in the path */
    val params: Array<String> = [],
    /** Request body type (for POST/PUT) */
    val bodyType: String = "",
    /** Response type for parsing */
    val responseType: String = ""
)

/**
 * Annotation to define deep link handling for a source.
 * KSP will generate URL matchers and handlers.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class SourceDeepLink(
    /** Host to match (e.g., "www.example.com") */
    val host: String,
    /** URL scheme (default: "https") */
    val scheme: String = "https",
    /** Path pattern to match (regex-like, e.g., "/novel/.*") */
    val pathPattern: String = "",
    /** Type of content this deep link points to */
    val type: String = "MANGA"  // MANGA, CHAPTER, SEARCH
)

/**
 * Annotation to configure rate limiting for a source.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class RateLimit(
    /** Number of requests allowed per period */
    val permits: Int = 2,
    /** Time period in milliseconds */
    val periodMs: Long = 1000,
    /** Whether to apply rate limiting to all requests */
    val applyToAll: Boolean = true
)

/**
 * Annotation to define custom headers for requests.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class CustomHeader(
    /** Header name */
    val name: String,
    /** Header value */
    val value: String
)

/**
 * Annotation to configure Cloudflare bypass settings.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class CloudflareConfig(
    /** Whether the source uses Cloudflare protection */
    val enabled: Boolean = true,
    /** Custom user agent for bypass */
    val userAgent: String = "",
    /** Timeout for WebView bypass in milliseconds */
    val timeoutMs: Long = 30000
)

/**
 * Annotation to mark a source as requiring authentication.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class RequiresAuth(
    /** Type of authentication (COOKIE, TOKEN, BASIC) */
    val type: String = "COOKIE",
    /** Login URL */
    val loginUrl: String = "",
    /** Whether login is required for all operations */
    val required: Boolean = false
)

/**
 * Annotation to define pagination configuration.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Pagination(
    /** Starting page number (usually 0 or 1) */
    val startPage: Int = 1,
    /** Maximum pages to fetch (0 = unlimited) */
    val maxPages: Int = 0,
    /** Items per page (for offset calculation) */
    val itemsPerPage: Int = 20,
    /** Whether pagination uses offset instead of page number */
    val useOffset: Boolean = false
)
