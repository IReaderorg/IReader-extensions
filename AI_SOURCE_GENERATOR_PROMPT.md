# 🤖 IReader Source Generator - AI Prompt Guide

> **CRITICAL: This document contains EXACT imports, class names, and patterns from the actual codebase.**
> **DO NOT invent or guess any imports, classes, or methods not listed here.**

---

## 🚨🚨🚨 MANDATORY: USE KSP ANNOTATIONS! 🚨🚨🚨

**BEFORE WRITING ANY SOURCE CODE, YOU MUST:**

1. **CHECK IF SITE IS MADARA** → Use `@MadaraSource` (ZERO CODE NEEDED!)
2. **ALWAYS USE `@AutoSourceId`** → Never hardcode source IDs!
3. **USE DECLARATIVE ANNOTATIONS** → `@ExploreFetcher`, `@DetailSelectors`, `@ChapterSelectors`, `@ContentSelectors`
4. **ONLY WRITE MANUAL CODE** when KSP annotations absolutely cannot handle the use case!
5. **NEVER USE `@Serializable` DATA CLASSES** → Use dynamic JSON parsing instead! (See JSON section below)

**KSP annotations are located in:** `annotations/src/commonMain/kotlin/tachiyomix/annotations/`

---

## 🚨 JSON API Sources: Use Dynamic Parsing!

**CRITICAL:** Do NOT use `@Serializable` data classes for JSON parsing - they cause `IncompatibleClassChangeError` at runtime due to kotlinx.serialization version mismatch.

```kotlin
// ❌ WRONG - causes runtime crash!
@Serializable data class Novel(val title: String)
val novel: Novel = json.decodeFromString(response)

// ✅ CORRECT - use dynamic parsing
val jsonObj = json.parseToJsonElement(response).jsonObject
val title = jsonObj["title"]?.jsonPrimitive?.contentOrNull ?: ""
```

See the **"JSON Parsing"** section below for complete examples.

---

## ✅ KSP Annotations Now Work Automatically!

**ALL declarative annotations now automatically implement their methods/properties!**

The KSP processor generates overrides directly in the Extension class - **no manual override needed!**

### Auto-Generated Annotations Summary:

| Annotation | Auto-Generates | Manual Override Needed? |
|------------|----------------|------------------------|
| `@GenerateFilters` | `getFilters()` | ❌ NO |
| `@GenerateCommands` | `getCommands()` | ❌ NO |
| `@ExploreFetcher` | `exploreFetchers` | ❌ NO |
| `@DetailSelectors` | `detailFetcher` | ❌ NO |
| `@ChapterSelectors` | `chapterFetcher` | ❌ NO |
| `@ContentSelectors` | `contentFetcher` | ❌ NO |

### Complete Example - Fully Declarative Source:

```kotlin
// ✅ BEST - All annotations work automatically!
@Extension
@AutoSourceId
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular"])
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
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
    endpoint = "/search?q={query}",
    selector = ".novel-item",
    nameSelector = ".title",
    linkSelector = "a",
    coverSelector = "img",
    isSearch = true
)
@DetailSelectors(
    title = "h1.novel-title",
    cover = ".cover img",
    author = ".author",
    description = ".synopsis",
    genres = ".genres a",
    status = ".status"
)
@ChapterSelectors(
    list = ".chapter-list li",
    name = "a",
    link = "a",
    reversed = true
)
@ContentSelectors(
    content = ".chapter-content p",
    title = ".chapter-title"
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {
    // ✅ ALL of these are AUTOMATICALLY generated:
    // - getFilters()
    // - getCommands()
    // - exploreFetchers
    // - detailFetcher
    // - chapterFetcher
    // - contentFetcher
    
    // Only need to define basic properties:
    override val lang = "en"
    override val baseUrl = "https://example.com"
    override val name = "My Source"
}
```

### Manual Overrides Still Work:

```kotlin
// ✅ ALSO CORRECT - Manual overrides for custom logic:
@Extension
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {
    override fun getFilters(): FilterList = listOf(Filter.Title())
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )
    // ... manual fetcher overrides
}
```

**RECOMMENDATION:** Use declarative annotations for standard sources - zero boilerplate!

---

## ⚠️ STRICT RULES FOR AI

1. **ALWAYS use KSP annotations when possible** - They reduce boilerplate and errors!
2. **ONLY use imports listed in this document** - No guessing!
3. **ONLY use classes and methods documented here** - They are verified to exist
4. **Follow the EXACT file structure** - Package names must match directory paths
5. **Copy templates exactly** - Then modify selectors only
6. **When in doubt, use the simpler approach** - @MadaraSource > @ThemeSource > SourceFactory with annotations > Manual code

---

## 🔴 CRITICAL: Method Signatures Must Be EXACT!

### Content Fetching - Use `getPageList` with `List<Page>` return type!
```kotlin
// ❌ WRONG - getContents doesn't exist!
override suspend fun getContents(chapter: ChapterInfo, commands: List<Command<*>>): List<String> {
    // This will cause compilation errors!
}

// ✅ CORRECT - Use getPageList with List<Page> return type
override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
    val doc = getContentRequest(chapter, commands)
    return parseContent(doc)  // Use private helper method
}

private fun parseContent(document: Document): List<Page> {
    return document.select(".content p")
        .map { it.text().trim() }
        .filter { it.isNotBlank() }
        .toPage()  // Use .toPage() extension!
}
```

### pageContentParse - Returns `List<Page>`, NOT `List<String>`!
```kotlin
// ❌ WRONG - Wrong return type!
override fun pageContentParse(document: Document): List<String> {
    // ERROR: Return type is not a subtype of List<Page>
}

// ✅ CORRECT - Return List<Page>
override fun pageContentParse(document: Document): List<Page> {
    return document.select(".content p")
        .map { it.text().trim() }
        .filter { it.isNotBlank() }
        .toPage()
}

// ✅ BETTER - Use private helper to avoid signature issues
private fun parseContent(document: Document): List<Page> {
    return document.select(".content p")
        .map { it.text().trim() }
        .filter { it.isNotBlank() }
        .toPage()
}
```

### Converting List<String> to List<Page>
```kotlin
// ✅ Use the .toPage() extension function
val content: List<String> = listOf("paragraph 1", "paragraph 2")
val pages: List<Page> = content.toPage()  // Converts strings to Page objects
```

---

## ❌ WRONG vs ✅ CORRECT Examples

### Source ID - Use @AutoSourceId when possible
```kotlin
// ❌ WRONG - Hardcoded ID for new sources
override val id: Long get() = 93

// ✅ CORRECT - Use @AutoSourceId for new sources
@Extension
@AutoSourceId
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {
    override val id: Long get() = MySourceSourceId.ID  // Generated!
}

// ✅ ALSO OK - Hardcoded ID for existing sources (to maintain compatibility)
override val id: Long get() = 93  // Keep existing ID
```

### Filters & Commands - Use annotations (RECOMMENDED)
```kotlin
// ✅ BEST - Use annotations for automatic generation
@Extension
@GenerateFilters(title = true)
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {
    // getFilters() and getCommands() are AUTOMATICALLY generated!
}

// ✅ ALSO OK - Manual overrides for custom logic
@Extension
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {
    
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )
}
```

### Complete CORRECT template:
```kotlin
// ✅✅✅ CORRECT - Standard SourceFactory source with KSP annotations ✅✅✅
@Extension
@GenerateFilters(title = true)
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {
    override val id: Long get() = MySourceSourceId.ID
    // Filters and commands are auto-generated by KSP!
    // No manual override needed!
}
```

---

## 📋 Table of Contents

