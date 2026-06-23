# Common Patterns

## Infinite Scroll Pagination

```kotlin
BaseExploreFetcher(
    key = "popular",
    infinitePage = true,  // Always return hasNextPage = true
)
```

## Custom URL Building

```kotlin
BaseExploreFetcher(
    key = "search",
    endpoint = "/search?q={query}&page={page}",
    onQuery = { query -> query.replace(" ", "+") },
)
```

## Ad/Content Filtering

```kotlin
contentFetcher = content {
    pageContentSelector = "div.chapter-content p"
    onContent { paragraphs ->
        paragraphs.filter { !it.contains("sponsored") }
    }
}
```

## Lazy-Loaded Images

```kotlin
BaseExploreFetcher(
    coverSelector = "img",
    coverAtt = "data-src",  // NOT "src"
)
```

## Reverse Chapter List

```kotlin
chapterFetcher = chapters {
    selector = ".chapter-list li"
    reverseChapterList = true  // If newest chapters first on page
}
```

## Custom User Agent

```kotlin
override fun getUserAgent(): String {
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}
```

## Cloudflare Handling

```kotlin
// Option 1: Annotation
@CloudflareConfig(enabled = true)

// Option 2: Browser engine
val html = deps.httpClients.browser.fetch(url = url, selector = "h1").responseBody
```

## JSON API with Dynamic Parsing

**CRITICAL:** Do NOT use `@Serializable` — causes `IncompatibleClassChangeError`.

```kotlin
val json = Json.parseToJsonElement(response).jsonObject
val title = json["title"]?.jsonPrimitive?.content ?: ""
val items = json["data"]?.jsonArray ?: emptyList()

// Nested objects
val meta = json["meta"]?.jsonObject
val hasNext = meta?.get("hasMore")?.jsonPrimitive?.boolean ?: false
```

## Status Parsing

```kotlin
onStatus = { status ->
    when {
        status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
        status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
        status.contains("Hiatus", ignoreCase = true) -> MangaInfo.ON_HIATUS
        status.contains("Cancelled", ignoreCase = true) -> MangaInfo.CANCELLED
        else -> MangaInfo.UNKNOWN
    }
}
```

## Multiple Listing Types

```kotlin
override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
    val query = filters.findInstance<Filter.Title>()?.value
    val sortFilter = filters.findInstance<Filter.Sort>()?.value?.index

    if (!query.isNullOrBlank()) return searchByTitle(query, page)

    return when (sortFilter) {
        0 -> getLatest(page)
        1 -> getPopular(page)
        else -> getLatest(page)
    }
}
```

## WebView Fallback

```kotlin
override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
    // Check for pre-fetched HTML from WebView
    commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
        if (cmd.html.isNotBlank()) {
            return parseDetailsFromHtml(cmd.html, manga)
        }
    }
    // Fallback to HTTP
    return parseDetailsFromHtml(client.get(requestBuilder(manga.key)).bodyAsText(), manga)
}
```

## Browser Engine for JS-Heavy Sites

```kotlin
private suspend fun fetchBrowser(
    url: String,
    selector: String? = null,
    timeout: Long = 50000
): String? {
    return try {
        val result = deps.httpClients.browser.fetch(
            url = url,
            selector = selector,
            timeout = timeout
        )
        if (result.isSuccess && result.responseBody.isNotBlank()) {
            result.responseBody
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
```

## Retry Logic

```kotlin
private suspend fun <T> retryBrowser(
    times: Int = 2,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
        }
    }
    throw lastException ?: Exception("All attempts failed")
}
```

## Helper Functions

```kotlin
// Convert string to Text page
fun String.toPage(): Page = Text(this)

// Convert list of strings to Text pages
fun List<String>.toPage(): List<Page> = map { it.toPage() }

// Safe selector with fallback
fun safeSelector(element: Element, selector: String?): String {
    return try {
        element.select(selector).text()
    } catch (e: Exception) {
        ""
    }
}
```

## Common CSS Selectors

```css
/* Novel cards */
.novel-item, .book-card, article, li.novel-item

/* Titles */
h1, h2, h3, .title, .novel-title, h3 > a

/* Links */
a, a[href], .title a

/* Images */
img, img[src], img[data-src], .cover img, .thumbnail img

/* Descriptions */
.synopsis, .description, .summary, p.description

/* Authors */
.author, span.author, .author-name, a[href*="author"]

/* Genres */
.genres a, .tags a, .category a, .genre-list a

/* Status */
.status, span.status, .novel-status

/* Chapters */
.chapter-list li, ul.chapters li, table tr, .chapter-item

/* Content */
.chapter-content p, .text-content p, #content p, .entry-content p
```

## URL Placeholders

| Placeholder | Replaced With | Example |
|-------------|---------------|---------|
| `{page}` | Page number (1, 2, 3...) | `/novels/page/{page}/` |
| `{query}` | Search query (URL encoded) | `/search?q={query}` |

```kotlin
endpoint = "/novels/page/{page}/"           // → /novels/page/1/, /novels/page/2/
endpoint = "/search?q={query}&page={page}"  // → /search?q=test&page=1
endpoint = "/list/all/all-onclick-{page}.html"  // → /list/all/all-onclick-1.html
```
