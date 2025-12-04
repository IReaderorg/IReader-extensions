# KSP Annotations Guide

This document describes the KSP (Kotlin Symbol Processing) annotations available for creating IReader extension sources.

## Overview

KSP annotations allow you to define sources declaratively, reducing boilerplate code and catching errors at compile time.

## Available Annotations

### Core Annotations

#### `@Extension`
Marks a class as an IReader extension source. Required for all sources.

```kotlin
@Extension
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // ...
}
```

#### `@SourceMeta`
Provides metadata for the source (used for repository index generation).

```kotlin
@SourceMeta(
    description = "A great novel source",
    nsfw = false,
    icon = "https://example.com/icon.png",
    tags = ["english", "novels"]
)
```

### Theme-Based Source Annotations

#### `@MadaraSource`
Generates a complete Madara theme source from configuration.

```kotlin
@MadaraSource(
    name = "MyMadaraSite",
    baseUrl = "https://my-madara-site.com",
    lang = "en",
    id = 12345,
    novelsPath = "novel",    // optional, default: "novel"
    novelPath = "novel",     // optional, default: "novel"
    chapterPath = "novel"    // optional, default: "novel"
)
object MyMadaraSiteConfig
```

#### `@ThemeSource`
Generic annotation for any theme-based source.

```kotlin
@ThemeSource(
    name = "MySite",
    baseUrl = "https://mysite.com",
    lang = "en",
    id = 12345,
    theme = "ireader.mytheme.MyTheme"
)
object MySiteConfig
```

### Filter Annotations

#### `@SourceFilters`
Auto-generates the `getFilters()` implementation.

```kotlin
@SourceFilters(
    hasTitle = true,
    hasAuthor = true,
    hasGenre = false,
    hasStatus = false,
    hasSort = true,
    sortOptions = ["Latest", "Popular", "Rating"]
)
```

Generated code can be used as:
```kotlin
override fun getFilters(): FilterList {
    return MySourceFilters.getGeneratedFilters()
}
```

### Fetcher Annotations

#### `@ExploreFetcher`
Defines explore/listing endpoints. Can be used multiple times.

```kotlin
@ExploreFetcher(
    name = "Latest",
    endpoint = "/novels/latest?page={page}",
    selector = ".novel-item",
    nameSelector = ".title a",
    linkSelector = ".title a",
    coverSelector = "img",
    isSearch = false
)
@ExploreFetcher(
    name = "Search",
    endpoint = "/search?q={query}&page={page}",
    selector = ".result",
    nameSelector = "h3 a",
    linkSelector = "h3 a",
    coverSelector = "img.cover",
    isSearch = true
)
```

Generated code can be used as:
```kotlin
override val exploreFetchers: List<BaseExploreFetcher>
    get() = MySourceFetchers.generatedExploreFetchers
```

### Selector Annotations

#### `@DetailSelectors`
Defines CSS selectors for the detail/info page.

```kotlin
@DetailSelectors(
    title = "h1.novel-title",
    cover = ".cover img",
    author = ".author a",
    description = ".description p",
    genres = ".genres a",
    status = ".status"
)
```

#### `@ChapterSelectors`
Defines CSS selectors for the chapter list.

```kotlin
@ChapterSelectors(
    list = ".chapter-list li",
    name = "a",
    link = "a",
    date = ".date",
    reversed = true
)
```

#### `@ContentSelectors`
Defines CSS selectors for chapter content.

```kotlin
@ContentSelectors(
    content = ".chapter-content p",
    title = ".chapter-title",
    removeSelectors = [".ads", ".comments", ".social"]
)
```

### Date Format Annotations

#### `@DateFormat`
Defines custom date formats for parsing. Can be used multiple times.

```kotlin
@DateFormat(pattern = "MMM dd, yyyy", locale = "en_US")
@DateFormat(pattern = "yyyy-MM-dd")
```

## Compile-Time Validation

The KSP processors perform validation at compile time:

