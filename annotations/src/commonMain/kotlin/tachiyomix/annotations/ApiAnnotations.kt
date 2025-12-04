package tachiyomix.annotations

/**
 * 🌐 API ENDPOINT - Define HTTP endpoints for code generation
 * 
 * For sources with REST/JSON APIs. KSP generates Ktor request builders.
 * 
 * ```kotlin
 * @Extension
 * @ApiEndpoint(
 *     name = "getNovelList",
 *     path = "/api/novels?page={page}",
 *     method = "GET",
 *     params = ["page"]
 * )
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class ApiEndpoint(
    /** Endpoint name (used for generated function name) */
    val name: String,
    /** URL path with placeholders like {page}, {query} */
    val path: String,
    /** HTTP method: "GET", "POST", "PUT", "DELETE" */
    val method: String = "GET",
    /** Parameter names in the path */
    val params: Array<String> = [],
    /** Request body type for POST/PUT */
    val bodyType: String = "",
    /** Response type for parsing */
    val responseType: String = ""
)

/**
 * 🔗 SOURCE DEEP LINK - Handle URLs opened from browser
 * 
 * Let users open novels directly from their browser.
 * 
 * ```kotlin
 * @Extension
 * @SourceDeepLink(host = "www.example.com", pathPattern = "/novel/.*")
 * @SourceDeepLink(host = "example.com", pathPattern = "/novel/.*")
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class SourceDeepLink(
    /** Host to match (e.g., "www.example.com") */
    val host: String,
    /** URL scheme: "https" or "http" */
    val scheme: String = "https",
    /** Path pattern (regex-like): "/novel/.*" */
    val pathPattern: String = "",
    /** Content type: "MANGA", "CHAPTER", or "SEARCH" */
    val type: String = "MANGA"
)

/**
 * ⏱️ RATE LIMIT - Prevent getting blocked
 * 
 * ```kotlin
 * @Extension
 * @RateLimit(permits = 2, periodMs = 1000)  // 2 requests per second
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class RateLimit(
    /** Requests allowed per period */
    val permits: Int = 2,
    /** Period in milliseconds */
    val periodMs: Long = 1000,
    /** Apply to all requests */
    val applyToAll: Boolean = true
)

/**
 * 📨 CUSTOM HEADER - Add headers to requests
 * 
 * ```kotlin
 * @Extension
 * @CustomHeader(name = "Referer", value = "https://example.com/")
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
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
 * ☁️ CLOUDFLARE CONFIG - Handle Cloudflare protection
 * 
 * ```kotlin
 * @Extension
 * @CloudflareConfig(enabled = true, timeoutMs = 30000)
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class CloudflareConfig(
    /** Source uses Cloudflare */
    val enabled: Boolean = true,
    /** Custom user agent */
    val userAgent: String = "",
    /** WebView timeout (ms) */
    val timeoutMs: Long = 30000
)

/**
 * 🔐 REQUIRES AUTH - Mark source as requiring login
 * 
 * ```kotlin
 * @Extension
 * @RequiresAuth(type = "COOKIE", loginUrl = "https://example.com/login")
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class RequiresAuth(
    /** Auth type: "COOKIE", "TOKEN", "BASIC" */
    val type: String = "COOKIE",
    /** Login page URL */
    val loginUrl: String = "",
    /** Is login required? */
    val required: Boolean = false
)

/**
 * 📄 PAGINATION - Configure pagination
 * 
 * ```kotlin
 * @Extension
 * @Pagination(startPage = 1, itemsPerPage = 20)
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Pagination(
    /** First page number (0 or 1) */
    val startPage: Int = 1,
    /** Max pages (0 = unlimited) */
    val maxPages: Int = 0,
    /** Items per page */
    val itemsPerPage: Int = 20,
    /** Use offset instead of page */
    val useOffset: Boolean = false
)