1. [Decision Tree](#-decision-tree)
2. [KSP Annotations Reference](#-ksp-annotations-reference)
3. [Verified Imports](#-verified-imports)
4. [Source Type 1: MadaraSource (Zero Code)](#-source-type-1-madarasource-zero-code)
5. [Source Type 2: SourceFactory (Declarative)](#-source-type-2-sourcefactory-declarative-with-ksp)
6. [Available Classes & Methods](#-available-classes--methods)
7. [File Structure](#-file-structure)
8. [build.gradle.kts Template](#-buildgradlekts-template)
9. [Step-by-Step Process](#-step-by-step-process)
10. [Common Patterns](#-common-patterns)

---

## 🏷️ KSP Annotations Reference

**ALWAYS prefer KSP annotations over manual code!** They reduce errors and boilerplate.

### Source Identity Annotations

| Annotation | Purpose | When to Use |
|------------|---------|-------------|
| `@Extension` | **REQUIRED** - Marks class as IReader source | Every source |
| `@AutoSourceId` | Auto-generate stable source ID | All new sources |
| `@AutoSourceId(seed = "OldName")` | Keep ID when renaming | Migrating sources |
| `@MadaraSource(...)` | Zero-code Madara source | Madara WordPress sites |
| `@ThemeSource(...)` | Zero-code theme source | Other theme-based sites |
| `@SourceConfig(...)` | Define all config in one place | Advanced use |

### Auto-Generation Annotations

| Annotation | Purpose | Generated Code |
|------------|---------|----------------|
| `@GenerateFilters(title=true, sort=true, sortOptions=[...])` | Auto-generate filters | `getFilters()` implementation |
| `@GenerateCommands(detailFetch=true, contentFetch=true, chapterFetch=true)` | Auto-generate commands | `getCommands()` implementation |

### Declarative Selector Annotations

| Annotation | Purpose | Replaces |
|------------|---------|----------|
| `@ExploreFetcher(name, endpoint, selector, ...)` | Define listing endpoints | `exploreFetchers` override |
| `@DetailSelectors(title, cover, author, description, ...)` | Novel detail selectors | `detailFetcher` override |
| `@ChapterSelectors(list, name, link, date, reversed)` | Chapter list selectors | `chapterFetcher` override |
| `@ContentSelectors(content, title, removeSelectors)` | Chapter content selectors | `contentFetcher` override |

### HTTP Configuration Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@RateLimit(permits=2, periodMs=1000)` | Limit request rate | 2 requests/second |
| `@CustomHeader(name, value)` | Add HTTP headers | `@CustomHeader(name="Referer", value="...")` |
| `@CloudflareConfig(enabled=true)` | Handle Cloudflare | Sites with CF protection |
| `@Pagination(startPage=1, itemsPerPage=20)` | Configure pagination | Custom pagination |

### Metadata Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@SourceMeta(description, nsfw, tags)` | Add source metadata | `@SourceMeta(nsfw=true)` |
| `@SourceDeepLink(host, pathPattern)` | Handle browser URLs | Open novels from browser |
| `@RequiresAuth(type, loginUrl)` | Mark auth requirements | Sites needing login |
| `@ApiEndpoint(name, path, method)` | Define API endpoints | JSON API sources |

### Theme Customization Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@Selector(name, value)` | Override theme selector | `@Selector(name="novelTitle", value="h1.custom")` |
| `@DateFormat(pattern, locale)` | Custom date parsing | `@DateFormat(pattern="dd/MM/yyyy")` |

### Example: Fully Annotated Source

```kotlin
package ireader.mysite

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import tachiyomix.annotations.*

@Extension
@AutoSourceId
@GenerateFilters(
    title = true,
    sort = true,
    sortOptions = ["Latest", "Popular", "Rating"]
)
@GenerateCommands(
    detailFetch = true,
    contentFetch = true,
    chapterFetch = true
)
@SourceMeta(
    description = "Popular novel site",
    nsfw = false,
    tags = ["light-novel", "web-novel"]
)
@RateLimit(permits = 3, periodMs = 1000)
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
    selector = ".novel-item",
    nameSelector = ".title",
    linkSelector = "a",
    coverSelector = "img",
    isSearch = true
)
@DetailSelectors(
    title = "h1.novel-title",
    cover = ".novel-cover img",
    author = ".author-name",
    description = ".synopsis p",
    genres = ".genre-list a",
    status = ".novel-status"
)
@ChapterSelectors(
    list = ".chapter-list li",
    name = ".chapter-title",
    link = "a",
    reversed = true
)
@ContentSelectors(
    content = ".chapter-content p",
    title = ".chapter-title",
    removeSelectors = [".ads", "script", ".author-note"]
)
abstract class MySite(deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang = "en"
    override val baseUrl = "https://mysite.com"
    override val id: Long get() = MySiteSourceId.ID
    override val name = "My Site"
    
    // That's it! All fetchers, filters, commands are generated by KSP!
}
```

---

## 🎯 Decision Tree

```
Is the site a Madara/WordPress theme?
│
├── YES → Use @MadaraSource (ZERO CODE - just 1 annotation!)
│         Signs: 
│         - URL pattern: /novel/novel-name/chapter-1/
│         - Has /wp-admin/ page
│         - Similar layout to BoxNovel, NovelFull
│
└── NO → Is it based on another known theme?
         │
         ├── YES → Use @ThemeSource (minimal code)
         │
         └── NO → Use SourceFactory with KSP annotations
                  PRIORITY ORDER:
                  1. @AutoSourceId - Auto-generate ID
                  2. @GenerateFilters - Auto-generate filters
                  3. @GenerateCommands - Auto-generate commands
                  4. @ExploreFetcher - Declarative listings
                  5. @DetailSelectors - Declarative detail page
                  6. @ChapterSelectors - Declarative chapters
                  7. @ContentSelectors - Declarative content
                  8. Manual code ONLY when annotations can't handle it
```

---

## 📦 Verified Imports

### For @MadaraSource (Zero-Code):
```kotlin
import tachiyomix.annotations.MadaraSource
```

### For SourceFactory Sources:
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
```

### ✅ KSP ANNOTATIONS (USE THESE!):
```kotlin
// WORKING KSP annotations - ALL auto-generate their implementations!
import tachiyomix.annotations.Extension          // ✅ REQUIRED for all sources
import tachiyomix.annotations.MadaraSource       // ✅ Zero-code Madara sources (BEST!)
import tachiyomix.annotations.ThemeSource        // ✅ Theme-based sources
import tachiyomix.annotations.AutoSourceId       // ✅ Auto-generate source ID
import tachiyomix.annotations.SourceMeta         // ✅ Add source metadata
import tachiyomix.annotations.GenerateFilters    // ✅ Auto-generates getFilters()
import tachiyomix.annotations.GenerateCommands   // ✅ Auto-generates getCommands()
import tachiyomix.annotations.ExploreFetcher     // ✅ Auto-generates exploreFetchers
import tachiyomix.annotations.DetailSelectors    // ✅ Auto-generates detailFetcher
import tachiyomix.annotations.ChapterSelectors   // ✅ Auto-generates chapterFetcher
import tachiyomix.annotations.ContentSelectors   // ✅ Auto-generates contentFetcher
import tachiyomix.annotations.ApiEndpoint        // ✅ API endpoint definition
import tachiyomix.annotations.SourceDeepLink     // ✅ Deep link handling
import tachiyomix.annotations.RequiresAuth       // ✅ Auth requirements
import tachiyomix.annotations.Selector           // ✅ Custom selector override
import tachiyomix.annotations.DateFormat         // ✅ Custom date format
```

### ❌ DO NOT USE THESE (They don't exist):
```kotlin
// WRONG - These do NOT exist in this project:
import ireader.common.utils.DateParser          // ❌ NOT AVAILABLE
import ireader.common.utils.ContentCleaner      // ❌ NOT AVAILABLE  
import ireader.core.source.helpers.*            // ❌ NOT AVAILABLE
```

---

## 🎨 Source Type 1: MadaraSource (Zero Code)

**This is the BEST option when the site uses Madara WordPress theme.**

### Minimal Example (COPY THIS EXACTLY):
```kotlin
package ireader.sitename

import tachiyomix.annotations.MadaraSource

@MadaraSource(
    name = "SiteName",
    baseUrl = "https://example.com",
    lang = "en",
    id = 12345
)
object SiteNameConfig
```

### With Custom Paths:
```kotlin
package ireader.lunarletters

import tachiyomix.annotations.MadaraSource

@MadaraSource(
    name = "LunarLetters",
    baseUrl = "https://www.lunarletters.com",
    lang = "en",
    id = 81,
    novelsPath = "series",    // If novels at /series/ instead of /novel/
    novelPath = "series",     // Individual novel pages
    chapterPath = "series"    // Chapter pages
)
object LunarLettersConfig
```

### @MadaraSource Parameters:
| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `name` | YES | - | Display name |
| `baseUrl` | YES | - | Website URL (no trailing slash) |
| `lang` | YES | - | Language code: "en", "ar", "tu", etc. |
| `id` | YES | - | Unique source ID (number) |
| `novelsPath` | NO | "novel" | URL path for novel listings |
| `novelPath` | NO | "novel" | URL path for novel detail pages |
| `chapterPath` | NO | "novel" | URL path for chapter pages |

---

## 🏭 Source Type 2: SourceFactory (Declarative with KSP)

**Use when site is NOT Madara but has standard HTML structure.**
**ALWAYS use KSP annotations to minimize boilerplate!**

### Complete Working Template with KSP Annotations (RECOMMENDED):
```kotlin
package ireader.sitename

import ireader.core.source.Dependencies
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.GenerateFilters
import tachiyomix.annotations.GenerateCommands
import tachiyomix.annotations.SourceMeta

@Extension
@AutoSourceId                                    // ✅ Auto-generates stable ID
@GenerateFilters(title = true)                   // ✅ Auto-generates getFilters()
@GenerateCommands(                               // ✅ Auto-generates getCommands()
    detailFetch = true,
    contentFetch = true,
    chapterFetch = true
)
@SourceMeta(                                     // ✅ Optional metadata
    description = "Description of the source",
    nsfw = false
)
abstract class SiteName(deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // REQUIRED: Basic Info (ID auto-generated by @AutoSourceId)
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://example.com"
    override val id: Long get() = SiteNameSourceId.ID  // Use generated ID
    override val name: String get() = "Site Name"

    // ═══════════════════════════════════════════════════════════════
    // FILTERS & COMMANDS - Auto-generated by annotations above!
    // No need to override getFilters() or getCommands()
    // ═══════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════
    // EXPLORE FETCHERS - Novel listings and search
    // ═══════════════════════════════════════════════════════════════
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novels/page/{page}/",
                selector = ".novel-item",
                nameSelector = "h3",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                maxPage = 50
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?q={query}",
                selector = ".novel-item",
                nameSelector = "h3",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            )
        )

    // ═══════════════════════════════════════════════════════════════
    // DETAIL FETCHER - Novel details page
    // ═══════════════════════════════════════════════════════════════
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.novel-title",
            coverSelector = ".novel-cover img",
            coverAtt = "src",
            descriptionSelector = ".synopsis",
            authorBookSelector = ".author-name",
            categorySelector = ".genre-list a",
            statusSelector = ".novel-status",
            addBaseurlToCoverLink = true,
            onStatus = { status ->
                if (status.contains("Completed", ignoreCase = true)) {
                    MangaInfo.COMPLETED
                } else {
                    MangaInfo.ONGOING
                }
            }
        )

    // ═══════════════════════════════════════════════════════════════
    // CHAPTER FETCHER - Chapter list
    // ═══════════════════════════════════════════════════════════════
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = true
        )

    // ═══════════════════════════════════════════════════════════════
    // CONTENT FETCHER - Chapter content
    // ═══════════════════════════════════════════════════════════════
    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title",
            pageContentSelector = ".chapter-content p"
        )
}
```

---

## 📚 Available Classes & Methods

### MangaInfo Status Constants
```kotlin
MangaInfo.UNKNOWN    // 0L - Default
MangaInfo.ONGOING    // 1L
MangaInfo.COMPLETED  // 2L
MangaInfo.LICENSED   // 3L
MangaInfo.CANCELLED  // 5L
MangaInfo.ON_HIATUS  // 6L
```

### Filter Types
```kotlin
Filter.Title()                                    // Text search
Filter.Sort("Sort By:", arrayOf("Latest", "Popular"))  // Dropdown
```

### Command Types
```kotlin
Command.Detail.Fetch()   // Fetch novel details
Command.Content.Fetch()  // Fetch chapter content
Command.Chapter.Fetch()  // Fetch chapter list
```

### SourceFactory.Type
```kotlin
SourceFactory.Type.Search   // For search fetcher
SourceFactory.Type.Others   // Default for listings
```

---

## � Fil e Structure

### For Regular Sources (sources/{lang}/{name}/):
```
sources/
└── en/                              # Language code
    └── sitename/                    # Lowercase, no spaces
        ├── build.gradle.kts
        └── main/
            └── src/
                └── ireader/
                    └── sitename/    # Must match package name
                        └── SiteName.kt
```

### For Madara Sources (sources/multisrc/madara/):
```
sources/
└── multisrc/
    └── madara/
        ├── build.gradle.kts         # Add entry here
        └── sitename/
            └── src/
                └── ireader/
                    └── sitename/
                        └── SiteName.kt
```

### Package Name Rules:
- Package MUST be `ireader.{sourcename}` (lowercase)
- Directory MUST match: `ireader/{sourcename}/`
- Class name: PascalCase (e.g., `NovelFull`)
- Package/folder: lowercase (e.g., `novelfull`)

---

## � buirld.gradle.kts Template

### For Regular Sources:
```kotlin
listOf("en").map { lang ->
    Extension(
        name = "SiteName",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
```

### Parameters:
| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | String | Display name (must match class name) |
| `versionCode` | Int | Version number (start at 1) |
| `libVersion` | String | Library version ("1" or "2") |
| `lang` | String | Language code |
| `description` | String | Optional description |
| `nsfw` | Boolean | true if adult content |
| `icon` | - | Use `DEFAULT_ICON` |

---

## 📋 Step-by-Step Process

### Step 1: Determine Source Type (KSP-First Approach)

Open the website and check:
- Does URL look like `/novel/name/chapter-1/`? → **@MadaraSource** (zero code!)
- Does it have `/wp-admin/`? → **@MadaraSource** (zero code!)
- Is it based on another known theme? → **@ThemeSource**
- Otherwise → **SourceFactory with KSP annotations**

**ALWAYS try KSP annotations first before writing manual code!**

### Step 2: Find CSS Selectors (For SourceFactory)

Use browser DevTools (F12):

**Novel Listing Page:**
```
1. Find container for each novel card
2. Find title element inside card
3. Find link element (usually <a>)
4. Find cover image
```

**Novel Detail Page:**
```
1. Find title (usually h1)
2. Find cover image
3. Find description/synopsis
4. Find author name
5. Find genre/category links
6. Find status text
```

**Chapter List:**
```
1. Find container for each chapter
2. Find chapter name/title
3. Find chapter link
```

**Chapter Content:**
```
1. Find chapter title
2. Find content paragraphs (usually .content p)
```

### Step 3: Generate Source ID

**RECOMMENDED: Use `@AutoSourceId` annotation!**
```kotlin
@Extension
@AutoSourceId  // KSP generates stable ID automatically
abstract class MySite(deps: Dependencies) : SourceFactory(deps = deps) {
    override val id: Long get() = MySiteSourceId.ID  // Use generated constant
}
```

**Alternative: Manual generation**
Run: `./gradlew generateSourceId -PsourceName="Name"`

For manual calculation:
```
MD5(lowercase(name) + "/" + lang + "/1") → first 8 bytes as Long
```

### Step 4: Create Files

1. Create directory structure
2. Create Kotlin source file
3. Create build.gradle.kts
4. Build and test

---

## 🔧 Common Patterns

### Pattern 1: Lazy-Loaded Images (data-src)
```kotlin
BaseExploreFetcher(
    // ...
    coverSelector = "img",
    coverAtt = "data-src",  // NOT "src"
    // ...
)
```

### Pattern 2: Relative URLs
```kotlin
BaseExploreFetcher(
    // ...
    addBaseUrlToLink = true,      // Prepends baseUrl to links
    addBaseurlToCoverLink = true, // Prepends baseUrl to covers
    // ...
)
```

### Pattern 3: Status Parsing
```kotlin
override val detailFetcher: Detail
    get() = SourceFactory.Detail(
        // ...
        onStatus = { status ->
            when {
                status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                status.contains("Hiatus", ignoreCase = true) -> MangaInfo.ON_HIATUS
                else -> MangaInfo.UNKNOWN
            }
        }
    )
```

### Pattern 4: Reverse Chapter List
```kotlin
override val chapterFetcher: Chapters
    get() = SourceFactory.Chapters(
        // ...
        reverseChapterList = true,  // If newest chapters appear first on page
    )
```

### Pattern 5: Max Pages
```kotlin
BaseExploreFetcher(
    // ...
    maxPage = 50,  // Limit pagination
    // ...
)
```

### Pattern 6: Custom User Agent
```kotlin
override fun getUserAgent(): String {
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}
```

---

## ✅ Validation Checklist

Before submitting:

### KSP Annotations (PREFERRED):
- [ ] Used `@MadaraSource` if site is Madara-based (zero code!)
- [ ] Used `@AutoSourceId` instead of hardcoding ID
- [ ] Used `@GenerateFilters` instead of manual `getFilters()`
- [ ] Used `@GenerateCommands` instead of manual `getCommands()`
- [ ] Used `@ExploreFetcher` for declarative listings
- [ ] Used `@DetailSelectors`, `@ChapterSelectors`, `@ContentSelectors` where possible
- [ ] Used `@RateLimit` if site needs rate limiting
- [ ] Used `@SourceMeta` for description and NSFW flag

### Required Checks:
- [ ] Package name is `ireader.{lowercase_name}`
- [ ] Directory matches package: `ireader/{lowercase_name}/`
- [ ] Class is `abstract` and extends `SourceFactory`
- [ ] Has `@Extension` annotation
- [ ] All required overrides present (lang, baseUrl, id, name)
- [ ] Selectors are valid CSS
- [ ] `addBaseUrlToLink = true` if URLs are relative
- [ ] build.gradle.kts has correct name and lang

### Only if NOT using KSP annotations:
- [ ] `getFilters()` returns at least `Filter.Title()`
- [ ] `getCommands()` returns the three standard commands

---

## 🚫 Common Mistakes to Avoid

1. **Wrong package name:**
   ```kotlin
   // ❌ WRONG
   package ireader.novel_full
   package ireader.NovelFull
   
   // ✅ CORRECT
   package ireader.novelfull
   ```

2. **Missing abstract keyword:**
   ```kotlin
   // ❌ WRONG
   class SiteName(deps: Dependencies) : SourceFactory(deps)
   
   // ✅ CORRECT
   abstract class SiteName(deps: Dependencies) : SourceFactory(deps = deps)
   ```

3. **Wrong import:**
   ```kotlin
   // ❌ WRONG - These don't exist
   import ireader.common.utils.DateParser
   import org.jsoup.nodes.Document
   
   // ✅ CORRECT
   import com.fleeksoft.ksoup.nodes.Document
   ```

4. **Missing deps parameter:**
   ```kotlin
   // ❌ WRONG
   SourceFactory(deps)
   
   // ✅ CORRECT
   SourceFactory(deps = deps)
   ```

5. **Wrong selector attribute:**
   ```kotlin
   // ❌ WRONG - "src" when image uses data-src
   coverAtt = "src"
   
   // ✅ CORRECT - Check actual HTML
   coverAtt = "data-src"
   ```

---

## 📞 Quick Reference

### Option 1: Madara Source (BEST - Zero Code):
```kotlin
package ireader.sitename
import tachiyomix.annotations.MadaraSource

@MadaraSource(name = "Name", baseUrl = "https://...", lang = "en", id = 123)
object SiteNameConfig
// That's it! No class body needed!
```

### Option 2: SourceFactory with KSP Annotations (RECOMMENDED):
```kotlin
package ireader.sitename
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import tachiyomix.annotations.*

@Extension
@AutoSourceId
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular"])
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
@ExploreFetcher(name = "Latest", endpoint = "/novels/{page}/", selector = ".novel-item", 
    nameSelector = ".title", linkSelector = "a", coverSelector = "img")
@ExploreFetcher(name = "Search", endpoint = "/search?q={query}", selector = ".novel-item",
    nameSelector = ".title", linkSelector = "a", coverSelector = "img", isSearch = true)
@DetailSelectors(title = "h1", cover = ".cover img", author = ".author", description = ".desc")
@ChapterSelectors(list = ".chapter-list li", name = "a", link = "a", reversed = true)
@ContentSelectors(content = ".chapter-content p")
abstract class SiteName(deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang = "en"
    override val baseUrl = "https://..."
    override val id: Long get() = SiteNameSourceId.ID
    override val name = "Name"
    // Filters, commands, and fetchers are auto-generated!
}
```

### Option 3: SourceFactory Manual (Only if annotations can't handle it):
```kotlin
package ireader.sitename
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.*
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

@Extension
@AutoSourceId  // Still use this for ID!
abstract class SiteName(deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang = "en"
    override val baseUrl = "https://..."
    override val id: Long get() = SiteNameSourceId.ID
    override val name = "Name"
    
    override fun getFilters() = listOf(Filter.Title())
    override fun getCommands() = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch()
    )
    
    override val exploreFetchers = listOf(/* ... */)
    override val detailFetcher = SourceFactory.Detail(/* ... */)
    override val chapterFetcher = SourceFactory.Chapters(/* ... */)
    override val contentFetcher = SourceFactory.Content(/* ... */)
}
```

---

## 🔗 Real Working Examples

### Example 1: EpikNovel (SourceFactory)
Location: `sources/tu/epiknovel/main/src/ireader/epiknovel/EpikNovel.kt`

### Example 2: SonicMTL (MadaraSource)
Location: `sources/multisrc/madara/sonicmtl/src/ireader/sonicmtl/SonicMTL.kt`

### Example 3: LunarLetters (MadaraSource with custom paths)
Location: `sources/multisrc/madara/lunarletters/src/ireader/lunarletters/LunarLetters.kt`

---

*Last updated: December 2024*
*Based on actual codebase analysis - all imports and classes verified*


---

## 📖 Complete Parameter Reference

### BaseExploreFetcher Parameters

```kotlin
BaseExploreFetcher(
    key: String,                      // REQUIRED: Unique name ("Latest", "Search", etc.)
    endpoint: String? = null,         // URL pattern: "/novels/{page}/" or "/search?q={query}"
    selector: String? = null,         // CSS selector for each novel item
    nameSelector: String? = null,     // CSS selector for novel title
    nameAtt: String? = null,          // Attribute for title (null = text content)
    linkSelector: String? = null,     // CSS selector for link element
    linkAtt: String? = "href",        // Attribute for link (usually "href")
    coverSelector: String? = null,    // CSS selector for cover image
    coverAtt: String? = "src",        // Attribute for cover ("src" or "data-src")
    addBaseUrlToLink: Boolean = false,      // true if links are relative (/novel/...)
    addBaseurlToCoverLink: Boolean = false, // true if cover URLs are relative
    nextPageSelector: String? = null, // CSS selector for "next page" element
    nextPageAtt: String? = null,      // Attribute for next page check
    nextPageValue: String? = null,    // Expected value for next page
    infinitePage: Boolean = false,    // true = always has next page
    maxPage: Int = -1,                // Max pages (-1 = unlimited)
    type: Type = Type.Others,         // Type.Search for search fetcher
    onLink: (url: String, key: String) -> String = { url, _ -> url },
    onName: (String, key: String) -> String = { name, _ -> name },
    onCover: (String, key: String) -> String = { url, _ -> url },
    onQuery: (query: String) -> String = { query -> query },
    onPage: (page: String) -> String = { page -> page }
)
```

### SourceFactory.Detail Parameters

```kotlin
SourceFactory.Detail(
    nameSelector: String? = null,           // CSS selector for novel title
    nameAtt: String? = null,                // Attribute (null = text)
    coverSelector: String? = null,          // CSS selector for cover image
    coverAtt: String? = "src",              // Attribute ("src" or "data-src")
    descriptionSelector: String? = null,    // CSS selector for description
    descriptionBookAtt: String? = null,     // Attribute (null = text)
    authorBookSelector: String? = null,     // CSS selector for author
    authorBookAtt: String? = null,          // Attribute (null = text)
    categorySelector: String? = null,       // CSS selector for genres/categories
    categoryAtt: String? = null,            // Attribute (null = text)
    statusSelector: String? = null,         // CSS selector for status
    statusAtt: String? = null,              // Attribute (null = text)
    addBaseurlToCoverLink: Boolean = false, // true if cover URL is relative
    onName: (String) -> String = { it },
    onCover: (String) -> String = { it },
    onDescription: (List<String>) -> List<String> = { it },
    onAuthor: (String) -> String = { it },
    onCategory: (List<String>) -> List<String> = { it },
    onStatus: (String) -> Long = { MangaInfo.UNKNOWN }  // Return MangaInfo.ONGOING, etc.
)
```

### SourceFactory.Chapters Parameters

```kotlin
SourceFactory.Chapters(
    selector: String? = null,              // CSS selector for each chapter element
    nameSelector: String? = null,          // CSS selector for chapter name
    nameAtt: String? = null,               // Attribute (null = text)
    linkSelector: String? = null,          // CSS selector for chapter link
    linkAtt: String? = "href",             // Attribute (usually "href")
    uploadDateSelector: String? = null,    // CSS selector for upload date
    uploadDateAtt: String? = null,         // Attribute (null = text)
    numberSelector: String? = null,        // CSS selector for chapter number
    numberAtt: String? = null,             // Attribute (null = text)
    translatorSelector: String? = null,    // CSS selector for translator
    translatorAtt: String? = null,         // Attribute (null = text)
    addBaseUrlToLink: Boolean = false,     // true if chapter URLs are relative
    reverseChapterList: Boolean = false,   // true if newest chapters first on page
    onLink: (String) -> String = { it },
    onName: (String) -> String = { it },
    onNumber: (String) -> String = { it },
    onTranslator: (String) -> String = { it },
    uploadDateParser: (String) -> Long = { 0L }  // Parse date string to timestamp
)
```

### SourceFactory.Content Parameters

```kotlin
SourceFactory.Content(
    pageTitleSelector: String? = null,     // CSS selector for chapter title
    pageTitleAtt: String? = null,          // Attribute (null = text)
    pageContentSelector: String? = null,   // CSS selector for content paragraphs
    pageContentAtt: String? = null,        // Attribute (null = text)
    onTitle: (String) -> String = { it },
    onContent: (List<String>) -> List<String> = { it }  // Filter/clean paragraphs
)
```

---

## 🔄 URL Placeholders

In `endpoint` strings, use these placeholders:

| Placeholder | Replaced With | Example |
|-------------|---------------|---------|
| `{page}` | Page number (1, 2, 3...) | `/novels/page/{page}/` |
| `{query}` | Search query (URL encoded) | `/search?q={query}` |

**Examples:**
```kotlin
endpoint = "/novels/page/{page}/"           // → /novels/page/1/, /novels/page/2/
endpoint = "/search?q={query}&page={page}"  // → /search?q=test&page=1
endpoint = "/list/all/all-onclick-{page}.html"  // → /list/all/all-onclick-1.html
```

---

## 🎯 Selector Tips

### Common Selectors:
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

### Attribute Values:
```kotlin
// For text content (most common)
nameAtt = null  // Gets element.text()

// For links
linkAtt = "href"

// For images
coverAtt = "src"      // Regular images
coverAtt = "data-src" // Lazy-loaded images

// For other attributes
someAtt = "data-id"
someAtt = "title"
```

---

## 🛠️ Scripts Available

### Create Source Interactively:
```bash
python scripts/add-source.py
```

### Create Empty Source:
```bash
python scripts/create-empty-source.py SiteName https://example.com en
```

### Generate Source ID:
```bash
./gradlew generateSourceId -PsourceName="Site Name"
```

### Build Extension:
```bash
# Format: ./gradlew :extensions:individual:{lang}:{sourcename}:assemble{Lang}Debug
# Note: The task name includes the capitalized language code (en → En, ru → Ru, etc.)

# English sources
./gradlew :extensions:individual:en:sitename:assembleEnDebug
./gradlew :extensions:individual:en:freewebnovel:assembleEnDebug

# Russian sources
./gradlew :extensions:individual:ru:jaomix:assembleRuDebug

# Chinese sources
./gradlew :extensions:individual:zh:quanben:assembleZhDebug

# Portuguese sources
./gradlew :extensions:individual:pt:novelmania:assemblePtDebug

# Ukrainian sources
./gradlew :extensions:individual:uk:bakainua:assembleUkDebug
```

### Build All Sources:
```bash
./gradlew assembleDebug
```

### Test with Test Server:
```bash
# Build + start server
./gradlew buildAndTest

# Or manually:
./gradlew assembleDebug
./gradlew testServer
```

---

## ⚡ Quick Copy-Paste Templates

### Template A: Madara Source (BEST - Zero Code!)
```kotlin
package ireader.CHANGEME

import tachiyomix.annotations.MadaraSource

@MadaraSource(
    name = "CHANGEME",
    baseUrl = "https://CHANGEME.com",
    lang = "en",
    id = CHANGEME
)
object CHANGEMEConfig
// That's it! No class body needed!
```

### Template B: SourceFactory with KSP Annotations (RECOMMENDED)
```kotlin
package ireader.CHANGEME

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import tachiyomix.annotations.*

@Extension
@AutoSourceId
@GenerateFilters(title = true)
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
@ExploreFetcher(
    name = "Latest",
    endpoint = "/CHANGEME/{page}/",
    selector = "CHANGEME",
    nameSelector = "CHANGEME",
    linkSelector = "a",
    coverSelector = "img"
)
@ExploreFetcher(
    name = "Search",
    endpoint = "/search?q={query}",
    selector = "CHANGEME",
    nameSelector = "CHANGEME",
    linkSelector = "a",
    coverSelector = "img",
    isSearch = true
)
@DetailSelectors(
    title = "CHANGEME",
    cover = "CHANGEME img",
    author = "CHANGEME",
    description = "CHANGEME",
    genres = "CHANGEME a"
)
@ChapterSelectors(
    list = "CHANGEME",
    name = "a",
    link = "a",
    reversed = true
)
@ContentSelectors(
    content = "CHANGEME p",
    title = "CHANGEME"
)
abstract class CHANGEME(deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://CHANGEME.com"
    override val id: Long get() = CHANGEMESourceId.ID
    override val name: String get() = "CHANGEME"
    // Filters, commands, and fetchers are auto-generated by KSP!
}
```

### Template C: SourceFactory Manual (Only when annotations can't handle it)
```kotlin
package ireader.CHANGEME

import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

@Extension
@AutoSourceId  // Still use this for ID generation!
abstract class CHANGEME(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://CHANGEME.com"
    override val id: Long get() = CHANGEMESourceId.ID
    override val name: String get() = "CHANGEME"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/CHANGEME/{page}/",
                selector = "CHANGEME",
                nameSelector = "CHANGEME",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?q={query}",
                selector = "CHANGEME",
                nameSelector = "CHANGEME",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "CHANGEME",
            coverSelector = "CHANGEME img",
            coverAtt = "src",
            descriptionSelector = "CHANGEME",
            authorBookSelector = "CHANGEME",
            categorySelector = "CHANGEME a",
            addBaseurlToCoverLink = true
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "CHANGEME",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "CHANGEME",
            pageContentSelector = "CHANGEME p"
        )
}
```

### Template C: build.gradle.kts
```kotlin
listOf("LANG").map { lang ->
    Extension(
        name = "CHANGEME",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
```

---

*This document is based on actual working sources in the IReader-extensions repository.*
*All imports, classes, and patterns have been verified to exist and work.*


---

## 🎛️ Advanced Filters

### All Available Filter Types

```kotlin
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList

// Filter types available:
Filter.Title(name: String = "Title")           // Text search
Filter.Author(name: String = "Author")         // Author search
Filter.Artist(name: String = "Artist")         // Artist search
Filter.Note(name: String)                      // Display-only note
Filter.Text(name: String, value: String = "")  // Generic text input
Filter.Check(name: String, allowsExclusion: Boolean = false, value: Boolean? = null)  // Checkbox
Filter.Select(name: String, options: Array<String>, value: Int = 0)  // Dropdown
Filter.Sort(name: String, options: Array<String>, value: Selection? = null)  // Sort dropdown
Filter.Group(name: String, filters: List<Filter<*>>)  // Group of filters
Filter.Genre(name: String, allowsExclusion: Boolean = false)  // Genre checkbox
```

### Example: Genre/Category Dropdown Filter

```kotlin
override fun getFilters(): FilterList = listOf(
    Filter.Title(),
    Filter.Sort(
        "Order by:",
        arrayOf(
            "Latest",
            "Popular",
            "Rating"
        )
    ),
    Filter.Select(
        "Status",
        arrayOf(
            "All",
            "Ongoing",
            "Completed"
        )
    ),
    Filter.Select(
        "Category",
        arrayOf(
            "ALL",
            "Action",
            "Adventure",
            "Comedy",
            "Drama",
            "Fantasy",
            "Harem",
            "Historical",
            "Horror",
            "Martial Arts",
            "Mature",
            "Mystery",
            "Psychological",
            "Romance",
            "School Life",
            "Sci-Fi",
            "Seinen",
            "Slice of Life",
            "Supernatural",
            "Tragedy"
        )
    )
)
```

### Using Filters in getMangaList()

```kotlin
import ireader.core.source.findInstance

override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
    // Get filter values
    val query = filters.findInstance<Filter.Title>()?.value
    val sortFilter = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
    val statusFilter = filters.findInstance<Filter.Select>()?.value ?: 0
    
    // If there's a search query, use search endpoint
    if (!query.isNullOrBlank()) {
        return getSearch(page, query)
    }
    
    // Build URL with filters
    val sortParam = when (sortFilter) {
        0 -> "latest"
        1 -> "popular"
        2 -> "rating"
        else -> "latest"
    }
    
    val statusParam = when (statusFilter) {
        0 -> "all"
        1 -> "ongoing"
        2 -> "completed"
        else -> "all"
    }
    
    val url = "$baseUrl/novels?sort=$sortParam&status=$statusParam&page=$page"
    
    // Fetch and parse...
    return getLists(exploreFetchers.first(), page, "", filters)
}
```

### Multiple Filter.Select for Different Categories

```kotlin
// Define value mappings (display name -> URL parameter)
private val statusValues = arrayOf("all", "completed", "ongoing")
private val categoryValues = arrayOf(
    "all",
    "1",   // Action
    "2",   // Adventure
    "7",   // Fantasy
    "22",  // Romance
    // ... more category IDs
)

override fun getFilters(): FilterList = listOf(
    Filter.Title(),
    Filter.Sort("Type:", arrayOf("Recently updated", "Newest", "Top view")),
    Filter.Select("Status", arrayOf("ALL", "Completed", "Ongoing")),
    Filter.Select("Category", arrayOf(
        "ALL", "Action", "Adventure", "Fantasy", "Romance"
    ))
)

override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
    val query = filters.findInstance<Filter.Title>()?.value
    if (!query.isNullOrBlank()) {
        return getSearch(page, query)
    }
    
    // Get all Filter.Select values (there can be multiple)
    val allSelects = filters.filterIsInstance<Filter.Select>()
    val statusIndex = allSelects.getOrNull(0)?.value ?: 0
    val categoryIndex = allSelects.getOrNull(1)?.value ?: 0
    
    val status = statusValues.getOrElse(statusIndex) { "all" }
    val category = categoryValues.getOrElse(categoryIndex) { "all" }
    
    val url = "$baseUrl/novel_list?type=latest&category=$category&state=$status&page=$page"
    // ... fetch and parse
}
```

---

## ⚡ Custom Commands

### All Available Command Types

```kotlin
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList

// Standard fetch commands (for WebView fallback)
Command.Detail.Fetch()    // Fetch novel details via WebView
Command.Chapter.Fetch()   // Fetch chapter list via WebView
Command.Content.Fetch()   // Fetch chapter content via WebView

// Custom chapter commands
Command.Chapter.Note(name: String)                              // Display-only note
Command.Chapter.Text(name: String, value: String = "")          // Text input
Command.Chapter.Select(name: String, options: Array<String>, value: Int = 0)  // Dropdown
```

### Example: Custom Chapter Fetch Options

This allows users to choose how many chapters to fetch:

```kotlin
override fun getCommands(): CommandList {
    return listOf(
        Command.Chapter.Select(
            "Get Chapters",
            options = arrayOf(
                "All Chapters",
                "Last 25 Chapters",
                "Last 50 Chapters",
                "Last 100 Chapters"
            ),
            value = 0
        ),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
        Command.Detail.Fetch(),
    )
}
```

### Using Custom Commands in getChapterList()

```kotlin
import ireader.core.source.findInstance

override suspend fun getChapterList(
    manga: MangaInfo,
    commands: List<Command<*>>
): List<ChapterInfo> {
    // Check for WebView fetch first
    val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
    if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
        return chaptersParse(chapterFetch.html.asJsoup()).reversed()
    }

    // Get custom command selection
    val command = commands.findInstance<Command.Chapter.Select>()
    val fetchOption = command?.value ?: 0
    
    // Fetch all chapters
    val allChapters = fetchAllChapters(manga)
    
    // Return based on selection
    return when (fetchOption) {
        0 -> allChapters                           // All chapters
        1 -> allChapters.takeLast(25)              // Last 25
        2 -> allChapters.takeLast(50)              // Last 50
        3 -> allChapters.takeLast(100)             // Last 100
        else -> allChapters
    }
}

private suspend fun fetchAllChapters(manga: MangaInfo): List<ChapterInfo> {
    val chapters = mutableListOf<ChapterInfo>()
    var page = 1
    var hasMore = true
    
    while (hasMore) {
        val url = "${manga.key}/chapters?page=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        val pageChapters = chaptersParse(document)
        
        if (pageChapters.isEmpty()) {
            hasMore = false
        } else {
            chapters.addAll(pageChapters)
            page++
        }
    }
    
    return chapters.reversed()
}
```

### Example: Text Input Command

```kotlin
override fun getCommands(): CommandList {
    return listOf(
        Command.Chapter.Text(
            "Start from chapter",
            value = ""  // User can input chapter number
        ),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
        Command.Detail.Fetch(),
    )
}

override suspend fun getChapterList(
    manga: MangaInfo,
    commands: List<Command<*>>
): List<ChapterInfo> {
    val startChapter = commands.findInstance<Command.Chapter.Text>()?.value?.toIntOrNull() ?: 1
    
    val allChapters = fetchAllChapters(manga)
    
    // Filter chapters starting from specified number
    return allChapters.filter { chapter ->
        val chapterNum = extractChapterNumber(chapter.name)
        chapterNum >= startChapter
    }
}

private fun extractChapterNumber(name: String): Int {
    val regex = Regex("\\d+")
    return regex.find(name)?.value?.toIntOrNull() ?: 0
}
```

### Complete Example: Ranobes-style Commands

```kotlin
override fun getCommands(): CommandList {
    return listOf(
        Command.Chapter.Select(
            "Get Chapters",
            options = arrayOf(
                "None",           // Don't auto-fetch, use WebView
                "Last 25 Chapter" // Fetch last 25 via API
            ),
            value = 0
        ),
        Command.Chapter.Fetch(),   // WebView fallback
        Command.Content.Fetch(),   // WebView fallback
        Command.Detail.Fetch(),    // WebView fallback
    )
}

override suspend fun getChapterList(
    manga: MangaInfo,
    commands: List<Command<*>>
): List<ChapterInfo> {
    // Priority 1: Check for WebView HTML
    val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
    if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
        return chaptersParse(chapterFetch.html.asJsoup()).reversed()
    }

    // Priority 2: Check custom command
    val command = commands.findInstance<Command.Chapter.Select>()
    
    if (command != null && command.value == 1) {
        // User selected "Last 25 Chapter" - fetch via API
        return fetchChaptersViaApi(manga).takeLast(25)
    }
    
    // Default: Fetch all chapters
    return fetchAllChapters(manga)
}
```

---

## 🔧 Using findInstance<T>()

The `findInstance<T>()` extension function finds the first filter/command of a specific type:

```kotlin
import ireader.core.source.findInstance

// For filters
val titleFilter = filters.findInstance<Filter.Title>()
val sortFilter = filters.findInstance<Filter.Sort>()
val selectFilter = filters.findInstance<Filter.Select>()

// For commands
val detailFetch = commands.findInstance<Command.Detail.Fetch>()
val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
val contentFetch = commands.findInstance<Command.Content.Fetch>()
val chapterSelect = commands.findInstance<Command.Chapter.Select>()
val chapterText = commands.findInstance<Command.Chapter.Text>()

// Getting values
val query = filters.findInstance<Filter.Title>()?.value           // String?
val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index // Int?
val selectIndex = filters.findInstance<Filter.Select>()?.value    // Int?
val html = commands.findInstance<Command.Chapter.Fetch>()?.html   // String
```

---

## 📝 Complete Source with Advanced Filters & Commands

```kotlin
package ireader.advancedexample

import io.ktor.client.request.get
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class AdvancedExample(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://example.com"
    override val id: Long get() = 99999L
    override val name: String get() = "Advanced Example"

    // ═══════════════════════════════════════════════════════════════
    // FILTERS - With genres and status
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf("Latest", "Popular", "Rating", "A-Z")
        ),
        Filter.Select(
            "Status",
            arrayOf("All", "Ongoing", "Completed", "Hiatus")
        ),
        Filter.Select(
            "Genre",
            arrayOf(
                "All Genres",
                "Action",
                "Adventure",
                "Comedy",
                "Drama",
                "Fantasy",
                "Harem",
                "Historical",
                "Horror",
                "Martial Arts",
                "Mystery",
                "Psychological",
                "Romance",
                "School Life",
                "Sci-Fi",
                "Slice of Life",
                "Supernatural"
            )
        )
    )

    // URL parameter mappings
    private val sortValues = arrayOf("latest", "popular", "rating", "name")
    private val statusValues = arrayOf("all", "ongoing", "completed", "hiatus")
    private val genreValues = arrayOf(
        "", "action", "adventure", "comedy", "drama", "fantasy",
        "harem", "historical", "horror", "martial-arts", "mystery",
        "psychological", "romance", "school-life", "sci-fi",
        "slice-of-life", "supernatural"
    )

    // ═══════════════════════════════════════════════════════════════
    // COMMANDS - With chapter fetch options
    // ═══════════════════════════════════════════════════════════════
    override fun getCommands(): CommandList = listOf(
        Command.Chapter.Select(
            "Fetch Chapters",
            options = arrayOf(
                "All Chapters",
                "Last 10 Chapters",
                "Last 25 Chapters",
                "Last 50 Chapters",
                "Last 100 Chapters"
            ),
            value = 0
        ),
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM getMangaList WITH FILTERS
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        // Check for search query first
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            val url = "$baseUrl/search?q=$query&page=$page"
            val document = client.get(requestBuilder(url)).asJsoup()
            return parseNovelList(document)
        }

        // Get filter values
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        
        // Get all Select filters (status and genre)
        val selectFilters = filters.filterIsInstance<Filter.Select>()
        val statusIndex = selectFilters.getOrNull(0)?.value ?: 0
        val genreIndex = selectFilters.getOrNull(1)?.value ?: 0

        // Build URL with filters
        val sort = sortValues.getOrElse(sortIndex) { "latest" }
        val status = statusValues.getOrElse(statusIndex) { "all" }
        val genre = genreValues.getOrElse(genreIndex) { "" }

        val url = buildString {
            append("$baseUrl/novels?")
            append("sort=$sort")
            append("&status=$status")
            if (genre.isNotEmpty()) append("&genre=$genre")
            append("&page=$page")
        }

        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: Document): MangasPageInfo {
        val novels = document.select(".novel-item").map { element ->
            MangaInfo(
                key = element.select("a").attr("href"),
                title = element.select(".title").text(),
                cover = element.select("img").attr("src")
            )
        }
        val hasNext = document.select(".pagination .next").isNotEmpty()
        return MangasPageInfo(novels, hasNext)
    }

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM getChapterList WITH COMMANDS
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        // Check for WebView HTML first
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return chaptersParse(chapterFetch.html.asJsoup()).reversed()
        }

        // Fetch all chapters
        val allChapters = fetchAllChapters(manga)

        // Apply chapter limit based on command
        val chapterCommand = commands.findInstance<Command.Chapter.Select>()
        val limitOption = chapterCommand?.value ?: 0

        return when (limitOption) {
            0 -> allChapters                    // All
            1 -> allChapters.takeLast(10)       // Last 10
            2 -> allChapters.takeLast(25)       // Last 25
            3 -> allChapters.takeLast(50)       // Last 50
            4 -> allChapters.takeLast(100)      // Last 100
            else -> allChapters
        }
    }

    private suspend fun fetchAllChapters(manga: MangaInfo): List<ChapterInfo> {
        val document = client.get(requestBuilder(manga.key)).asJsoup()
        return chaptersParse(document).reversed()
    }

    // ═══════════════════════════════════════════════════════════════
    // DECLARATIVE FETCHERS (for default behavior)
    // ═══════════════════════════════════════════════════════════════
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novels?sort=latest&page={page}",
                selector = ".novel-item",
                nameSelector = ".title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?q={query}&page={page}",
                selector = ".novel-item",
                nameSelector = ".title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.title",
            coverSelector = ".cover img",
            coverAtt = "src",
            descriptionSelector = ".synopsis",
            authorBookSelector = ".author",
            categorySelector = ".genres a",
            statusSelector = ".status",
            addBaseurlToCoverLink = true,
            onStatus = { status ->
                when {
                    status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    status.contains("Hiatus", ignoreCase = true) -> MangaInfo.ON_HIATUS
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title",
            pageContentSelector = ".chapter-content p"
        )
}
```

---

## 📋 Filter & Command Quick Reference

### Filter Types Summary

| Type | Usage | Get Value |
|------|-------|-----------|
| `Filter.Title()` | Text search | `.value` → `String` |
| `Filter.Author()` | Author search | `.value` → `String` |
| `Filter.Sort(name, options)` | Sort dropdown | `.value?.index` → `Int?` |
| `Filter.Select(name, options)` | Generic dropdown | `.value` → `Int` |
| `Filter.Check(name)` | Checkbox | `.value` → `Boolean?` |
| `Filter.Group(name, filters)` | Group of filters | `.filters` → `List<Filter<*>>` |

### Command Types Summary

| Type | Usage | Get Value |
|------|-------|-----------|
| `Command.Detail.Fetch()` | WebView detail fetch | `.html` → `String` |
| `Command.Chapter.Fetch()` | WebView chapter fetch | `.html` → `String` |
| `Command.Content.Fetch()` | WebView content fetch | `.html` → `String` |
| `Command.Chapter.Select(name, options)` | Chapter options dropdown | `.value` → `Int` |
| `Command.Chapter.Text(name)` | Chapter text input | `.value` → `String` |
| `Command.Chapter.Note(name)` | Display-only note | N/A |

---

*This section covers advanced filter and command usage based on real working sources in the IReader-extensions repository.*


---

## 🚨 CRITICAL: Common AI Mistakes to AVOID

### ❌ Mistake 1: Using `class` instead of `abstract class`

```kotlin
// ❌ WRONG - Will cause compilation error
@Extension
class MySource(deps: Dependencies) : SourceFactory(deps = deps) {

// ✅ CORRECT - SourceFactory sources MUST be abstract
@Extension
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {
```

**Why:** The KSP processor generates a concrete implementation class that extends your abstract class.

---

### ❌ Mistake 2: Using `chaptersRequest` in SourceFactory

```kotlin
// ❌ WRONG - chaptersRequest is from ParsedHttpSource, NOT SourceFactory
override fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
    return HttpRequestBuilder().apply {
        url(book.key)
    }
}

// ✅ CORRECT - Use getChapterListRequest for SourceFactory
override suspend fun getChapterListRequest(
    manga: MangaInfo,
    commands: List<Command<*>>
): Document {
    return client.get(requestBuilder(manga.key)).asJsoup()
}
```

**Why:** `SourceFactory` and `ParsedHttpSource` have different APIs. Don't mix them.

---

### ❌ Mistake 3: Using Java imports (NOT available in Kotlin Multiplatform)

```kotlin
// ❌ WRONG - These Java classes don't exist in KMP
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())

