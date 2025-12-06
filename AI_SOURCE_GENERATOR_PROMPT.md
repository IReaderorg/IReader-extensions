# 🤖 IReader Source Generator - AI Prompt Guide

> **CRITICAL: This document contains EXACT imports, class names, and patterns from the actual codebase.**
> **DO NOT invent or guess any imports, classes, or methods not listed here.**

---

## ⚠️ STRICT RULES FOR AI

1. **ONLY use imports listed in this document** - No guessing!
2. **ONLY use classes and methods documented here** - They are verified to exist
3. **Follow the EXACT file structure** - Package names must match directory paths
4. **Copy templates exactly** - Then modify selectors only
5. **When in doubt, use the simpler approach** - @MadaraSource > SourceFactory

---

## 📋 Table of Contents

1. [Decision Tree](#-decision-tree)
2. [Verified Imports](#-verified-imports)
3. [Source Type 1: MadaraSource (Zero Code)](#-source-type-1-madarasource-zero-code)
4. [Source Type 2: SourceFactory (Declarative)](#-source-type-2-sourcefactory-declarative)
5. [Available Classes & Methods](#-available-classes--methods)
6. [File Structure](#-file-structure)
7. [build.gradle.kts Template](#-buildgradlekts-template)
8. [Step-by-Step Process](#-step-by-step-process)
9. [Common Patterns](#-common-patterns)

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
└── NO → Use SourceFactory with declarative fetchers
         - Define CSS selectors
         - Override exploreFetchers, detailFetcher, chapterFetcher, contentFetcher
```

---

## 📦 Verified Imports

### For @MadaraSource (Zero-Code):
```kotlin
import tachiyomix.annotations.MadaraSource
```

### For SourceFactory Sources:
```kotlin
// REQUIRED - Always include these
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

// MODEL CLASSES - Use as needed
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Page

// HTTP - Only if overriding requests
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters

// PARSING - Only if custom parsing needed
import ireader.core.source.asJsoup
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
```

### ❌ DO NOT USE THESE (They don't exist):
```kotlin
// WRONG - These do NOT exist in this project:
import ireader.common.utils.DateParser          // ❌ NOT AVAILABLE
import ireader.common.utils.ContentCleaner      // ❌ NOT AVAILABLE  
import ireader.core.source.helpers.*            // ❌ NOT AVAILABLE
import tachiyomix.annotations.AutoSourceId      // ❌ NOT PROCESSED YET
import tachiyomix.annotations.GenerateFilters   // ❌ NOT PROCESSED YET
import tachiyomix.annotations.GenerateCommands  // ❌ NOT PROCESSED YET
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

## 🏭 Source Type 2: SourceFactory (Declarative)

**Use when site is NOT Madara but has standard HTML structure.**

### Complete Working Template (COPY THIS):
```kotlin
package ireader.sitename

import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class SiteName(deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // REQUIRED: Basic Info
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://example.com"
    override val id: Long get() = 12345L
    override val name: String get() = "Site Name"

    // ═══════════════════════════════════════════════════════════════
    // REQUIRED: Filters
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    // ═══════════════════════════════════════════════════════════════
    // REQUIRED: Commands
    // ═══════════════════════════════════════════════════════════════
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

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

### Step 1: Determine Source Type

Open the website and check:
- Does URL look like `/novel/name/chapter-1/`? → **MadaraSource**
- Does it have `/wp-admin/`? → **MadaraSource**
- Otherwise → **SourceFactory**

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

Use this formula or run: `./gradlew generateSourceId -PsourceName="Name"`

For manual calculation:
```
MD5(lowercase(name) + "/" + lang + "/1") → first 8 bytes as Long
```

Or just use a unique number not used by other sources.

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

- [ ] Package name is `ireader.{lowercase_name}`
- [ ] Directory matches package: `ireader/{lowercase_name}/`
- [ ] Class is `abstract` and extends `SourceFactory`
- [ ] Has `@Extension` annotation
- [ ] All required overrides present (lang, baseUrl, id, name)
- [ ] `getFilters()` returns at least `Filter.Title()`
- [ ] `getCommands()` returns the three standard commands
- [ ] Selectors are valid CSS
- [ ] `addBaseUrlToLink = true` if URLs are relative
- [ ] build.gradle.kts has correct name and lang

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

### Create Madara Source:
```kotlin
package ireader.sitename
import tachiyomix.annotations.MadaraSource

@MadaraSource(name = "Name", baseUrl = "https://...", lang = "en", id = 123)
object SiteNameConfig
```

### Create SourceFactory Source:
```kotlin
package ireader.sitename
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.*
import tachiyomix.annotations.Extension

@Extension
abstract class SiteName(deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang = "en"
    override val baseUrl = "https://..."
    override val id = 123L
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
./gradlew :sources:en:sitename:assembleDebug
```

---

## ⚡ Quick Copy-Paste Templates

### Template A: Madara Source
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
```

### Template B: SourceFactory Source
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

@Extension
abstract class CHANGEME(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://CHANGEME.com"
    override val id: Long get() = CHANGEME_ID
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