### Selector Validation
- Checks for unbalanced brackets
- Validates pseudo-selectors
- Detects common typos
- Warns about empty selectors

### Source Validation
- Ensures required properties are set
- Validates package naming conventions
- Checks interface implementations

## Generated Files

KSP generates the following files:

| Annotation | Generated File |
|------------|----------------|
| `@SourceFilters` | `{ClassName}Filters.kt` |
| `@ExploreFetcher` | `{ClassName}Fetchers.kt` |
| `@MadaraSource` | `{Name}Generated.kt` |
| `@ThemeSource` | `{Name}Generated.kt` |
| All sources | `source-index.json` |

## Example: Complete Annotated Source

```kotlin
package ireader.mysite

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import tachiyomix.annotations.*

@Extension
@SourceMeta(
    description = "My awesome novel source",
    nsfw = false,
    tags = ["english", "novels"]
)
@SourceFilters(
    hasTitle = true,
    hasSort = true,
    sortOptions = ["Latest", "Popular"]
)
@ExploreFetcher(
    name = "Latest",
    endpoint = "/latest?page={page}",
    selector = ".novel",
    nameSelector = "h3 a",
    linkSelector = "h3 a",
    coverSelector = "img"
)
@ExploreFetcher(
    name = "Search",
    endpoint = "/search?q={query}",
    selector = ".result",
    nameSelector = "a",
    linkSelector = "a",
    coverSelector = "img",
    isSearch = true
)
@DetailSelectors(
    title = "h1",
    cover = ".cover img",
    author = ".author",
    description = ".desc"
)
@ChapterSelectors(
    list = ".chapters li",
    name = "a",
    link = "a"
)
@ContentSelectors(
    content = ".content p"
)
abstract class MySite(deps: Dependencies) : SourceFactory(deps) {
    override val lang = "en"
    override val baseUrl = "https://mysite.com"
    override val id = 12345L
    override val name = "MySite"

    override fun getFilters() = MySiteFilters.getGeneratedFilters()
    override val exploreFetchers get() = MySiteFetchers.generatedExploreFetchers
}
```

## Migration from Manual Implementation

To migrate an existing source to use annotations:

1. Add `@SourceMeta` with description and tags
2. Replace manual filter list with `@SourceFilters`
3. Replace `exploreFetchers` list with `@ExploreFetcher` annotations
4. Add selector annotations for documentation/validation
5. Update `getFilters()` and `exploreFetchers` to use generated code

## API & Network Annotations

### `@ApiEndpoint`
Define API endpoints for code generation.

```kotlin
@ApiEndpoint(
    name = "GetNovel",
    path = "/api/novel/{id}",
    method = "GET",
    params = ["id"]
)
@ApiEndpoint(
    name = "Search",
    path = "/api/search?q={query}&page={page}",
    method = "GET",
    params = ["query", "page"]
)
```

### `@SourceDeepLink`
Define deep link handling for the source.

```kotlin
@SourceDeepLink(
    host = "www.example.com",
    scheme = "https",
    pathPattern = "/novel/.*",
    type = "MANGA"
)
@SourceDeepLink(
    host = "www.example.com",
    pathPattern = "/chapter/.*",
    type = "CHAPTER"
)
```

### `@RateLimit`
Configure rate limiting.

```kotlin
@RateLimit(
    permits = 2,
    periodMs = 1000,
    applyToAll = true
)
```

### `@CustomHeader`
Add custom headers to requests.

```kotlin
@CustomHeader(name = "X-Custom-Header", value = "custom-value")
@CustomHeader(name = "Referer", value = "https://example.com")
```

### `@CloudflareConfig`
Configure Cloudflare bypass.

```kotlin
@CloudflareConfig(
    enabled = true,
    userAgent = "Custom User Agent",
    timeoutMs = 30000
)
```

### `@RequiresAuth`
Mark source as requiring authentication.

```kotlin
@RequiresAuth(
    type = "COOKIE",
    loginUrl = "https://example.com/login",
    required = false
)
```

