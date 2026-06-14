package ireader.novelshub

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
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
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

@Extension
@AutoSourceId(seed = "Novelshub")
abstract class Novelshub(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://novelshub.org"
    override val id: Long get() = NovelshubSourceId.ID
    override val name: String get() = "Novelshub"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/series?page=$page&limit=20"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val meta = json["meta"]?.jsonObject
            val hasMore = meta?.get("hasMore")?.jsonPrimitive?.boolean ?: false

            val mangaList = data.map { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
                val coverImage = obj["coverImage"]?.jsonPrimitive?.content ?: ""
                val fullCover = if (coverImage.startsWith("http")) coverImage else "$baseUrl$coverImage"
                MangaInfo(key = "$baseUrl/series/novel/$slug", title = title, cover = fullCover)
            }

            MangasPageInfo(mangaList, hasMore)
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val query = titleFilter?.value ?: ""

        val endpoint = if (query.isNotBlank()) {
            "$baseUrl/api/series?search=$query&page=$page&limit=20"
        } else {
            "$baseUrl/api/series?page=$page&limit=20"
        }

        return try {
            val response = client.get(requestBuilder(endpoint))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val meta = json["meta"]?.jsonObject
            val hasMore = meta?.get("hasMore")?.jsonPrimitive?.boolean ?: false

            val mangaList = data.map { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
                val coverImage = obj["coverImage"]?.jsonPrimitive?.content ?: ""
                val fullCover = if (coverImage.startsWith("http")) coverImage else "$baseUrl$coverImage"
                MangaInfo(key = "$baseUrl/series/novel/$slug", title = title, cover = fullCover)
            }

            MangasPageInfo(mangaList, hasMore)
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        // Check for pre-fetched HTML from WebView (Command.Detail.Fetch)
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                return parseDetailsFromHtml(cmd.html, manga)
            }
        }

        // Use browser engine to render the JavaScript-heavy page
        return try {
            val browserResult = deps.httpClients.browser.fetch(
                url = manga.key,
                selector = "h1, meta[property=og:title]",
                timeout = 50000
            )
            
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                parseDetailsFromHtml(browserResult.responseBody, manga)
            } else {
                // Fallback to regular HTTP request
                val response = client.get(requestBuilder(manga.key))
                val doc = response.asJsoup()
                
                val title = doc.selectFirst("h1")?.text() ?: manga.title
                val cover = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: manga.cover
                val description = doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
                val author = doc.selectFirst("meta[name=author]")?.attr("content") ?: ""
                
                manga.copy(
                    title = title,
                    cover = cover,
                    description = description,
                    author = author,
                )
            }
        } catch (e: Exception) {
            Log.error { "Error fetching manga detail: ${e.message}" }
            manga
        }
    }

    private fun parseDetailsFromHtml(html: String, manga: MangaInfo): MangaInfo {
        val doc = com.fleeksoft.ksoup.Ksoup.parse(html)
        
        // Only overwrite title if scraped one is valid (not "JavaScript Required" etc.)
        val scrapedTitle = doc.selectFirst("h1")?.text() 
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
        val title = if (!scrapedTitle.isNullOrBlank() && 
                        !scrapedTitle.contains("JavaScript", ignoreCase = true) &&
                        !scrapedTitle.contains("Loading", ignoreCase = true) &&
                        !scrapedTitle.contains("Just a moment", ignoreCase = true)) {
            scrapedTitle
        } else {
            manga.title  // Keep the original title from API
        }
        
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: manga.cover
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
        val author = doc.selectFirst("meta[name=author]")?.attr("content") ?: ""
        
        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val slug = manga.key.substringAfterLast("/")

        // Check for pre-fetched HTML from WebView (Command.Chapter.Fetch)
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                return parseChaptersFromHtml(cmd.html, slug)
            }
        }

        // Use browser to fetch the series page (has full chapter list in RSC payload)
        return try {
            val browserResult = deps.httpClients.browser.fetch(
                url = manga.key,
                selector = "h1",
                timeout = 50000
            )
            
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                val chapters = extractChaptersFromRscPayload(browserResult.responseBody, slug)
                if (chapters.isNotEmpty()) {
                    chapters
                } else {
                    // Fallback: parse chapter links from rendered HTML
                    parseChaptersFromHtml(browserResult.responseBody, slug)
                }
            } else {
                // Fallback to API (limited to 4 chapters)
                fetchChaptersFromApi(slug)
            }
        } catch (e: Exception) {
            Log.error { "Error fetching chapters: ${e.message}" }
            fetchChaptersFromApi(slug)
        }
    }

    private suspend fun fetchChaptersFromApi(slug: String): List<ChapterInfo> {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/series?page=1&limit=100&search=$slug"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return emptyList()

            val series = data.firstOrNull { element ->
                val obj = element.jsonObject
                obj["slug"]?.jsonPrimitive?.content == slug
            }?.jsonObject ?: return emptyList()

            val chapters = series["chapters"]?.jsonArray ?: return emptyList()
            chapters.map { chapter ->
                val obj = chapter.jsonObject
                val chapterTitle = obj["title"]?.jsonPrimitive?.content ?: ""
                val chapterNumber = obj["number"]?.jsonPrimitive?.int ?: 0
                ChapterInfo(
                    name = "Chapter $chapterNumber: $chapterTitle",
                    key = "$baseUrl/series/novel/$slug/chapter/$chapterNumber",
                )
            }.reversed()
        } catch (e: Exception) {
            Log.error { "Error fetching chapters from API: ${e.message}" }
            emptyList()
        }
    }

    private fun extractChaptersFromRscPayload(html: String, slug: String): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        
        // Extract allChapters from RSC payload (self.__next_f.push calls)
        val rscPattern = Regex("""self\.__next_f\.push\(\[1,"(.*?)"\]\)""", RegexOption.DOT_MATCHES_ALL)
        val rscMatches = rscPattern.findAll(html)
        
        for (match in rscMatches) {
            val payload = match.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            
            // Look for allChapters JSON array
            val allChaptersPattern = Regex(""""allChapters":\s*(\[.*?\])\s*,\s*"prevChapter"""", RegexOption.DOT_MATCHES_ALL)
            val allChaptersMatch = allChaptersPattern.find(payload)
            
            if (allChaptersMatch != null) {
                try {
                    val chaptersJson = Json.parseToJsonElement(allChaptersMatch.groupValues[1]).jsonArray
                    for (chapterElement in chaptersJson) {
                        val chapterObj = chapterElement.jsonObject
                        val number = chapterObj["number"]?.jsonPrimitive?.int ?: continue
                        val title = chapterObj["title"]?.jsonPrimitive?.content ?: ""
                        
                        chapters.add(
                            ChapterInfo(
                                name = "Chapter $number: $title",
                                key = "$baseUrl/series/novel/$slug/chapter/$number",
                            )
                        )
                    }
                    if (chapters.isNotEmpty()) break
                } catch (e: Exception) {
                    Log.error { "Error parsing allChapters JSON: ${e.message}" }
                }
            }
        }
        
        return chapters.sortedBy { 
            Regex("/chapter/(\\d+)").find(it.key)?.groupValues?.get(1)?.toIntOrNull() ?: 0 
        }
    }

    private fun parseChaptersFromHtml(html: String, slug: String): List<ChapterInfo> {
        val doc = com.fleeksoft.ksoup.Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()

        // Find chapter links - filter out "Start Reading" and similar non-chapter links
        doc.select("a[href*='/chapter/']").forEach { link ->
            val href = link.attr("href")
            val linkText = link.text().trim()
            
            // Skip "Start Reading" and similar non-chapter links
            if (linkText.contains("Start Reading", ignoreCase = true) ||
                linkText.contains("Begin Reading", ignoreCase = true) ||
                linkText.isBlank()) {
                return@forEach
            }
            
            val chapterMatch = Regex("/chapter/(\\d+)").find(href)
            if (chapterMatch != null) {
                val chapterNumber = chapterMatch.groupValues[1].toIntOrNull() ?: return@forEach
                val chapterName = link.text().trim().ifBlank { "Chapter $chapterNumber" }
                chapters.add(
                    ChapterInfo(
                        name = chapterName,
                        key = "$baseUrl/series/novel/$slug/chapter/$chapterNumber",
                    )
                )
            }
        }

        return chapters.distinctBy { it.key }.sortedBy { 
            Regex("/chapter/(\\d+)").find(it.key)?.groupValues?.get(1)?.toIntOrNull() ?: 0 
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        // Check for pre-fetched HTML from WebView (Command.Content.Fetch)
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                return parseContentFromHtml(cmd.html)
            }
        }

        // Use browser engine to render the JavaScript-heavy page
        return try {
            val browserResult = deps.httpClients.browser.fetch(
                url = chapter.key,
                selector = ".protected-content",
                timeout = 50000
            )
            
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                parseContentFromHtml(browserResult.responseBody)
            } else {
                // Fallback to regular HTTP request
                val response = client.get(requestBuilder(chapter.key))
                val doc = response.asJsoup()
                
                val content = doc.selectFirst(".protected-content")?.html() ?: ""
                if (content.isBlank()) {
                    listOf(Text("Chapter content not available. Use WebView to load this chapter."))
                } else {
                    parseContentFromHtml(content)
                }
            }
        } catch (e: Exception) {
            Log.error { "Error fetching page list: ${e.message}" }
            listOf(Text("Error loading chapter content."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = com.fleeksoft.ksoup.Ksoup.parse(html)
        val contentDiv = doc.selectFirst(".protected-content")
        
        if (contentDiv != null) {
            // Extract text from <p> tags (novel paragraphs)
            val paragraphs = contentDiv.select("p").map { it.text() }.filter { it.isNotBlank() }
            if (paragraphs.isNotEmpty()) {
                return paragraphs.map { Text(it) }
            }
            
            // Fallback: get all text from the div
            val text = contentDiv.text()
            if (text.isNotBlank()) {
                return text.split("\n").filter { it.isNotBlank() }.map { Text(it) }
            }
        }
        
        // Last resort: strip all tags from full HTML
        val text = doc.body()?.text() ?: ""
        return text.split("\n").filter { it.isNotBlank() }.map { Text(it) }
    }
}