// ✅ CORRECT - Use Ktor's URL encoding or manual encoding
import io.ktor.http.encodeURLParameter

val encoded = query.encodeURLParameter()

// OR simple manual encoding for basic cases
val encoded = query.replace(" ", "%20")
```

**Why:** IReader uses Kotlin Multiplatform. Java-specific classes are not available.

---

### ❌ Mistake 4: Mixing ParsedHttpSource and SourceFactory methods

**ParsedHttpSource methods (DO NOT use in SourceFactory):**
```kotlin
// ❌ These are ParsedHttpSource methods - NOT available in SourceFactory
fun chaptersRequest(book: MangaInfo): HttpRequestBuilder
fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder
fun detailRequest(book: MangaInfo): HttpRequestBuilder
fun chaptersSelector(): String
fun chapterFromElement(element: Element): ChapterInfo
```

**SourceFactory methods (USE these instead):**
```kotlin
// ✅ These are SourceFactory methods
suspend fun getChapterListRequest(manga: MangaInfo, commands: List<Command<*>>): Document
suspend fun getMangaDetailsRequest(manga: MangaInfo, commands: List<Command<*>>): Document
suspend fun getContentRequest(chapter: ChapterInfo, commands: List<Command<*>>): Document
val chapterFetcher: Chapters  // Use declarative fetcher instead
```

---

### ❌ Mistake 5: Wrong import for Document/Element

```kotlin
// ❌ WRONG - org.jsoup is NOT available
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// ✅ CORRECT - Use fleeksoft.ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
```

---

## ✅ Correct Method Overrides for SourceFactory

### Custom Chapter List Request

```kotlin
import io.ktor.client.request.get
import ireader.core.source.asJsoup
import com.fleeksoft.ksoup.nodes.Document

