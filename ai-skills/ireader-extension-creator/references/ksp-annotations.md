# KSP Annotations Reference

## Required Annotations

```kotlin
@Extension                    // REQUIRED - marks class as IReader source
@AutoSourceId(seed = "Name")  // REQUIRED - auto-generates stable source ID
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular"])
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
@SourceMeta(description = "...", nsfw = false)
@GenerateTests(unitTests = true, integrationTests = false, searchQuery = "test", minSearchResults = 1)
@TestFixture(
    "https://example.com/novel/test-novel",
    chapterUrl = "https://example.com/novel/test-novel/chapter-1",
    expectedAuthor = "Author",
    expectedTitle = "Test Novel"
)
@TestExpectations()
```

## Source Identity Annotations

| Annotation | Purpose | When to Use |
|------------|---------|-------------|
| `@Extension` | **REQUIRED** - Marks class as IReader source | Every source |
| `@AutoSourceId` | Auto-generate stable source ID | All new sources |
| `@AutoSourceId(seed = "OldName")` | Keep ID when renaming | Migrating sources |
| `@MadaraSource(...)` | Zero-code Madara source | Madara WordPress sites |
| `@ThemeSource(...)` | Zero-code theme source | Other theme-based sites |
| `@SourceConfig(...)` | Define all config in one place | Advanced use |

## Auto-Generation Annotations

| Annotation | Purpose | Generated Code |
|------------|---------|----------------|
| `@GenerateFilters(title=true, sort=true, sortOptions=[...])` | Auto-generate filters | `getFilters()` implementation |
| `@GenerateCommands(detailFetch=true, contentFetch=true, chapterFetch=true)` | Auto-generate commands | `getCommands()` implementation |

## Declarative Selector Annotations

| Annotation | Purpose | Replaces |
|------------|---------|----------|
| `@ExploreFetcher(name, endpoint, selector, ...)` | Define listing endpoints | `exploreFetchers` override |
| `@DetailSelectors(title, cover, author, description, ...)` | Novel detail selectors | `detailFetcher` override |
| `@ChapterSelectors(list, name, link, date, reversed)` | Chapter list selectors | `chapterFetcher` override |
| `@ContentSelectors(content, title, removeSelectors)` | Chapter content selectors | `contentFetcher` override |

## HTTP Configuration Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@RateLimit(permits=2, periodMs=1000)` | Limit request rate | 2 requests/second |
| `@CustomHeader(name, value)` | Add HTTP headers | `@CustomHeader(name="Referer", value="...")` |
| `@CloudflareConfig(enabled=true)` | Handle Cloudflare | Sites with CF protection |
| `@Pagination(startPage=1, itemsPerPage=20)` | Configure pagination | Custom pagination |

## Metadata Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@SourceMeta(description, nsfw, tags)` | Add source metadata | `@SourceMeta(nsfw=true)` |
| `@SourceDeepLink(host, pathPattern)` | Handle browser URLs | Open novels from browser |
| `@RequiresAuth(type, loginUrl)` | Mark auth requirements | Sites needing login |
| `@ApiEndpoint(name, path, method)` | Define API endpoints | JSON API sources |

## Test Annotations (REQUIRED)

| Annotation | Purpose |
|------------|---------|
| `@GenerateTests(unitTests, integrationTests, searchQuery, minSearchResults)` | Auto-generate test cases |
| `@TestFixture(novelUrl, chapterUrl, expectedAuthor, expectedTitle)` | Test URLs and expectations |
| `@TestExpectations()` | Enable test expectation checks |
| `@SkipTests(search, chapters, content, reason)` | Skip specific tests |

## Verified Imports

```kotlin
// REQUIRED - Always include these for ANY SourceFactory source
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import ireader.core.source.model.Listing
import ireader.core.source.model.MangasPageInfo
import tachiyomix.annotations.Extension

// HTTP - Only if overriding requests
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters

// PARSING - Only if custom parsing needed
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

// JSON - Use dynamic parsing, NOT @Serializable
import kotlinx.serialization.json.*

// KSP ANNOTATIONS
import tachiyomix.annotations.Extension
import tachiyomix.annotations.MadaraSource
import tachiyomix.annotations.ThemeSource
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.SourceMeta
import tachiyomix.annotations.GenerateFilters
import tachiyomix.annotations.GenerateCommands
import tachiyomix.annotations.ExploreFetcher
import tachiyomix.annotations.DetailSelectors
import tachiyomix.annotations.ChapterSelectors
import tachiyomix.annotations.ContentSelectors
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.SkipTests
```

## DO NOT USE

```kotlin
// WRONG - These do NOT exist in this project:
import ireader.common.utils.DateParser          // NOT AVAILABLE
import ireader.common.utils.ContentCleaner      // NOT AVAILABLE
import ireader.core.source.helpers.*            // NOT AVAILABLE
```
