# Creating Sources for IReader Extensions — Lessons Learned

## Overview

This guide documents practical lessons from creating novel source extensions (NovelBin, ReadNovelFull, Novelshub) for the IReader app. It covers common pitfalls, testing strategies, and patterns that work.

---

## 1. URL Structure is Everything

**Before writing any code, verify the actual URL patterns on the site.**

Common mistakes:
- Assuming `/novel/{slug}` when the real URL is `/{slug}.html`
- Assuming `/chapter/{number}` when the real URL is `/{slug}/chapter-{number}-{title}.html`

**How to verify:**
```bash
# Use curl to check actual URLs
curl -s "https://example.com/novel-list/latest" | grep -o 'href="[^"]*"' | head -10

# Or use the browse.js Puppeteer script
node browse.js "https://example.com/novel-list" ".novel-title a"
```

**Example from ReadNovelFull:**
- Wrong: `/novel/{slug}` → Right: `/{slug}.html`
- Wrong: `/chapter/{number}` → Right: `/{slug}/chapter-{number}-{title}.html`

---

## 2. AJAX Endpoints for Chapter Lists

Many sites don't load all chapters in the initial HTML. They use AJAX to fetch chapters on demand.

**How to find the AJAX endpoint:**
1. Open browser DevTools → Network tab
2. Click "Chapter List" tab or scroll to chapter section
3. Look for XHR requests
4. Note the endpoint URL and parameters

**Example from ReadNovelFull:**
- Endpoint: `/ajax/chapter-archive?novelId={id}`
- The `novelId` is in the HTML: `data-novel-id="2572"`
- This returns ALL chapters (1682+) vs 30 from initial page

**Pattern:**
```kotlin
// 1. Fetch detail page to get novelId
val response = client.get(requestBuilder(manga.key))
val body = response.bodyAsText()
val novelId = Regex("data-novel-id=\"(\\d+)\"").find(body)?.groupValues?.get(1)

// 2. Call AJAX endpoint
val chapterResponse = client.get(requestBuilder("$baseUrl/ajax/chapter-archive?novelId=$novelId"))
val chapterBody = chapterResponse.bodyAsText()
```

---

## 3. CSS Selectors — Verify Before Using

**Never guess selectors. Always verify with Puppeteer or curl.**

Install Puppeteer:
```bash
cd IReader-extensions && npm init -y && npm install puppeteer
```

Create `browse.js`:
```javascript
const puppeteer = require('puppeteer');
async function browse(url, selector) {
    const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox'] });
    const page = await browser.newPage();
    await page.goto(url, { waitUntil: 'networkidle2', timeout: 30000 });
    
    if (selector) {
        const count = await page.evaluate((sel) => document.querySelectorAll(sel).length, selector);
        console.log(`Selector "${selector}": ${count} elements`);
    }
    
    const title = await page.title();
    console.log('Title:', title);
    await browser.close();
}
browse(process.argv[2], process.argv[3]);
```

**Usage:**
```bash
node browse.js "https://example.com/novel-list" ".novel-title a"
node browse.js "https://example.com/novel/slug" "#list-chapter"
node browse.js "https://example.com/novel/slug/chapter/1" "#chr-content"
```

**Common selector patterns:**
| Site | Novel List | Chapter List | Content |
|------|-----------|--------------|---------|
| NovelBin | `.row h3.novel-title a` | `#list-chapter li a` | `#chr-content` |
| ReadNovelFull | `.novel-title a` | `ul.list-chapter li a` | `#chr-content, .chr-c` |
| Novelshub | API JSON | RSC payload | `.protected-content` |

---

## 4. Robolectric Integration Tests — Why They Fail

**The Problem:**
Generated integration tests use Robolectric which blocks network requests by default. Tests like `testFetchChaptersComprehensive` fail because:
1. `TestHttpClients` creates `HttpClient(OkHttp)` without proper configuration
2. Robolectric's `ShadowHttpURLConnection` intercepts and blocks all HTTP calls
3. The `@Config(sdk = [28])` annotation doesn't enable network access

**Why Robolectric blocks network:**
- Robolectric shadows `java.net.HttpURLConnection` to prevent real network calls
- Ktor's OkHttp engine uses `OkHttpClient` which doesn't use `HttpURLConnection` directly
- But Robolectric still intercepts at a lower level

**The Fix:**
1. Add `@Config(manifest = Config.NONE, sdk = [28])` to disable manifest processing
2. Use `HttpClient(OkHttp)` without `BrowserUserAgent` plugin (which uses `HttpURLConnection`)
3. Disable integration tests for now: `integrationTests = false` in `@GenerateTests`

**For future reference:**
- Integration tests now use plain JUnit with real Ktor HTTP (no Robolectric)
- Generated `TestHttpClients` uses `HttpClient(OkHttp)` for network requests
- Unit tests are safe (no network), integration tests make real requests
- Set `integrationTests = false` if site has Cloudflare or requires browser

---

## 5. Cloudflare Protection

**How to detect:**
- Page shows "Just a moment..." title
- HTML contains `challenges.cloudflare.com`
- Response is a challenge page, not content

