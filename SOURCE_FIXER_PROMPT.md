# IReader Source Fixer - AI Prompt

## 🎯 Objective

Fix and validate IReader extension sources by:
1. Using browser-based MCP to test website accessibility and extract correct CSS selectors
2. Updating sources to use correct selectors and patterns
3. Tracking progress in `sources-fix-tracker.json`
4. Documenting non-standard sites for later implementation

---

## 🚨 CRITICAL RULES

1. **DO NOT REMOVE SOURCES** - Only mark them in the tracker for review
2. **DOCUMENT EVERYTHING** - When a site has non-standard structure, document it in `needs_review`
3. **KEEP EXISTING IDs** - Never change source IDs for existing sources
4. **USE KSP ANNOTATIONS** - Follow patterns from `AI_SOURCE_GENERATOR_PROMPT.md`
5. **ALWAYS use `abstract class`** - SourceFactory sources MUST be abstract
6. **ALWAYS use `requestBuilder(url)`** - Never use `client.get(url)` directly
7. **🧪 MANDATORY: ADD KSP TEST ANNOTATIONS** - Every fixed source MUST have test annotations!

---

## 🧪 MANDATORY: KSP Test Annotations

**EVERY fixed source MUST include these test annotations:**

```kotlin
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations

@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "popular_search_term",  // Use a common search term for the site
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://example.com/novel/known-novel/",      // A working novel URL
    chapterUrl = "https://example.com/novel/known-novel/ch-1/", // A working chapter URL
    expectedTitle = "Known Novel Title",                       // Expected title
    expectedAuthor = "Author Name"                             // Expected author (or "")
)
@TestExpectations(
    minLatestNovels = 10,      // Minimum novels in latest listing
    minChapters = 5,           // Minimum chapters expected
    supportsPagination = true,  // Does the site support pagination?
    requiresLogin = false       // Does it require login?
)
abstract class SourceName(deps: Dependencies) : SourceFactory(deps = deps) {
    // ...
}
```

### How to Get Test Fixture Data:

1. **During browser testing**, note down:
   - A working novel URL (from detail page)
   - A working chapter URL (from content page)
   - The exact novel title shown on the page
   - The author name (if available)

2. **For searchQuery**: Use a common term that returns results (e.g., "dragon", "system", "reborn")

3. **For minLatestNovels**: Count how many novels appear on the listing page

4. **For minChapters**: Check how many chapters the test novel has

---

## 📋 Workflow

### Step 1: Check Source Status
1. Read `sources-fix-tracker.json` to see which sources are already processed
2. Pick the next source from `pending` array
3. Skip sources already in "fixed", "removed", "skipped", or "needs_review"

### Step 2: Test Website Accessibility
Use browser MCP to navigate to the source's baseUrl:
```
mcp_playwright_browser_navigate to baseUrl
mcp_playwright_browser_wait_for(time=3)
mcp_playwright_browser_snapshot()
```

**Decision Tree:**

```
Is site accessible?
│
├── NO (dead/parked/error) → Add to "removed" array
│
└── YES → Is it a standard novel site?
          │
          ├── YES (standard structure) → Continue to Step 3
          │
          └── NO (non-standard) → Document in "needs_review" with structure details
              Examples of non-standard:
              - Blogger-based sites
              - Manga/manhwa sites
              - Sites requiring authentication
              - Sites with complex JavaScript rendering
              - Sites with unusual URL patterns
```

### Step 3: Extract Selectors Using Browser

Navigate through the site to verify/extract selectors:

1. **Novel Listing Page:**
   ```
   mcp_playwright_browser_navigate to latest/popular page
   mcp_playwright_browser_snapshot()
   ```
   - Find selector for novel cards
   - Find selector for title, link, cover

2. **Novel Detail Page:**
   ```
   mcp_playwright_browser_click on a novel
   mcp_playwright_browser_snapshot()
   ```
   - Find selector for title, cover, description, author, genres, status

3. **Chapter List:**
   - Find selector for chapter items
   - Check if chapters are in correct order (may need `reverseChapterList = true`)

4. **Chapter Content:**
   ```
   mcp_playwright_browser_click on a chapter
   mcp_playwright_browser_snapshot()
   ```
   - Find selector for content paragraphs
   - Check for ads/unwanted elements to filter

### Step 4: Update Source Code

