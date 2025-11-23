# Fenrir Realm - JSON API Implementation

## Problem
The Fenrir Realm plugin was completely broken because the AI converter tried to parse JSON API responses as HTML.

### Root Cause
- Fenrir uses **JSON APIs** for both explore (popular/latest/search) and chapters
- The converter generated HTML parsing code with `exploreFetchers` 
- SourceFactory's `exploreFetchers` expect HTML responses, not JSON
- This caused all features to fail

## Solution
Implemented custom API handling with proper JSON deserialization.

### 1. Explore Methods (Popular/Latest/Search)
**API Endpoint:**
```
GET /api/series/filter?page={page}&per_page=20&status=any&order={sort}
```

**Response Format:**
```json
{
  "data": [
    {
      "title": "Novel Title",
      "slug": "novel-slug",
      "cover": "storage/covers/image.jpg",
      "description": "Novel description",
      "status": "ongoing",
      "genres": [{"name": "Fantasy"}, {"name": "Action"}]
    }
  ]
}
```

**Implementation:**
```kotlin
override suspend fun getMangaList(sort: Listing?, page: Int): List<MangaInfo> {
    val order = when (sort) {
        Listing.Popular -> "popular"
        Listing.Latest -> "latest"
        else -> "popular"
    }
    return getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&status=any&order=$order")
}

override suspend fun getMangaList(filters: FilterList, page: Int): List<MangaInfo> {
    val query = filters.findInstance<Filter.Title>()?.value ?: ""
    return if (query.isNotEmpty()) {
        getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&search=$query")
    } else {
        getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&status=any&order=popular")
    }
}

private suspend fun getFromAPI(url: String): List<MangaInfo> {
    val responseJson = client.get(requestBuilder(url)).bodyAsText()
    val json = Json { ignoreUnknownKeys = true }
    val response = json.decodeFromString<APIResponse>(responseJson)
    
    return response.data.map { novel ->
        MangaInfo(
            key = novel.slug,
            title = novel.title,
            cover = "$baseUrl/${novel.cover}",
            description = novel.description ?: "",
            status = when (novel.status?.lowercase()) {
                "ongoing" -> 1
                "completed" -> 2
                else -> 0
            },
            genres = novel.genres?.map { it.name } ?: emptyList()
        )
    }
}
```

### 2. Chapter List
**API Endpoint:**
```
GET /api/novels/chapter-list/{novelPath}
```

**Response Format:**
```json
[
  {
    "locked": {"price": 100},
    "group": {"index": 1, "slug": "volume-1"},
    "title": "Chapter Title",
    "number": 1,
    "created_at": "2024-01-01T00:00:00Z"
  }
]
```

**Implementation:**
```kotlin
override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
    val novelPath = manga.key
    val chaptersJson = client.get(requestBuilder("$baseUrl/api/novels/chapter-list/$novelPath")).bodyAsText()
    
    val json = Json { ignoreUnknownKeys = true }
    val chapters = json.decodeFromString<List<APIChapter>>(chaptersJson)
    
    return chapters.map { c ->
        val chapterName =
            (if (c.locked?.price != null) "🔒 " else "") +
            (if (c.group?.index == null) "" else "Vol ${c.group.index} ") +
            "Chapter ${c.number}" +
            (if (!c.title.isNullOrBlank() && c.title.trim() != "Chapter ${c.number}") 
                " - ${c.title.replace(Regex("^chapter [0-9]+ . ", RegexOption.IGNORE_CASE), "")}" 
            else "")

        val chapterPath =
            novelPath +
            (if (c.group?.index == null) "" else "/${c.group.slug}") +
            "/chapter-${c.number}"

        ChapterInfo(
            name = chapterName,
            key = chapterPath,
            dateUpload = c.created_at
        )
    }.sortedBy { it.name }
}
```

### 3. Data Classes
```kotlin
@Serializable
data class APIResponse(
    val data: List<APINovel>
)

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
data class Genre(
    val name: String
)

@Serializable
data class APIChapter(
    val locked: Locked? = null,
    val group: Group? = null,
    val title: String? = null,
    val number: Int,
    val created_at: String
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

### 4. Content Parsing (HTML)
Content is still HTML-based, so we use the standard approach:

```kotlin
override fun pageContentParse(document: Document): List<Page> {
    val chapter = document.select("[id^=\"reader-area-\"]").first() ?: return emptyList()
    // Remove HTML comments
    val nodesToRemove = chapter.childNodes().filter { it.nodeName() == "#comment" }
    nodesToRemove.forEach { it.remove() }
    return listOf(Text(chapter.html() ?: ""))
}
```

## Key Changes

1. **Removed exploreFetchers** - They don't work with JSON APIs
2. **Added custom explore methods** - `getPopular()`, `getLatest()`, `getSearch()`
3. **JSON deserialization** - Using kotlinx.serialization
4. **Proper data mapping** - Convert API models to IReader models
5. **Status mapping** - Convert string status to integer codes
6. **Cover URL handling** - Prepend baseUrl to relative paths
7. **Chapter formatting** - Handle locked chapters, volumes, and titles

## Testing
```bash
./gradlew :extensions:v5:en:fenrir:assembleDebug
```

Expected: ✅ BUILD SUCCESSFUL

## Lessons for Converter

The converter needs to detect JSON API patterns:
- Look for `r.json()` in TypeScript
- Look for `APIResponse`, `APINovel`, `APIChapter` types
- When detected, generate custom explore methods instead of exploreFetchers
- Add kotlinx.serialization imports and data classes
- Use `Json { ignoreUnknownKeys = true }` for flexible parsing

This is a limitation of the current SourceFactory pattern - it assumes HTML scraping, not JSON APIs.