**Solutions:**
1. **Browser engine** — Use `deps.httpClients.browser.fetch()` which renders JS
2. **WebView** — Show WebView to user for manual completion
3. **Cookie bypass** — Extract `cf_clearance` cookie from completed challenge

**Implementation:**
```kotlin
// Check for Cloudflare
if (html.contains("challenges.cloudflare.com") || html.contains("Just a moment")) {
    // Use browser engine to handle challenge
    val browserResult = deps.httpClients.browser.fetch(
        url = url,
        selector = ".content",  // Wait for this element
        timeout = 50000
    )
}
```

---

## 6. Test Annotations Reference

### `@GenerateTests`
Auto-generates unit and integration tests via KSP.
```kotlin
@GenerateTests(
    unitTests = true,           // Safe tests (no network)
    integrationTests = false,   // Network tests (Robolectric issue)
    searchQuery = "dragon",     // Search term for tests
    minSearchResults = 1        // Minimum expected results
)
```

### `@TestFixture`
Defines test URLs and expected values for validation.
```kotlin
@TestFixture(
    novelUrl = "https://example.com/novel/slug.html",
    chapterUrl = "https://example.com/novel/slug/chapter-1.html",
    expectedTitle = "My Novel",
    expectedAuthor = "Author",
    expectedMinChapters = 50
)
```

### `@TestExpectations`
Defines expected behavior for tests.
```kotlin
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 50,
    supportsPagination = true,
    requiresLogin = false,
    requiresJs = false
)
```

### `@UrlValidation`
Validates URL patterns returned by selectors.
```kotlin
@UrlValidation(
    novelPattern = "^https://example\\.com/[a-z0-9-]+\\.html$",
    chapterPattern = "^https://example\\.com/[a-z0-9-]+/chapter-\\d+-.*\\.html$",
    coverPattern = "^https?://.*\\.(jpg|png|webp)$"
)
```

### `@SelectorSnapshot`
Defines expected selector results for health checks.
```kotlin
@SelectorSnapshot(name = "novelTitle", selector = ".novel-title a", pageType = "list")
@SelectorSnapshot(name = "chapterContent", selector = "#chr-content", pageType = "content", expectedMinLength = 100)
```

### `@SkipTests`
Skip specific tests when features don't work.
```kotlin
@SkipTests(search = true, reason = "Site doesn't support search")
```

---

## 7. Source Code Patterns

### Basic Source Structure
```kotlin
@Extension
@AutoSourceId(seed = "MySource")
@GenerateTests(unitTests = true, integrationTests = false)
@TestFixture(novelUrl = "...", chapterUrl = "...", expectedTitle = "...")
@TestExpectations(minLatestNovels = 10, minChapters = 50)
@UrlValidation(novelPattern = "...", chapterPattern = "...")
@SelectorSnapshot(name = "title", selector = "h1", pageType = "detail")
abstract class MySource(private val deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://example.com"
    override val id: Long get() = MySourceSourceId.ID
    override val name: String get() = "MySource"
    // ... methods
}
```

### Chapter Parsing Pattern
```kotlin
private fun parseChaptersFromHtml(html: String, slug: String): List<ChapterInfo> {
    val doc = Ksoup.parse(html)
    val chapters = mutableListOf<ChapterInfo>()
    
    doc.select("ul.list-chapter li a[href*='/chapter-']").forEach { link ->
        val href = link.attr("href")
        val linkText = link.selectFirst(".nchr-text")?.text()?.trim() ?: link.text().trim()
        if (linkText.isBlank() || linkText.contains("Start Reading", ignoreCase = true)) return@forEach
        
        val chapterMatch = Regex("/chapter-(\\d+)-").find(href)
        if (chapterMatch != null) {
            val chapterNumber = chapterMatch.groupValues[1].toIntOrNull() ?: return@forEach
            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            chapters.add(ChapterInfo(name = linkText.ifBlank { "Chapter $chapterNumber" }, key = fullUrl))
        }
    }
    
    return chapters.distinctBy { it.key }.sortedBy {
        Regex("/chapter-(\\d+)-").find(it.key)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
```

### Content Parsing Pattern
```kotlin
private fun parseContentFromHtml(html: String): List<Page> {
    val doc = Ksoup.parse(html)
    val contentDiv = doc.selectFirst("#chr-content, .chr-c, .chapter-content")
    
    if (contentDiv != null) {
        val paragraphs = contentDiv.select("p").map { it.text() }.filter { it.isNotBlank() }
        if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }
        val text = contentDiv.text()
        if (text.isNotBlank()) return text.split("\n").filter { it.isNotBlank() }.map { Text(it) }
    }
    
    val bodyText = doc.body()?.text() ?: ""
    return bodyText.split("\n").filter { it.isNotBlank() }.map { Text(it) }
}
```

---

## 8. Common Pitfalls

1. **URL patterns change** — Always verify with Puppeteer before coding
2. **AJAX endpoints** — Check Network tab for hidden API calls
3. **Cloudflare** — Use browser engine, not plain HTTP
4. **Robolectric blocks network** — Disable integration tests
5. **Selector specificity** — `.cover, img` picks up wrong elements; use `.cover` specifically
6. **Duplicate code** — Watch for copy-paste errors in Kotlin
7. **TestHttpClients** — Generated mock HTTP client doesn't have proper engine configuration