Follow the patterns from `AI_SOURCE_GENERATOR_PROMPT.md`:

```kotlin
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations

@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "dragon",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://example.com/novel/test-novel/",
    chapterUrl = "https://example.com/novel/test-novel/chapter-1/",
    expectedTitle = "Test Novel Title",
    expectedAuthor = "Author Name"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 5,
    supportsPagination = true,
    requiresLogin = false
)
abstract class SourceName(deps: Dependencies) : SourceFactory(deps = deps) {
    
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://example.com"
    override val id: Long get() = EXISTING_ID  // Keep existing ID!
    override val name: String get() = "Source Name"

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
                endpoint = "/novels/page/{page}/",
                selector = ".novel-item",
                nameSelector = ".title",
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
                selector = ".novel-item",
                nameSelector = ".title",
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
            nameSelector = "h1",
            coverSelector = ".cover img",
            coverAtt = "src",
            descriptionSelector = ".description",
            authorBookSelector = ".author",
            categorySelector = ".genres a",
            addBaseurlToCoverLink = true
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
            pageContentSelector = ".chapter-content p"
        )
}
```

### Step 5: Update Tracker

After processing each source, update `sources-fix-tracker.json`:

```json
{
  "lastUpdated": "YYYY-MM-DD",
  "status": "in_progress",
  "fixed": ["source1", "source2"],
  "failed": [],
  "skipped": [],
  "removed": ["dead_site"],
  "needs_review": [
    {
      "name": "source_name",
      "reason": "Detailed description of why it needs review",
      "url": "https://example.com",
      "structure": {
        "novels_page": "/path/to/novels",
        "detail_pattern": "/path/{novel}/",
        "chapter_pattern": "/path/{novel}/{chapter}",
        "selectors": {
          "novel_list": "selector",
          "detail_title": "selector",
          "chapter_list": "selector",
          "content": "selector"
        }
      }
    }
  ],
  "pending": ["remaining_sources"]
}
```

---

## 📊 Tracker Categories

### fixed
Sources that are working correctly:
- Website is accessible
- All selectors work
- Source compiles without errors

### failed
Sources with unresolvable issues:
- Persistent errors that can't be fixed
- Sites that block scraping entirely

### skipped
Sources that need special handling:
- Require authentication/login
- Have Cloudflare protection that can't be bypassed
- Need WebView-only access

### removed
Dead or unusable sites:
- Domain is parked/for sale
- Site returns 404/500 errors
- Site has completely changed purpose

### needs_review
Sources requiring human decision or custom implementation:
- **Non-standard structure** (Blogger, custom CMS)
- **Wrong site type** (manga instead of novel)
- **Package name issues** (wrong directory structure)
- **Complex JavaScript** (requires special handling)
- **Unusual URL patterns** (non-standard routing)

**IMPORTANT:** Always include detailed `structure` information for needs_review sources!

---

## 📝 Documenting Non-Standard Sites

When a site has non-standard structure, document it thoroughly:

```json
{
  "name": "dreambigtl",
  "reason": "Blogger-based site with non-standard structure. Novels page at /p/novels.html lists TOC pages. Each novel has a TOC page with chapters as paragraph links.",
  "url": "https://dreambigtl.com",
  "structure": {
    "novels_page": "/p/novels.html",
    "toc_pattern": "/p/{novel-name}-toc.html",
    "chapter_pattern": "/{year}/{month}/{chapter-name}.html",
    "selectors": {
      "novel_list": "article .post-body a[href*='-toc.html']",
      "detail_title": "article h1",
      "detail_cover": "article img",
      "detail_description": "article p (before 'Table of Contents')",
      "chapter_list": "article p a",
      "content": ".post-body div, .post-body p"
    }
  }
}
```

This documentation helps when implementing the source later.

---

## 🔍 Browser Testing Commands

```kotlin
// Navigate to site
mcp_playwright_browser_navigate(url = "https://example.com")

// Wait for page load
mcp_playwright_browser_wait_for(time = 3)

// Get page snapshot (for selector verification)
mcp_playwright_browser_snapshot()

// Click on element
mcp_playwright_browser_click(element = "description", ref = "e123")

// Take screenshot if needed
mcp_playwright_browser_take_screenshot()

// Check console for errors
mcp_playwright_browser_console_messages()
```

---

## ⚠️ Common Issues & Fixes