// Override to customize chapter list URL
override suspend fun getChapterListRequest(
    manga: MangaInfo,
    commands: List<Command<*>>
): Document {
    // Example: Different URL pattern for chapters
    val chapterUrl = manga.key.replace("/novel/", "/chapters/")
    return client.get(requestBuilder(chapterUrl)).asJsoup()
}
```

### Custom Detail Request

```kotlin
// Override to customize detail page URL
override suspend fun getMangaDetailsRequest(
    manga: MangaInfo,
    commands: List<Command<*>>
): Document {
    return client.get(requestBuilder(manga.key)).asJsoup()
}
```

### Custom Content Request

```kotlin
// Override to customize chapter content URL
override suspend fun getContentRequest(
    chapter: ChapterInfo,
    commands: List<Command<*>>
): Document {
    return client.get(requestBuilder(chapter.key)).asJsoup()
}
```

### Full Custom Chapter List (with pagination)

```kotlin
override suspend fun getChapterList(
    manga: MangaInfo,
    commands: List<Command<*>>
): List<ChapterInfo> {
    // Check for WebView HTML first
    val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
    if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
        return chaptersParse(chapterFetch.html.asJsoup()).reversed()
    }

    // Custom chapter fetching logic
    val chapters = mutableListOf<ChapterInfo>()
    var page = 1
    
    while (true) {
        val url = "${manga.key}/chapters?page=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        val pageChapters = chaptersParse(document)
        
        if (pageChapters.isEmpty()) break
        
        chapters.addAll(pageChapters)
        page++
    }
    
    return chapters.reversed()
}
```

---

## 📋 SourceFactory vs ParsedHttpSource Comparison

| Feature | SourceFactory | ParsedHttpSource |
|---------|---------------|------------------|
| Class type | `abstract class` | `abstract class` |
| Chapter request | `getChapterListRequest()` | `chaptersRequest()` |
| Detail request | `getMangaDetailsRequest()` | `detailRequest()` |
| Content request | `getContentRequest()` | `contentRequest()` |
| Chapter parsing | `chapterFetcher` (declarative) | `chaptersSelector()` + `chapterFromElement()` |
| Recommended | ✅ YES - Use this | ⚠️ Legacy - Avoid |

---

## 🔒 Safe Imports List (Verified to Work)

```kotlin
// ALWAYS SAFE - Core imports
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

