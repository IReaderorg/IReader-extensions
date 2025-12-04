# 📚 KSP Annotations Reference

Complete reference for all KSP annotations in IReader extensions.

---

## Quick Navigation

| Category | Annotations |
|----------|-------------|
| **Core** | [@Extension](#extension), [@AutoSourceId](#autosourceid) |
| **Theme Sources** | [@MadaraSource](#madarasource), [@ThemeSource](#themesource) |
| **Selectors** | [@ExploreFetcher](#explorefetcher), [@DetailSelectors](#detailselectors), [@ChapterSelectors](#chapterselectors), [@ContentSelectors](#contentselectors) |
| **Filters** | [@SourceFilters](#sourcefilters), [@GenerateFilters](#generatefilters) |
| **Network** | [@RateLimit](#ratelimit), [@CustomHeader](#customheader), [@CloudflareConfig](#cloudflareconfig) |
| **Deep Links** | [@SourceDeepLink](#sourcedeeplink) |
| **Testing** | [@GenerateTests](#generatetests), [@TestFixture](#testfixture) |
| **Metadata** | [@SourceMeta](#sourcemeta) |

---

## Core Annotations

### @Extension

**Required for every source.** Marks a class as an IReader extension.

```kotlin
@Extension
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    override val name = "My Source"
    override val lang = "en"
    override val baseUrl = "https://example.com"
    override val id: Long = 12345L
}
```

**Requirements:**
- Class must be `open` or `abstract`
- Must extend `SourceFactory` or implement `Source`
- Constructor must accept `Dependencies`

---

### @AutoSourceId

**Auto-generates a stable source ID.** No more manual ID management!

```kotlin
@Extension
@AutoSourceId
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // ID is auto-generated from name + lang
}
```

**Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `seed` | String | "" | Custom seed for ID generation |
| `version` | Int | 1 | Version number for ID |

**Migration from manual IDs:**
```kotlin
@AutoSourceId(seed = "OldSourceName")  // Keeps the old ID
```

---

## Theme Source Annotations

### @MadaraSource

**Create a Madara-based source with zero code!**

```kotlin
@MadaraSource(
    name = "My Novel Site",
    baseUrl = "https://mysite.com",
    lang = "en",
    id = 12345
)
object MySiteConfig  // That's it!
```

**Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | String | required | Display name |
| `baseUrl` | String | required | Site URL |
| `lang` | String | required | Language code |
| `id` | Long | required | Source ID |
| `novelsPath` | String | "novel" | URL path for listings |
| `novelPath` | String | "novel" | URL path for novel pages |
| `chapterPath` | String | "novel" | URL path for chapters |

---

### @ThemeSource

**Create a source from any theme/template.**

```kotlin
@ThemeSource(
    name = "My Site",
    baseUrl = "https://mysite.com",
    lang = "en",
    id = 12345,
    theme = "ireader.themes.BoxNovel"
)
object MySiteConfig
```

---

## Selector Annotations

### @ExploreFetcher

**Define listing/search endpoints declaratively.**

```kotlin
@Extension
@ExploreFetcher(
    name = "Latest",
    endpoint = "/novels/page/{page}/",
    selector = ".novel-item",
    nameSelector = ".title",
    linkSelector = "a",
    coverSelector = "img"
)
@ExploreFetcher(
    name = "Search",
    endpoint = "/search?q={query}&page={page}",
    selector = ".search-result",
    isSearch = true
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps)
```

**Placeholders:**
- `{page}` - Page number
- `{query}` - Search query (URL encoded)

---

### @DetailSelectors

**Define novel detail page selectors.**

```kotlin
@DetailSelectors(
    title = "h1.novel-title",
    cover = ".cover img",
    author = ".author-name",
    description = ".summary p",
    genres = ".genre-list a",
    status = ".status"
)
```

---

### @ChapterSelectors

**Define chapter list selectors.**

```kotlin
@ChapterSelectors(
    list = ".chapter-list li",
    name = ".chapter-title",
    link = "a",
    date = ".date",
    reversed = true  // Newest first
)
```

---

### @ContentSelectors

**Define chapter content selectors.**

```kotlin
@ContentSelectors(
    content = ".chapter-content p",
    title = ".chapter-title",
    removeSelectors = [".ads", "script", ".author-note"]
)
```

---

## Filter Annotations

### @SourceFilters

**Generate filter UI automatically.**

```kotlin
@SourceFilters(
    hasTitle = true,
    hasAuthor = false,
    hasSort = true,
    sortOptions = ["Latest", "Popular", "Rating"]
)
```

**Usage:**
```kotlin
override fun getFilters() = MySourceFilters.getGeneratedFilters()
```

---

### @GenerateFilters

**Alternative filter generation (from SourceAnnotations).**

```kotlin
@GenerateFilters(
    title = true,
    sort = true,
    sortOptions = ["Latest", "Popular", "New"]
)
```

---

## Network Annotations

### @RateLimit

**Prevent getting blocked by limiting request rate.**

```kotlin
@RateLimit(
    permits = 2,      // 2 requests
    periodMs = 1000   // per second
)
```

**Recommended values:**
- Normal sites: 2-3 req/sec
- Slow sites: 1 req/sec
- Fast APIs: 5-10 req/sec

---

### @CustomHeader

**Add headers to all requests.**

```kotlin
@CustomHeader(name = "Referer", value = "https://example.com/")
@CustomHeader(name = "X-Requested-With", value = "XMLHttpRequest")
```

---

### @CloudflareConfig

**Handle Cloudflare protection.**

```kotlin
@CloudflareConfig(
    enabled = true,
    timeoutMs = 30000  // 30 seconds for WebView
)
```

---

## Deep Link Annotations

### @SourceDeepLink

**Handle URLs opened from browser.**

```kotlin
@SourceDeepLink(
    host = "www.example.com",
    pathPattern = "/novel/.*"
)
@SourceDeepLink(
    host = "example.com",  // Also without www
    pathPattern = "/novel/.*"
)
```

**Generated code includes:**
- URL pattern matchers
- `canHandle(url)` function
- AndroidManifest.xml snippet

---

## Testing Annotations

### @GenerateTests

**Auto-generate test cases.**

```kotlin
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "dragon"
)
```

**Enable in build.gradle.kts:**
```kotlin
ksp {
    arg("generateTests", "true")
}
```

---

### @TestFixture

**Provide known-good test data.**

```kotlin
@TestFixture(
    novelUrl = "https://example.com/novel/my-novel/",
    chapterUrl = "https://example.com/novel/my-novel/chapter-1/",
    expectedTitle = "My Novel",
    expectedAuthor = "Author Name"
)
```

---

## Metadata Annotations

### @SourceMeta

**Add metadata for repository index.**

```kotlin
@SourceMeta(
    description = "Popular light novel site",
    nsfw = false,
    tags = ["light-novel", "translations"]
)
```

---

## Annotation Combinations

### Minimal Source
```kotlin
@Extension
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // Manual implementation
}
```

### Auto-ID Source
```kotlin
@Extension
@AutoSourceId
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // ID auto-generated
}
```

### Fully Declarative Source
```kotlin
@Extension
@AutoSourceId
@SourceMeta(description = "My source")
@RateLimit(permits = 2, periodMs = 1000)
@ExploreFetcher(name = "Latest", endpoint = "/latest/{page}", selector = ".item")
@DetailSelectors(title = "h1", cover = "img")
@ChapterSelectors(list = ".chapters li", name = "a")
@ContentSelectors(content = ".content p")
@GenerateTests(unitTests = true)
abstract class MySource(deps: Dependencies) : SourceFactory(deps)
```

### Zero-Code Madara Source
```kotlin
@MadaraSource(
    name = "My Site",
    baseUrl = "https://mysite.com",
    lang = "en",
    id = 12345
)
object MySiteConfig
```

---

## Generated Files

| Annotation | Generated File |
|------------|----------------|
| `@Extension` | `Extension.kt` (in tachiyomix.extension) |
| `@AutoSourceId` | `{ClassName}SourceId.kt` |
| `@MadaraSource` | `{Name}Generated.kt` |
| `@ThemeSource` | `{Name}Generated.kt` |
| `@ExploreFetcher` | `{ClassName}Fetchers.kt` |
| `@SourceFilters` | `{ClassName}Filters.kt` |
| `@SourceDeepLink` | `{ClassName}DeepLinks.kt` |
| `@ApiEndpoint` | `{ClassName}Api.kt` |
| `@GenerateTests` | `{ClassName}UnitTest.kt` |

Generated files are in: `build/generated/ksp/{variant}/kotlin/`