### 1. Dead/Parked Domain
**Signs:** Redirects to parking page, shows "domain for sale", 404 errors
**Action:** Add to "removed" in tracker, DO NOT delete source files

### 2. Cloudflare Protection
**Signs:** Challenge page, 403 errors, "checking your browser"
**Action:** Add to "skipped" with note about CF protection

### 3. Wrong Selectors
**Signs:** Empty results, null errors
**Fix:** Use browser snapshot to find correct selectors

### 4. Missing baseUrl in links
**Signs:** Relative URLs not working, 404 on novel/chapter pages
**Fix:** Add `addBaseUrlToLink = true`

### 5. Lazy-loaded images
**Signs:** Images show placeholder or don't load
**Fix:** Use `coverAtt = "data-src"` or `"data-lazy-src"` instead of `"src"`

### 6. Reversed chapter list
**Signs:** Chapters in wrong order (newest first when should be oldest first)
**Fix:** Set `reverseChapterList = true`

### 7. Non-standard site structure
**Signs:** Site doesn't follow typical novel site patterns
**Action:** Document in "needs_review" with full structure details

### 8. Wrong package name
**Signs:** Package doesn't match directory structure
**Action:** Document in "needs_review", note the correct package name

---

## 📁 Verified Imports (from AI_SOURCE_GENERATOR_PROMPT.md)

```kotlin
// REQUIRED - Always include these
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import tachiyomix.annotations.Extension

// HTTP - Only if overriding requests
import io.ktor.client.request.get
import ireader.core.source.asJsoup
import ireader.core.source.findInstance

// PARSING - Only if custom parsing needed
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
```

**DO NOT USE:**
```kotlin
// ❌ These don't exist
import org.jsoup.nodes.Document  // Use com.fleeksoft.ksoup
import java.net.URLEncoder       // Not available in KMP
import ireader.common.utils.*   // Doesn't exist
```

---

## 🎯 Success Criteria

A source is considered "fixed" when:
1. ✅ Website is accessible
2. ✅ All selectors work correctly (verified via browser)
3. ✅ Source compiles without errors
4. ✅ Basic functionality verified (listing, detail, chapters, content)
5. ✅ **KSP test annotations added** (`@GenerateTests`, `@TestFixture`, `@TestExpectations`)

A source goes to "needs_review" when:
1. ⚠️ Site has non-standard structure
2. ⚠️ Site is not a novel site (manga, manhwa, etc.)
3. ⚠️ Package name doesn't match directory
4. ⚠️ Requires custom implementation beyond standard SourceFactory

---

## 📊 Current Progress Example

```json
{
  "lastUpdated": "2024-12-22",
  "status": "in_progress",
  "fixed": [
    "allnovelfull",
    "freewebnovel",
    "novelfull",
    "royalroad"
  ],
  "failed": [],
  "skipped": [],
  "removed": [
    "bestlightnovel",
    "boxnovelcom",
    "comrademao",
    "daonovel"
  ],
  "needs_review": [
    {
      "name": "coolnovel",
      "reason": "Manga/manhwa site that redirects to external sources for content. Wrong package name.",
      "url": "https://www.novelcool.com"
    },
    {
      "name": "dreambigtl",
      "reason": "Blogger-based site with non-standard structure.",
      "url": "https://dreambigtl.com",
      "structure": { ... }
    }
  ],
  "pending": [
    "fanmtl",
    "fastnovel",
    ...
  ]
}
```

---

## 🚀 Quick Reference

### Check if site is Madara (WordPress theme)
- URL pattern: `/novel/novel-name/chapter-1/`
- Has `/wp-admin/` page
- Similar layout to BoxNovel, NovelFull

If Madara → Use `@MadaraSource` annotation (zero code!)

### Standard SourceFactory Pattern
1. `abstract class` extending `SourceFactory`
2. `@Extension` annotation
3. Override: `lang`, `baseUrl`, `id`, `name`
4. Override: `getFilters()`, `getCommands()`
5. Override: `exploreFetchers`, `detailFetcher`, `chapterFetcher`, `contentFetcher`

### When to use needs_review
- Blogger/custom CMS sites
- Manga/manhwa sites
- Sites with unusual URL patterns
- Sites requiring authentication
- Package name mismatches

---

*Use this prompt to systematically fix all IReader extension sources while preserving sources for later review.*