// SAFE - Model imports
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Listing

// SAFE - HTTP imports
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders

// SAFE - Parsing imports
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

// SAFE - JSON Parsing (USE DYNAMIC PARSING, NOT @Serializable!)
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject

// ⚠️ WARNING: Do NOT use @Serializable data classes - causes runtime errors!
// import kotlinx.serialization.Serializable  // ❌ AVOID - causes IncompatibleClassChangeError

// SAFE - Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ireader.core.util.DefaultDispatcher
```

---

## 🚨 CRITICAL: JSON Parsing - Use Dynamic Parsing, NOT @Serializable!

**NEVER use `@Serializable` data classes for JSON parsing!** This causes `IncompatibleClassChangeError` in the test server due to kotlinx.serialization version mismatch between the extension and the test server.

### ❌ WRONG - Will cause runtime errors:
```kotlin
// ❌ NEVER DO THIS - causes IncompatibleClassChangeError!
@Serializable
data class NovelResponse(
    val id: String,
    val title: String,
    val chapters: List<Chapter>
)

// This will FAIL at runtime with:
// "Method 'kotlinx.serialization.KSerializer[] kotlinx.serialization.internal.GeneratedSerializer.typeParametersSerializers()' must be InterfaceMethodref constant"
val response: NovelResponse = json.decodeFromString(responseText)
```

### ✅ CORRECT - Use dynamic JSON parsing:
```kotlin
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