### `@Pagination`
Configure pagination behavior.

```kotlin
@Pagination(
    startPage = 1,
    maxPages = 100,
    itemsPerPage = 20,
    useOffset = false
)
```

## Test Generation

Enable test generation by adding KSP argument:

```kotlin
ksp {
    arg("generateTests", "true")
}
```

Generated tests include:
- Source property validation
- Filter validation
- Fetcher endpoint validation
- Selector syntax validation

## All KSP Processors

| Processor | Purpose |
|-----------|---------|
| `ExtensionProcessor` | Generate Extension class |
| `ThemeSourceProcessor` | Generate theme-based sources |
| `SourceIndexProcessor` | Generate repository index |
| `SourceFactoryProcessor` | Generate filters & fetchers |
| `SelectorValidatorProcessor` | Validate CSS selectors |
| `TestGeneratorProcessor` | Generate test cases |
| `HttpClientProcessor` | Generate HTTP request helpers |
| `DeepLinkProcessor` | Generate deep link handlers |

## Best Practices

1. **Use annotations for simple sources** - Complex sources may still need manual implementation
2. **Validate selectors** - The compiler will warn about potential issues
3. **Keep metadata up to date** - `@SourceMeta` is used for the repository index
4. **Use theme annotations** - For Madara/theme sites, use `@MadaraSource` or `@ThemeSource`
5. **Enable test generation** - Catch issues early with auto-generated tests
6. **Configure rate limiting** - Prevent getting blocked by sources
7. **Add deep links** - Improve user experience with URL handling


## Test Generation

### Enabling Test Generation

Add KSP arguments to your build:

```kotlin
// In build.gradle.kts
ksp {
    arg("generateTests", "true")
    arg("generateIntegrationTests", "true")  // Optional
}
```

### Test Annotations

#### `@GenerateTests`
Configure test generation for a source.

```kotlin
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "fantasy",
    minSearchResults = 5
)
```

#### `@TestFixture`
Provide test data for a source.

```kotlin
@TestFixture(
    novelUrl = "https://example.com/novel/123",
    chapterUrl = "https://example.com/chapter/456",
    expectedTitle = "Test Novel",
    expectedAuthor = "Test Author"
)
```

#### `@SkipTests`
Skip certain tests for a source.

```kotlin
@SkipTests(
    search = true,  // Skip search tests
    reason = "Search requires login"
)
```

#### `@TestExpectations`
Define expected behavior for tests.

```kotlin
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 5,
    supportsPagination = true,
    requiresLogin = false
)
```

### Generated Test Types

#### Unit Tests (`{Source}UnitTest.kt`)
- Filter validation tests
- Fetcher endpoint tests
- Selector syntax validation
- URL placeholder tests
- Deep link handler tests

#### Integration Tests (`{Source}IntegrationTest.kt`)
- Fetch latest novels test
- Search novels test
- Fetch novel details test
- Fetch chapters test
- Fetch chapter content test

### Running Tests

```bash
# Run unit tests
./gradlew :extensions:individual:en:mysource:test

# Run integration tests (requires network)
./gradlew :extensions:individual:en:mysource:connectedTest
```

### Example: Fully Annotated Source with Tests

```kotlin
@Extension
@SourceMeta(description = "My Source")
@SourceFilters(hasTitle = true, hasSort = true, sortOptions = ["Latest", "Popular"])
@ExploreFetcher(name = "Latest", endpoint = "/latest?page={page}", selector = ".novel")
@ExploreFetcher(name = "Search", endpoint = "/search?q={query}", selector = ".result", isSearch = true)
@GenerateTests(unitTests = true, integrationTests = true)
@TestFixture(novelUrl = "https://example.com/novel/test")
@TestExpectations(minLatestNovels = 10)
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // ...
}
```

This will generate:
- `MySourceUnitTest.kt` - Unit tests for filters, fetchers, selectors
- `MySourceIntegrationTest.kt` - Network tests (disabled by default)
