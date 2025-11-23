# Common Utilities

This module provides shared utilities for IReader extensions to reduce code duplication and improve consistency.

## Available Utilities

### RateLimiter

Controls request frequency to prevent overwhelming servers.

```kotlin
import ireader.common.utils.RateLimiter
import ireader.common.utils.RateLimiterManager

// Get or create a rate limiter for your source
val limiter = RateLimiterManager.getOrCreate(
    sourceId = "mySource",
    permits = 2,        // 2 requests
    periodMillis = 1000 // per second
)

// Use it
limiter.execute {
    client.get(url)
}
```

### HtmlCleaner

Cleans and processes HTML content.

```kotlin
import ireader.common.utils.HtmlCleaner

// Clean HTML
val cleaned = HtmlCleaner.cleanHtml(rawHtml)

// Clean text
val cleanText = HtmlCleaner.cleanText(text)

// Extract paragraphs
val paragraphs = HtmlCleaner.extractParagraphs(element)

// Convert HTML to plain text
val plainText = HtmlCleaner.htmlToText(html)
```

### UrlBuilder

Type-safe URL construction.

```kotlin
import ireader.common.utils.UrlBuilder

val url = UrlBuilder.from("https://example.com")
    .addPath("novels")
    .addPath("page")
    .addQueryParameter("sort", "popular")
    .addQueryParameter("page", "1")
    .build()
// Result: https://example.com/novels/page?sort=popular&page=1

// Or use extension function
val url2 = "https://example.com"
    .toUrlBuilder()
    .addPath("search")
    .addQueryParameter("q", "novel name")
    .build()
```

## Available Utilities

### DateParser

Handles parsing of dates from various formats commonly found in web novels.

```kotlin
import ireader.common.utils.DateParser

// Parse relative dates like "2 hours ago"
val timestamp = DateParser.parseRelativeOrAbsoluteDate("2 hours ago")

// Parse absolute dates
val timestamp2 = DateParser.parseAbsoluteDate("Jan 15, 2024")

// Add custom date format
DateParser.addCustomFormat("dd-MM-yyyy")
```

**Supported relative formats:**
- "X seconds/minutes/hours/days/weeks/months/years ago"

**Supported absolute formats:**
- MMM dd, yyyy
- yyyy-MM-dd HH:mm:ss
- yyyy-MM-dd
- dd/MM/yyyy
- MM/dd/yyyy

### StatusParser

Normalizes publication status strings into standard MangaInfo status codes.

```kotlin
import ireader.common.utils.StatusParser

val status = StatusParser.parseStatus("Ongoing")
// Returns: MangaInfo.ONGOING

// Add custom keywords
StatusParser.addCustomKeywords(MangaInfo.COMPLETED, "finished", "done")
```

**Supported statuses:**
- ONGOING
- COMPLETED
- ON_HIATUS
- CANCELLED
- UNKNOWN

### ErrorHandler

Provides standardized error handling with retry logic and error categorization.

```kotlin
import ireader.common.utils.ErrorHandler

// Execute with retry logic
val result = ErrorHandler.withRetry(
    config = ErrorHandler.RetryConfig(maxAttempts = 3)
) { attempt ->
    // Your network request here
    client.get(url)
}

// Safe request execution
val result = ErrorHandler.safeRequest {
    client.get(url)
}

// Categorize errors
try {
    // ...
} catch (e: Exception) {
    val categorized = ErrorHandler.categorizeError(e)
    when (categorized) {
        is ErrorHandler.SourceError.NetworkError -> // Handle network error
        is ErrorHandler.SourceError.RateLimitError -> // Handle rate limit
        else -> // Handle other errors
    }
}
```

### ImageUrlHelper

Utilities for handling image URLs across different sources.

```kotlin
import ireader.common.utils.ImageUrlHelper

// Normalize URLs
val fullUrl = ImageUrlHelper.normalizeUrl("/images/cover.jpg", "https://example.com")
// Returns: "https://example.com/images/cover.jpg"

// Extract image from lazy-loaded elements
val imageUrl = ImageUrlHelper.extractImageUrl(element)

// Convert thumbnail to full size
val fullSize = ImageUrlHelper.thumbnailToFullSize(thumbnailUrl)

// Validate image URLs
if (ImageUrlHelper.isValidImageUrl(url)) {
    // Process image
}
```

### SelectorConstants

Common CSS selectors for popular website themes.

```kotlin
import ireader.common.utils.SelectorConstants

// WordPress Manga theme
val bookList = document.select(SelectorConstants.WPManga.BOOK_LIST)
val title = document.select(SelectorConstants.WPManga.DETAIL_TITLE)

// Madara theme
val searchResults = document.select(SelectorConstants.Madara.SEARCH_RESULTS)

// Novel theme
val chapters = document.select(SelectorConstants.NovelTheme.CHAPTER_LIST)
```

**Available selector sets:**
- `WPManga` - WordPress Manga theme
- `Madara` - Madara theme
- `NovelTheme` - Common novel website theme

## Usage in Extensions

Add the common module as a dependency (already included in extension-setup):

```kotlin
dependencies {
    compileOnly(project(":common"))
}
```

Then import and use the utilities in your extension:

```kotlin
import ireader.common.utils.*

class MySource : ParsedHttpSource() {
    override fun chapterFromElement(element: Element): ChapterInfo {
        return ChapterInfo(
            name = element.select("a").text(),
            key = element.select("a").attr("href"),
            dateUpload = DateParser.parseRelativeOrAbsoluteDate(
                element.select("span.date").text()
            )
        )
    }
    
    override fun detailParse(document: Document): MangaInfo {
        return MangaInfo(
            title = document.select(SelectorConstants.WPManga.DETAIL_TITLE).text(),
            status = StatusParser.parseStatus(
                document.select(SelectorConstants.WPManga.DETAIL_STATUS).text()
            )
        )
    }
}
```

## Benefits

- **Consistency**: All extensions use the same parsing logic
- **Maintainability**: Fix bugs in one place
- **Reduced code**: Less boilerplate in each extension
- **Better error handling**: Standardized retry and error categorization
- **Type safety**: Fewer magic strings and numbers