// ✅ Parse JSON dynamically
val jsonObj = json.parseToJsonElement(responseText).jsonObject
val title = jsonObj["title"]?.jsonPrimitive?.contentOrNull ?: ""
val id = jsonObj["id"]?.jsonPrimitive?.contentOrNull ?: ""
val status = jsonObj["status"]?.jsonPrimitive?.intOrNull ?: 0
val isLocked = jsonObj["locked"]?.jsonPrimitive?.booleanOrNull == true

// ✅ Parse arrays
val chapters = jsonObj["chapters"]?.jsonArray?.mapNotNull { element ->
    val chapter = element.jsonObject
    val chapterId = chapter["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
    val chapterName = chapter["title"]?.jsonPrimitive?.contentOrNull ?: "Chapter"
    
    ChapterInfo(name = chapterName, key = chapterId)
} ?: emptyList()

// ✅ Parse nested objects
val dataObj = jsonObj["data"]?.jsonObject
val novels = dataObj?.get("novels")?.jsonArray ?: emptyList()
```

### Dynamic JSON Parsing Cheat Sheet:

| JSON Type | Kotlin Access | Null-Safe Access |
|-----------|---------------|------------------|
| String | `jsonPrimitive.content` | `jsonPrimitive.contentOrNull ?: ""` |
| Int | `jsonPrimitive.int` | `jsonPrimitive.intOrNull ?: 0` |
| Float | `jsonPrimitive.float` | `jsonPrimitive.floatOrNull ?: 0f` |
| Boolean | `jsonPrimitive.boolean` | `jsonPrimitive.booleanOrNull == true` |
| Object | `jsonObject` | `?.jsonObject` (check for null first) |
| Array | `jsonArray` | `?.jsonArray ?: emptyList()` |

### Building JSON Request Bodies:
```kotlin
val requestBody = buildJsonObject {
    put("path", "/api/endpoint")
    put("method", "GET")
    putJsonArray("headers") {
        addJsonArray {
            add("content-type")
            add("application/json")
        }
    }
}
val bodyString = requestBody.toString()
```

---

## ❌ FORBIDDEN Imports (Will NOT Work)

```kotlin
// ❌ FORBIDDEN - Java classes (not in KMP)
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ❌ FORBIDDEN - Wrong JSoup package
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// ❌ FORBIDDEN - Not available utilities
import ireader.common.utils.DateParser
import ireader.common.utils.ContentCleaner
import ireader.core.source.helpers.*

// ❌ FORBIDDEN - Causes runtime errors in test server
import kotlinx.serialization.Serializable  // Do NOT use @Serializable data classes!
```

---

## 🔄 URL Encoding Without Java

```kotlin
// Option 1: Ktor's encodeURLParameter
import io.ktor.http.encodeURLParameter

val encoded = query.encodeURLParameter()

// Option 2: Manual encoding for simple cases
fun encodeUrl(text: String): String {
    return text
        .replace(" ", "%20")
        .replace("&", "%26")
        .replace("=", "%3D")
        .replace("?", "%3F")
        .replace("#", "%23")
}

// Option 3: Use in endpoint directly (SourceFactory handles it)
BaseExploreFetcher(
    "Search",
    endpoint = "/search?q={query}",  // {query} is auto-encoded
    // ...
)
```

---

*These rules are based on actual compilation errors encountered. Following them will prevent 90% of AI-generated code issues.*


---

## 🚨 MORE CRITICAL MISTAKES TO AVOID

### ❌ Mistake 6: Using `client.get(url)` directly

```kotlin
// ❌ WRONG - Missing requestBuilder()
val document = client.get(url).asJsoup()
val document = client.get("$baseUrl/search").asJsoup()

// ✅ CORRECT - Always use requestBuilder()
val document = client.get(requestBuilder(url)).asJsoup()
val document = client.get(requestBuilder("$baseUrl/search")).asJsoup()
```

**Why:** `requestBuilder()` adds required headers (User-Agent, etc.). Without it, requests may fail or be blocked.

---

### ❌ Mistake 7: Using `class` instead of `abstract class` (REPEATED - VERY COMMON)

```kotlin
// ❌ WRONG - This is the #1 most common mistake!
@Extension
class MySource(deps: Dependencies) : SourceFactory(deps = deps) {

// ✅ CORRECT - MUST be abstract
@Extension
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {
```

**Why:** KSP generates a concrete `Extension` class that extends your source. Your source MUST be `abstract`.

**This mistake causes:** `Cannot create an instance of an abstract class` or similar errors.

---

### ❌ Mistake 8: Manual URL encoding with wrong methods

```kotlin
// ❌ WRONG - These don't exist or don't work in KMP
val encoded = URLEncoder.encode(text, "UTF-8")
val encoded = text.encodeToByteArray().toString()
val encoded = java.net.URLEncoder.encode(text, StandardCharsets.UTF_8)

// ✅ CORRECT - Simple manual encoding
val encoded = text.trim().replace(" ", "%20")

// ✅ CORRECT - Or use Ktor (if imported)
import io.ktor.http.encodeURLParameter
val encoded = text.encodeURLParameter()
```

---

### ❌ Mistake 9: Returning wrong type from getChapterList

```kotlin
// ❌ WRONG - Returning nullable or wrong type
override suspend fun getChapterList(...): List<ChapterInfo>? {  // ❌ nullable
override suspend fun getChapterList(...): MutableList<ChapterInfo> {  // ❌ mutable

// ✅ CORRECT - Return List<ChapterInfo> (non-nullable)
override suspend fun getChapterList(
    manga: MangaInfo,
    commands: List<Command<*>>
): List<ChapterInfo> {
    // ...
    return chapters  // Must be List<ChapterInfo>
}
```

---

### ❌ Mistake 10: Using `return@mapNotNull null` incorrectly

```kotlin
// ❌ PROBLEMATIC - Can cause issues
document.select("div").mapNotNull { element ->
    val link = element.select("a").first() ?: return@mapNotNull null
    // ...
}

// ✅ BETTER - Use let or explicit null handling
document.select("div").mapNotNull { element ->
    element.select("a").first()?.let { link ->
        ChapterInfo(
            name = link.text(),
            key = link.attr("href")
        )
    }
}

// ✅ ALSO GOOD - Filter after map
document.select("div").map { element ->
    val link = element.select("a").firstOrNull()
    if (link != null) {
        ChapterInfo(name = link.text(), key = link.attr("href"))
    } else {
        null
    }
}.filterNotNull()
```

---

## ✅ CORRECT SourceFactory Template (FINAL VERSION)

**Copy this EXACTLY and only change the marked parts:**

```kotlin
package ireader.SOURCENAME  // ← Change SOURCENAME

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import tachiyomix.annotations.Extension
import io.ktor.client.request.get
import ireader.core.source.asJsoup
import com.fleeksoft.ksoup.nodes.Document

@Extension
abstract class SourceName(deps: Dependencies) : SourceFactory(deps = deps) {  // ← MUST be abstract

    override val name = "Source Name"           // ← Change
    override val baseUrl = "https://example.com" // ← Change
    override val lang = "en"                     // ← Change if needed
    override val id = 12345L                     // ← Change to unique ID

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers = listOf(
        BaseExploreFetcher(
            "Latest",
            endpoint = "/novels/",              // ← Change
            selector = ".novel-item",           // ← Change
            nameSelector = ".title",            // ← Change
            linkSelector = "a",
            linkAtt = "href",
            coverSelector = "img",
            coverAtt = "src",
            addBaseUrlToLink = true,
            addBaseurlToCoverLink = true
        ),
        BaseExploreFetcher(
            "Search",
            endpoint = "/search?q={query}",     // ← Change
            selector = ".novel-item",           // ← Change
            nameSelector = ".title",            // ← Change
            linkSelector = "a",
            linkAtt = "href",
            coverSelector = "img",
            coverAtt = "src",
            addBaseUrlToLink = true,
            addBaseurlToCoverLink = true,
            type = SourceFactory.Type.Search
        )
    )

    override val detailFetcher = SourceFactory.Detail(
        nameSelector = "h1",                    // ← Change
        coverSelector = ".cover img",           // ← Change
        coverAtt = "src",
        descriptionSelector = ".description",   // ← Change
        authorBookSelector = ".author",         // ← Change (optional)
        categorySelector = ".genres a",         // ← Change (optional)
        addBaseurlToCoverLink = true
    )

    override val chapterFetcher = SourceFactory.Chapters(
        selector = ".chapter-list li",          // ← Change
        nameSelector = "a",                     // ← Change
        linkSelector = "a",
        linkAtt = "href",
        addBaseUrlToLink = true,
        reverseChapterList = true
    )

    override val contentFetcher = SourceFactory.Content(
        pageContentSelector = ".chapter-content p"  // ← Change
    )
}
```

---

## ✅ CORRECT Custom getChapterList Override

**If you need custom chapter fetching logic:**

```kotlin
import io.ktor.client.request.get
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import com.fleeksoft.ksoup.nodes.Document

override suspend fun getChapterList(
    manga: MangaInfo,
    commands: List<Command<*>>
): List<ChapterInfo> {
    // Step 1: Check for WebView HTML first (ALWAYS do this)
    val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
    if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
        return chaptersParse(chapterFetch.html.asJsoup()).reversed()
    }

    // Step 2: Custom URL building (if needed)
    val safeTitle = manga.title.trim().replace(" ", "%20")
    val url = "$baseUrl/search/label/$safeTitle?max-results=200"
    
    // Step 3: Fetch with requestBuilder (REQUIRED)
    val document = client.get(requestBuilder(url)).asJsoup()
    
    // Step 4: Parse chapters
    val chapters = document.select("div.post-outer").mapNotNull { element ->
        element.select("h3.post-title a").firstOrNull()?.let { link ->
            ChapterInfo(
                name = link.text(),
                key = link.attr("href")
            )
        }
    }
    
    // Step 5: Return (reversed if newest first on page)
    return chapters.reversed()
}
```

---

## 📋 Pre-Submission Checklist for AI

Before generating any source code, verify:

- [ ] Class is `abstract class`, NOT `class`
- [ ] Extends `SourceFactory(deps = deps)` with named parameter
- [ ] Has `@Extension` annotation
- [ ] All `client.get()` calls use `requestBuilder()`
- [ ] No Java imports (`java.net.*`, `java.nio.*`, etc.)
- [ ] Uses `com.fleeksoft.ksoup`, NOT `org.jsoup`
- [ ] Package name is lowercase: `ireader.sourcename`
- [ ] No `chaptersRequest()` method (that's ParsedHttpSource)
- [ ] Uses `getChapterListRequest()` if overriding chapter request
- [ ] Returns `List<ChapterInfo>` (not nullable, not mutable)

---

## 🔴 ABSOLUTE RULES (NEVER BREAK THESE)

### KSP Annotation Rules (HIGHEST PRIORITY):
1. **ALWAYS check if site is Madara first** → Use `@MadaraSource` (zero code!)
2. **ALWAYS use `@AutoSourceId`** instead of hardcoding IDs for new sources
3. **PREFER `@GenerateFilters` and `@GenerateCommands`** over manual implementations
4. **USE declarative annotations** (`@ExploreFetcher`, `@DetailSelectors`, etc.) when possible
5. **ONLY write manual code** when KSP annotations cannot handle the use case

### Code Rules:
6. **ALWAYS use `abstract class`** for SourceFactory sources
7. **ALWAYS use `requestBuilder(url)`** with `client.get()`
8. **NEVER use Java imports** (`java.net.*`, `java.nio.*`)
9. **NEVER use `org.jsoup`** - use `com.fleeksoft.ksoup`
10. **NEVER use `chaptersRequest()`** in SourceFactory - use `getChapterListRequest()`
11. **ALWAYS check for WebView HTML** in custom `getChapterList()` overrides

---

## 📚 KSP Annotations Location

All KSP annotations are defined in:
```
annotations/src/commonMain/kotlin/tachiyomix/annotations/
├── Extension.kt           # @Extension
├── SourceAnnotations.kt   # @AutoSourceId, @GenerateFilters, @GenerateCommands, etc.
├── MadaraSource.kt        # @MadaraSource, @ThemeSource, @Selector, @DateFormat
├── SourceMeta.kt          # @SourceMeta, @ExploreFetcher, @DetailSelectors, etc.
├── ApiAnnotations.kt      # @ApiEndpoint, @RateLimit, @CustomHeader, etc.
```

---

*Following these rules will prevent 99% of compilation errors in AI-generated sources.*
*ALWAYS prefer KSP annotations over manual code for cleaner, more maintainable sources!*

---

# ADVANCED: JSON API Sources

When a site uses a JSON API instead of HTML scraping, you MUST override the core methods manually. The SourceFactory declarative annotations only work for HTML scraping.

## JSON API Pattern

```kotlin
package ireader.mysource

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

@Extension
@AutoSourceId(seed = "MySource")
abstract class MySource(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://example.com"
    override val id: Long get() = MySourceSourceId.ID
    override val name: String get() = "My Source"

    override fun getFilters(): FilterList = listOf(Filter.Title())
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // BROWSE/SEARCH - Override getMangaList for JSON API
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value ?: ""
        val endpoint = if (query.isNotBlank()) {
            "$baseUrl/api/search?q=$query&page=$page"
        } else {
            "$baseUrl/api/novels?page=$page&limit=20"
        }
        return try {
            val response = client.get(requestBuilder(endpoint))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val hasMore = json["hasMore"]?.jsonPrimitive?.boolean ?: false

            val mangaList = data.map { element ->
                val obj = element.jsonObject
                MangaInfo(
                    key = "$baseUrl/novel/${obj["slug"]?.jsonPrimitive?.content}",
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    cover = obj["cover"]?.jsonPrimitive?.content ?: ""
                )
            }
            MangasPageInfo(mangaList, hasMore)
        } catch (e: Exception) {
            Log.error { "Error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    // DETAIL - Override getMangaDetails for JSON API
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/novel/${manga.key.substringAfterLast("/")}"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            manga.copy(
                title = json["title"]?.jsonPrimitive?.content ?: manga.title,
                cover = json["cover"]?.jsonPrimitive?.content ?: manga.cover,
                description = json["description"]?.jsonPrimitive?.content ?: "",
                author = json["author"]?.jsonPrimitive?.content ?: ""
            )
        } catch (e: Exception) { manga }
    }

    // CHAPTERS - Override getChapterList for JSON API
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        return try {
            val slug = manga.key.substringAfterLast("/")
            val response = client.get(requestBuilder("$baseUrl/api/novel/$slug/chapters"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val chapters = json["chapters"]?.jsonArray ?: return emptyList()

            chapters.map { ch ->
                val obj = ch.jsonObject
                ChapterInfo(
                    name = obj["title"]?.jsonPrimitive?.content ?: "",
                    key = "$baseUrl/novel/$slug/chapter/${obj["number"]?.jsonPrimitive?.int}"
                )
            }.reversed()
        } catch (e: Exception) { emptyList() }
    }

    // CONTENT - Override getPageList (usually still HTML scraping)
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return try {
            val doc = client.get(requestBuilder(chapter.key)).asJsoup()
            doc.select(".chapter-content p").map { Text(it.text()) }
        } catch (e: Exception) { listOf(Text("Error loading content")) }
    }
}
```

## Dynamic JSON Parsing Rules

```kotlin
// ✅ CORRECT - Dynamic parsing (NO @Serializable!)
val json = Json.parseToJsonElement(response).jsonObject
val title = json["title"]?.jsonPrimitive?.content ?: ""
val count = json["count"]?.jsonPrimitive?.int ?: 0
val isActive = json["active"]?.jsonPrimitive?.boolean ?: false
val items = json["items"]?.jsonArray ?: emptyList()
val nested = json["data"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""

// ❌ WRONG - Causes IncompatibleClassChangeError at runtime!
@Serializable data class Novel(val title: String)
```

---

# ADVANCED: Cloudflare & Anti-Bot Bypass

## WebView-Based Bypass

When a site has Cloudflare protection, use the WebView to get past it:

```kotlin
override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
    // Check for pre-fetched HTML from WebView
    commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
        val doc = Ksoup.parse(cmd.html)
        return parseChapters(doc)
    }

    // If no WebView HTML, fetch normally
    return withContext(DefaultDispatcher) {
        val doc = client.get(requestBuilder(manga.key)).asJsoup()
        parseChapters(doc)
    }
}

override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
    // Check for pre-fetched HTML from WebView
    commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
        val doc = Ksoup.parse(cmd.html)
        return parseContent(doc)
    }

    return withContext(DefaultDispatcher) {
        val doc = client.get(requestBuilder(chapter.key)).asJsoup()
        parseContent(doc)
    }
}
```

## Custom Headers for Anti-Bot

```kotlin
override fun requestBuilder(url: String): HttpRequestBuilder {
    return super.requestBuilder(url).apply {
        headers.append("Referer", "$baseUrl/")
        headers.append("Accept-Language", "en-US,en;q=0.9")
    }
}
```

## CloudflareConfig Annotation

```kotlin
@Extension
@CloudflareConfig(enabled = true)
abstract class CloudflareSource(deps: Dependencies) : SourceFactory(deps = deps) {
    // Cloudflare bypass is handled automatically
}
```

---

# ADVANCED: Error Handling Patterns

## Safe API Calls

```kotlin
override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
    return try {
        val response = client.get(requestBuilder(endpoint))
        
        // Check for HTTP errors
        if (!response.status.isSuccess()) {
            Log.error { "HTTP ${response.status.value}: ${response.status.description}" }
            return MangasPageInfo(emptyList(), false)
        }
        
        val body = response.bodyAsText()
        
        // Check for rate limiting
        if (body.contains("rate limit", ignoreCase = true)) {
            Log.error { "Rate limited" }
            return MangasPageInfo(emptyList(), false)
        }
        
        // Parse JSON
        val json = Json.parseToJsonElement(body).jsonObject
        // ... parse data
        
    } catch (e: kotlinx.serialization.SerializationException) {
        Log.error { "JSON parse error: ${e.message}" }
        MangasPageInfo(emptyList(), false)
    } catch (e: io.ktor.client.plugins.ResponseException) {
        Log.error { "HTTP error: ${e.message}" }
        MangasPageInfo(emptyList(), false)
    } catch (e: Exception) {
        Log.error { "Unexpected error: ${e.message}" }
        MangasPageInfo(emptyList(), false)
    }
}
```

## Content Not Available Handling

```kotlin
override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
    return try {
        val doc = client.get(requestBuilder(chapter.key)).asJsoup()
        
        val content = doc.selectFirst(".chapter-content, #content, article")
        if (content == null) {
            return listOf(Text("Chapter not found or content locked."))
        }
        
        val paragraphs = content.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) {
            return listOf(Text("No content available."))
        }
        
        paragraphs.map { Text(it) }
    } catch (e: Exception) {
        listOf(Text("Error loading chapter: ${e.message}"))
    }
}
```

---

# QUICK REFERENCE: Method Signatures

```kotlin
// These are the EXACT method signatures - do not guess!
override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo
override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo
override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo
override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo>
override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page>
```

---

# FILE STRUCTURE CHEAT SHEET

```
sources/{lang}/{sourcename}/
├── build.gradle.kts           # Extension registration
├── README.md                  # Optional documentation
└── main/
    ├── assets/
    │   └── icon.png           # 192x192 recommended
    └── src/
        └── ireader/
            └── {sourcename}/  # Must match package name
                └── {SourceName}.kt
```

## build.gradle.kts Template
```kotlin
listOf("en").map { lang ->
    Extension(
        name = "SourceName",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "Description here",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "en/sourcename/main/assets",
    )
}.also(::register)
```

---

*This document is the single source of truth for creating IReader extensions.*
*All patterns are verified against the actual codebase.*
