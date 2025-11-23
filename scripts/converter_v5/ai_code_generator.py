"""
AI-Powered Code Generator
Uses Gemini to generate actual working Kotlin implementations
"""

import os
import json
import urllib.request
from typing import Dict, Optional

class AICodeGenerator:
    """Generate Kotlin code using AI understanding"""
    
    def __init__(self):
        self.api_key = os.getenv('GEMINI_API_KEY')
    
    def generate_kotlin_from_typescript(self, ts_code: str, metadata: Dict, lang: str) -> Optional[str]:
        """Use AI to convert TypeScript to Kotlin"""
        if not self.api_key:
            return None
        
        prompt = self._create_conversion_prompt(ts_code, metadata, lang)
        
        try:
            response = self._call_gemini(prompt)
            if response:
                # Extract Kotlin code from response
                kotlin_code = self._extract_kotlin_code(response)
                return kotlin_code
        except Exception as e:
            print(f"   ⚠ AI Code Generation Error: {e}")
        
        return None
    
    def _create_conversion_prompt(self, ts_code: str, metadata: Dict, lang: str) -> str:
        """Create prompt for Kotlin code generation"""
        
        # Calculate ID
        import hashlib
        key = f"{metadata['id']}/{lang}/1"
        hash_bytes = hashlib.md5(key.encode()).digest()
        generated_id = int.from_bytes(hash_bytes[:8], 'big') & 0x7FFFFFFFFFFFFFFF
        
        package_name = f"ireader.{metadata['id'].replace('-', '').replace('_', '')}"
        class_name = ''.join(word.capitalize() for word in metadata['id'].replace('-', ' ').replace('_', ' ').split())
        
        return f"""Convert this TypeScript novel reader plugin to Kotlin for IReader. This is CRITICAL - the code MUST work exactly like the TypeScript version.

STEP 1: ANALYZE THE TYPESCRIPT CODE CAREFULLY
Read through the TypeScript and identify:
1. What selector is used for the novel list container? (in parseNovels)
2. What selector is used for the novel title? (in parseNovels)
3. What selector is used for the novel link? (in parseNovels)
4. What selector is used for the cover image? (in parseNovels)
5. What selector is used for novel details? (in parseNovel)
6. What selector is used for chapters? (in getChapters or parseNovel)
7. What selector is used for chapter content? (in parseChapter)

STEP 2: DETECT API TYPE
Check if the plugin uses JSON APIs or HTML scraping:
- Look for `.json()` calls in TypeScript (JSON API)
- Look for `loadCheerio()` or HTML parsing (HTML scraping)
- Many plugins use BOTH (HTML for details, JSON for lists/chapters)

STEP 3: EXTRACT EXACT SELECTORS
Do NOT guess or make up selectors. Use EXACTLY what you see in the TypeScript.

TYPESCRIPT CODE:
```typescript
{ts_code[:12000]}
```

REQUIREMENTS:
1. Package: {package_name}
2. Class: {class_name} (MUST be "abstract class", NOT just "class")
3. Extends: ParsedHttpSource(deps)
4. Plugin ID: {generated_id}L
5. Base URL: {metadata['site']}
6. Language: {lang}

CRITICAL: The class declaration MUST be "abstract class {class_name}" not "class {class_name}"

CRITICAL IMPLEMENTATION RULES:
1. **EXACT SELECTOR MATCHING**: Use the EXACT selectors from TypeScript
   - If TS uses `.book-item`, use `.book-item` in Kotlin
   - If TS uses `.title a`, use `.title a` in Kotlin
   - If TS uses `attr('data-src')`, use `.attr("data-src")` in Kotlin
   - If TS uses `attr('href')?.substring(1)`, remove leading slash in Kotlin too

2. **API CALLS**: If TypeScript makes API calls (like `api/manga/${{id}}/chapters`):
   - Extract the ID from HTML first (look for `bookId = (\\d+)` in script tags)
   - Build the API URL correctly: `$baseUrl/api/manga/$id/chapters?source=detail`
   - Use: `val html = client.get(requestBuilder(url)).bodyAsText()
   - Parse the returned HTML: `val doc = org.jsoup.Jsoup.parse(html)`

3. **HYBRID APPROACH**: Many plugins use BOTH HTML scraping AND API calls:
   - HTML scraping for: novel lists, novel details, chapter content
   - API calls for: chapter lists (common pattern)
   - Handle both in the same plugin

4. **URL BUILDING**:
   - If TS removes leading slash with `.substring(1)`, do the same
   - If TS adds `https:` prefix, handle protocol-relative URLs
   - Build full URLs: `$baseUrl/$path` (handle slashes correctly)

5. **CHAPTER FETCHING PATTERN** (very common):
   ```kotlin
   // Extract novel ID from script tag
   val novelId = document.selectFirst("script:containsData(bookId)")
       ?.data()?.let {{ Regex("bookId = (\\\\d+)").find(it)?.groupValues?.get(1) }}
       ?: throw Exception("Novel ID not found")
   

   val chaptersHtml = client.get(requestBuilder("$baseUrl/api/manga/$novelId/chapters?source=detail")).bodyAsText()
   
   // Parse chapters from returned HTML
   chaptersDoc.select("li").map {{ element -> ... }}
   ```

6. **JSOUP METHODS**:
   - `.select(selector)` returns list
   - `.selectFirst(selector)` returns single element or null
   - `.text()` gets text content
   - `.attr("name")` gets attribute
   - `.html()` gets HTML content
   - `.data()` gets script content

USE SOURCEFACTORY PATTERN (MUCH CLEANER AND SMALLER):

The SourceFactory pattern is declarative and requires minimal code. Use this instead of ParsedHttpSource.

KOTLIN TEMPLATE (MUST USE THIS EXACT STRUCTURE):
```kotlin
package {package_name}

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class {class_name}(deps: Dependencies) : SourceFactory(deps = deps) {{

    override val lang: String get() = "{lang}"
    override val baseUrl: String get() = "{metadata['site'].rstrip('/')}"
    override val id: Long get() = {generated_id}L
    override val name: String get() = "{metadata['name']}"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/search?page={{page}}",
                selector = ".book-item",
                nameSelector = ".title",
                linkSelector = ".title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?q={{query}}&page={{page}}",
                selector = ".book-item",
                nameSelector = ".title",
                linkSelector = ".title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".name h1",
            coverSelector = ".img-cover img",
            coverAtt = "data-src",
            descriptionSelector = ".section-body.summary .content",
            authorBookSelector = ".meta.box p:contains(Authors) a span",
            statusSelector = ".meta.box p:contains(Status) a",
            categorySelector = ".meta.box p:contains(Genres) a",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li",
            nameSelector = ".chapter-title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".chapter__content",
        )

    override fun pageContentParse(document: Document): List<Page> {{
        document.select("#listen-chapter, #google_translate_element").remove()
        return listOf(Text(document.select(".chapter__content").html() ?: ""))
    }}

    // IMPORTANT: If TypeScript uses API for chapters, add this override:
    // override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {{
    //     val doc = client.get(requestBuilder("$baseUrl/${{manga.key}}")).asJsoup()
    //     val novelId = doc.selectFirst("script")?.data()
    //         ?.let {{ Regex("bookId = (\\\\d+)").find(it)?.groupValues?.get(1) }}
    //         ?: return emptyList()
    //     val html = client.get(requestBuilder("$baseUrl/api/manga/$novelId/chapters?source=detail")).bodyAsText()
    //     val chaptersDoc = org.jsoup.Jsoup.parse(html)
    //     return chaptersDoc.select("li").mapNotNull {{ element ->
    //         val name = element.select(".chapter-title").text().trim()
    //         val href = element.select("a").attr("href")
    //         if (name.isBlank() || href.isBlank()) return@mapNotNull null
    //         
    //         val key = if (href.startsWith("/")) {{
    //             "$baseUrl$href"
    //         }} else {{
    //             "$baseUrl/$href"
    //         }}
    //         
    //         ChapterInfo(name = name, key = key)
    //     }}.reversed()
    // }}
}}
```

CRITICAL: Check the TypeScript code for API calls!
- If you see `api/manga/${{id}}/chapters` or similar API endpoint
- If you see `fetchApi(chapterListUrl)` in parseNovel() or getChapters()
- Then you MUST uncomment and include the getChapterList() override above!
- Extract the correct API endpoint URL from the TypeScript
- Extract the correct regex pattern for finding the novel ID

CRITICAL SOURCEFACTORY RULES:
1. **KEEP IT SIMPLE**: Only use Filter.Title() - NO complex filters
2. **Declarative Configuration**: Use exploreFetchers, detailFetcher, chapterFetcher, contentFetcher
3. **Selectors**: Extract EXACT CSS selectors from TypeScript and use them directly
4. **Attributes**: Use `nameAtt`, `linkAtt`, `coverAtt` to specify which attribute to extract
5. **Endpoints**: Use `{{page}}` and `{{query}}` placeholders (double curly braces!)
6. **Type**: Set `type = SourceFactory.Type.Search` for search fetchers
7. **pageContentParse**: Always override this to handle content properly
8. **API-Based Chapters**: If TypeScript uses API calls for chapters, override `getChapterList()`

DETECTING API-BASED CHAPTER FETCHING:
Look for these patterns in TypeScript:
- `fetchApi(${{this.site}}api/manga/${{id}}/chapters` 
- `const chapterListUrl = ...api...`
- API endpoint in parseNovel() or getChapters()

If found, you MUST override getChapterList() with the API logic!

SELECTOR EXTRACTION FROM TYPESCRIPT (CRITICAL):
You MUST extract the EXACT selectors from the TypeScript code. Do NOT make up selectors!

1. **List Selector**: Look for the main container in parseNovels()
   - Find: `loadedCheerio('.book-item')` or similar
   - Use: `selector = ".book-item"`

2. **Title Selector**: Look for how title is extracted
   - Find: `.find('.title')` or `.select('.title')`
   - Use: `nameSelector = ".title"`

3. **Link Selector**: Look for how URL is extracted
   - Find: `.find('.title a')` or `.select('a')`
   - Use: `linkSelector = ".title a"` or `linkSelector = "a"`

4. **Cover Selector**: Look for image selector
   - Find: `.find('img')` or `.select('img')`
   - Use: `coverSelector = "img"`

5. **Endpoint URLs**: Look in popularNovels() or similar methods
   - Find the URL construction: `${{this.site}}search?${{params.toString()}}`
   - Check filter defaults: `orderBy.value: 'views'`, `status.value: 'all'`
   - Build endpoint: `/search?sort=views&status=all&q=&page={{page}}`
   - For search: `/search?q={{query}}&page={{page}}`

6. **Detail Selectors**: Look in parseNovel() method
   - Title: `.select('.name h1')` → `nameSelector = ".name h1"`
   - Cover: `.select('.img-cover img')` → `coverSelector = ".img-cover img"`
   - Description: `.select('.summary .content')` → `descriptionSelector = ".summary .content"`

6. **Chapter Selectors**: Look in getChapters() or parseNovel()
   - Container: `loadedCheerio('li')` → `selector = "li"`
   - Name: `.find('.chapter-title')` → `nameSelector = ".chapter-title"`
   - Link: `.find('a')` → `linkSelector = "a"`

7. **Content Selectors**: Look in parseChapter()
   - Content: `.select('.chapter__content')` → `pageContentSelector = ".chapter__content"`
   - Remove: `.select('#ads').remove()` → `document.select("#ads").remove()`

EXAMPLE FROM TYPESCRIPT:
```typescript
loadedCheerio('.book-item').each((idx, ele) => {{
  const novelName = loadedCheerio(ele).find('.title').text();
  const novelCover = loadedCheerio(ele).find('img').attr('data-src');
  const novelUrl = loadedCheerio(ele).find('.title a').attr('href');
}});
```

BECOMES KOTLIN:
```kotlin
BaseExploreFetcher(
    "Latest",
    endpoint = "/search?sort=views&status=all&q=&page={{page}}",  // With default filter values!
    selector = ".book-item",           // From loadedCheerio('.book-item')
    nameSelector = ".title a",         // Use the LINK selector for name too!
    linkSelector = ".title a",         // From .find('.title a')
    linkAtt = "href",                  // From .attr('href')
    addBaseUrlToLink = true,           // ALWAYS add this for relative URLs!
    coverSelector = "img",             // From .find('img')
    coverAtt = "data-src",             // From .attr('data-src')
    addBaseurlToCoverLink = true,      // ALWAYS add this for relative cover URLs!
)
```

CRITICAL RULES:
1. Extract the COMPLETE URL with all default parameters from popularNovels()
2. ALWAYS add `addBaseUrlToLink = true` for exploreFetchers
3. ALWAYS add `addBaseurlToCoverLink = true` for exploreFetchers
4. ALWAYS add `addBaseUrlToLink = true` for chapterFetcher
5. ALWAYS add `addBaseurlToCoverLink = true` for detailFetcher

IMPORTANT RULE FOR NAME/LINK SELECTORS:
- If title uses `.find('.title').text()` 
- And link uses `.find('.title a').attr('href')`
- Then BOTH nameSelector and linkSelector should be ".title a"
- The framework will get text from the <a> tag for the name
- And get href attribute for the link

ATTRIBUTE EXTRACTION:
- If TS uses `.attr('href')` → use `linkAtt = "href"`
- If TS uses `.attr('src')` → use `coverAtt = "src"`  
- If TS uses `.attr('data-src')` → use `coverAtt = "data-src"`
- If TS uses `.text()` → use `nameSelector` without attribute

URL HANDLING:
- If TS removes leading slash with `.substring(1)` or `.slice(1)`, the framework handles this automatically
- If TS adds protocol `https:`, the framework handles this automatically
- Just provide the selectors and attributes

CONTENT PARSING:
- Always override `pageContentParse()` 
- Remove unwanted elements with `document.select("selector").remove()`
- Split content into paragraphs for better reading experience
- Return clean text without HTML tags
- Each paragraph should be a separate Text() item in the list

BEST PRACTICE PATTERN:
```kotlin
override fun pageContentParse(document: Document): List<Page> {{
    val content = document.select(".content-selector").first() ?: return emptyList()
    
    // Remove unwanted elements
    content.select("script, style, .ads, .advertisement").remove()
    
    // Select paragraphs INSIDE the content container
    // IMPORTANT: Use content.select("p") NOT document.select(".content-selector")
    return content.select("p").mapNotNull {{ element ->
        val text = element.text().trim()
        if (text.isNotEmpty()) Text(text) else null
    }}.ifEmpty {{
        // Fallback: if no paragraphs found, return all text as single item
        listOf(Text(content.text()))
    }}
}}
```

CRITICAL RULES:
1. ✅ First select the CONTAINER: `val content = document.select(".container").first()`
2. ✅ Then select PARAGRAPHS inside: `content.select("p")`
3. ✅ Each paragraph becomes ONE Text() item
4. ✅ Return List<Page> with multiple Text() items
5. ❌ Do NOT select the container in mapNotNull - select paragraphs!
6. ❌ Do NOT use `.childNodes()` or `.contents()`

WRONG PATTERN (DO NOT USE):
```kotlin
// ❌ WRONG - Selects container, not paragraphs
return document.select(".content-selector").mapNotNull {{ element ->
    Text(element.text())
}}
```

CORRECT PATTERN (USE THIS):
```kotlin
// ✅ CORRECT - Selects paragraphs inside container
val content = document.select(".content-selector").first() ?: return emptyList()
return content.select("p").mapNotNull {{ element ->
    val text = element.text().trim()
    if (text.isNotEmpty()) Text(text) else null
}}
```

JSON API HANDLING:
If the TypeScript uses `.json()` to parse API responses (like Fenrir Realm):

1. **Detect JSON APIs**: Look for `r.json()` or `r => r.json()` in TypeScript
2. **Empty exploreFetchers**: Set `override val exploreFetchers: List<BaseExploreFetcher> = emptyList()`
3. **Override getMangaList**: Implement custom methods that return `MangasPageInfo`
4. **Add Serialization**: Import `kotlinx.serialization.Serializable` and `kotlinx.serialization.json.Json`
5. **Create Data Classes**: Add `@Serializable` data classes matching the JSON structure

Example for JSON API plugin:
```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.findInstance

override val exploreFetchers: List<BaseExploreFetcher> = emptyList()

override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {{
    val order = if (sort?.name == "Latest") "latest" else "popular"
    return getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&status=any&order=$order")
}}

override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {{
    val query = filters.findInstance<Filter.Title>()?.value ?: ""
    return if (query.isNotEmpty()) {{
        getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&search=$query")
    }} else {{
        getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&status=any&order=popular")
    }}
}}

private suspend fun getFromAPI(url: String): MangasPageInfo {{
    val responseJson = client.get(requestBuilder(url)).bodyAsText()
    val json = Json {{ ignoreUnknownKeys = true }}
    val response = json.decodeFromString<APIResponse>(responseJson)
    
    val mangas = response.data.map {{ novel ->
        MangaInfo(
            key = "$baseUrl/series/${{novel.slug}}",  // IMPORTANT: Always use absolute URLs!
            title = novel.title,
            cover = "$baseUrl/${{novel.cover}}",
            description = novel.description ?: "",
            status = when (novel.status?.lowercase()) {{
                "ongoing" -> 1
                "completed" -> 2
                else -> 0
            }},
            genres = novel.genres?.map {{ it.name }} ?: emptyList()
        )
    }}
    
    return MangasPageInfo(mangas, hasNextPage = mangas.isNotEmpty())
}}

@Serializable
data class APIResponse(val data: List<APINovel>)

@Serializable
data class APINovel(
    val title: String,
    val slug: String,
    val cover: String,
    val description: String? = null,
    val status: String? = null,
    val genres: List<Genre>? = null
)

@Serializable
data class Genre(val name: String)
```

For JSON chapter lists:
```kotlin
override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {{
    // Extract slug from absolute URL: "https://site.com/series/slug" -> "slug"
    val novelSlug = manga.key.removePrefix("$baseUrl/series/")
    val chaptersJson = client.get(requestBuilder("$baseUrl/api/novels/chapter-list/$novelSlug")).bodyAsText()
    
    val json = Json {{ 
        ignoreUnknownKeys = true
        isLenient = true
    }}
    
    val chapters = try {{
        json.decodeFromString<List<APIChapter>>(chaptersJson)
    }} catch (e: Exception) {{
        println("Failed to parse chapters: ${{e.message}}")
        emptyList()
    }}
    
    return chapters.map {{ c ->
        ChapterInfo(
            name = "Chapter ${{c.number}}" + (if (!c.title.isNullOrBlank()) " - ${{c.title}}" else ""),
            key = "${{manga.key}}/chapter-${{c.number}}"  // IMPORTANT: Use manga.key directly (already absolute)
        )
    }}
}}

@Serializable
data class APIChapter(
    val locked: Locked? = null,
    val group: Group? = null,
    val title: String? = null,
    val number: Int
)

@Serializable
data class Locked(
    val price: Int? = null
)

@Serializable
data class Group(
    val index: Int? = null,
    val slug: String? = null
)
```

CRITICAL URL RULES (MUST FOLLOW):
- ✅ ALWAYS use ABSOLUTE URLs for `manga.key`: `"$baseUrl/series/${{slug}}"` NOT just `"${{slug}}"` or `"series/${{slug}}"`
- ✅ ALWAYS use ABSOLUTE URLs for `chapter.key`: `"${{manga.key}}/chapter-${{num}}"` 
- ✅ When calling APIs that need just the slug, extract it: `manga.key.removePrefix("$baseUrl/series/")`
- ✅ For chapter keys, use `manga.key` directly - it's already absolute, don't add baseUrl again!
- ❌ NEVER use relative URLs like `key = novel.slug` or `key = "series/${{slug}}"`
- ❌ NEVER concatenate like `baseUrl + "/" + novel.cover` - use string templates: `"$baseUrl/${{novel.cover}}"`
- ❌ NEVER do `"$baseUrl/series/${{manga.key.removePrefix(...)}}"` - just use `manga.key` directly!

EXAMPLE - CORRECT PATTERN:
```kotlin
// In getFromAPI - Create manga with ABSOLUTE URL
MangaInfo(
    key = "$baseUrl/series/${{novel.slug}}",  // ✅ ABSOLUTE URL
    cover = "$baseUrl/${{novel.cover}}"       // ✅ String template
)

// In getChapterList - Extract slug for API call
val novelSlug = manga.key.removePrefix("$baseUrl/series/")  // ✅ Extract slug
val json = client.get(requestBuilder("$baseUrl/api/novels/chapter-list/$novelSlug")).bodyAsText()

// Build chapter key from manga.key
ChapterInfo(
    key = "${{manga.key}}/chapter-${{c.number}}"  // ✅ Builds on absolute URL
)
```

EXAMPLE - WRONG PATTERNS (DO NOT USE):
```kotlin
// ❌ WRONG - Relative URL
MangaInfo(key = novel.slug)

// ❌ WRONG - Partial path
MangaInfo(key = "series/${{novel.slug}}")

// ❌ WRONG - String concatenation
MangaInfo(cover = baseUrl + "/" + novel.cover)

// ❌ WRONG - Using full URL in API call
val json = client.get(requestBuilder("$baseUrl/api/novels/chapter-list/${{manga.key}}"))
```
- ✅ The framework does NOT automatically prepend baseUrl to keys
- ✅ Keys must be complete URLs like: `https://site.com/series/novel-name/chapter-1`

CRITICAL JSON SERIALIZATION RULES:
- ✅ ALWAYS make nested object fields nullable: `val group: Group? = null`
- ✅ ALWAYS make fields inside nested objects nullable: `val slug: String? = null`
- ✅ ALWAYS add default values: `= null`
- ✅ Use `isLenient = true` in Json builder
- ❌ NEVER assume API fields are non-null
- ❌ NEVER omit default values

EXAMPLE:
```kotlin
@Serializable
data class APIChapter(
    val locked: Locked? = null,      // ✅ Nullable with default
    val group: Group? = null,         // ✅ Nullable with default
    val title: String? = null,        // ✅ Nullable with default
    val number: Int                   // ✅ Required field (no default)
)

@Serializable
data class Group(
    val index: Int? = null,           // ✅ All fields nullable
    val slug: String? = null          // ✅ All fields nullable
)
```

CRITICAL IMPORTS (ALWAYS INCLUDE ALL OF THESE):
```kotlin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension
```

IMPORTANT NOTES:
- ✅ Include ALL imports above, even if not all are used
- ✅ Use `client.get(requestBuilder(url)).asJsoup()` for HTML
- ✅ Use `client.get(requestBuilder(url)).bodyAsText()` for API text/JSON responses
- ✅ `findInstance` is needed for filter access
- ✅ `Listing` and `MangasPageInfo` are needed for custom explore methods
- ✅ `Serializable` and `Json` are needed for JSON API plugins

DO NOT:
- ❌ Use Filter.Header, Filter.Sort, Filter.Select, Filter.Group, Filter.CheckBox
- ❌ Override getChapterList() unless plugin uses API calls
- ❌ Use complex filter logic
- ❌ Add getExploreFilters(), getLatestFilters(), getPopularFilters() methods
- ❌ Import DetailInfo (it doesn't exist)
- ❌ Use `.asText()` - ALWAYS use `.bodyAsText()` instead!
- ❌ Use `.contents()` - ALWAYS use `.childNodes()` instead!

RESPOND WITH COMPLETE, WORKING KOTLIN CODE USING SOURCEFACTORY PATTERN. No explanations, no markdown outside code block."""

    def _call_gemini(self, prompt: str) -> Optional[str]:
        """Call Gemini API"""
        try:
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
            
            payload = {
                "contents": [{"parts": [{"text": prompt}]}],
                "generationConfig": {
                    "temperature": 0.2,
                    "maxOutputTokens": 8192,
                }
            }
            
            data = json.dumps(payload).encode('utf-8')
            req = urllib.request.Request(
                url,
                data=data,
                headers={
                    'Content-Type': 'application/json',
                    'X-goog-api-key': self.api_key
                }
            )
            
            with urllib.request.urlopen(req, timeout=60) as response:
                result = json.loads(response.read().decode('utf-8'))
                
                if 'candidates' in result and len(result['candidates']) > 0:
                    text = result['candidates'][0]['content']['parts'][0]['text']
                    return text
        except Exception as e:
            print(f"   ⚠ Gemini API Error: {e}")
        
        return None
    
    def _extract_kotlin_code(self, response: str) -> str:
        """Extract Kotlin code from AI response"""
        # Remove markdown code blocks
        text = response.strip()
        
        if '```kotlin' in text:
            # Extract code between ```kotlin and ```
            start = text.find('```kotlin') + 9
            end = text.find('```', start)
            if end > start:
                return text[start:end].strip()
        elif '```' in text:
            # Extract code between ``` and ```
            start = text.find('```') + 3
            end = text.find('```', start)
            if end > start:
                return text[start:end].strip()
        
        # If no code blocks, return as is
        return text.strip()
