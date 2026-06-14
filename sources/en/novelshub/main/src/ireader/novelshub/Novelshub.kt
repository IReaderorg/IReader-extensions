package ireader.novelshub

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
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
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular", "Top RatedA", "Name")),
        Filter.Select("Type", arrayOf("All", "Web Novel", "Light Novel", "Manhwa", "Manga", "Manhua")),
        Filter.Select("Status", arrayOf("All", "Ongoing", "Completed", "Hiatus", "Cancelled")),
        Filter.Select("Genre", arrayOf(
            "All", "Action", "Adventure", "Comedy", "Drama", "Fantasy",
            "Harem", "Historical", "Horror", "Isekai", "Martial Arts",
            "Mature", "Mystery", "Psychological", "Romance", "Sci-Fi",
            "Seinen", "Slice of Life", "Supernatural", "Tragedy"
        )),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    private suspend fun <T> retryBrowser(
        times: Int = 2,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(times) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.error { "Browser attempt ${attempt + 1} failed: ${e.message}" }
            }
        }
        throw lastException ?: Exception("All browser attempts failed")
    }

    private suspend fun fetchBrowser(
        url: String,
        selector: String? = null,
        timeout: Long = 50000
    ): String? {
        return try {
            retryBrowser {
                val result = deps.httpClients.browser.fetch(
                    url = url,
                    selector = selector,
                    timeout = timeout
                )
                if (result.isSuccess && result.responseBody.isNotBlank()) {
                    result.responseBody
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.error { "Browser fetch failed for $url: ${e.message}" }
            null
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/series?page=$page&limit=20&sort=latest"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val meta = json["meta"]?.jsonObject
            val hasMore = meta?.get("hasMore")?.jsonPrimitive?.boolean ?: false

            MangasPageInfo(
                data.map { parseMangaFromJson(it.jsonObject) },
                hasMore
            )
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val selectFilters = filters.filterIsInstance<Filter.Select>()

        // Use correct search endpoint when title filter is active
        val searchQuery = titleFilter?.value?.takeIf { it.isNotBlank() }
        if (searchQuery != null) {
            return searchByTitle(searchQuery, page)
        }

        val queryParams = mutableListOf("page=$page", "limit=20")

        sortFilter?.value?.index?.let { index ->
            when (index) {
                0 -> queryParams.add("sort=latest")
                1 -> queryParams.add("sort=popular")
                2 -> queryParams.add("sort=rating")
                3 -> queryParams.add("sort=name")
            }
        }

        selectFilters.getOrNull(0)?.value?.let { index ->
            if (index > 0) {
                val types = listOf("WEB_NOVEL", "LIGHT_NOVEL", "MANHWA", "MANGA", "MANHUA")
                if (index <= types.size) {
                    queryParams.add("type=${types[index - 1]}")
                }
            }
        }

        selectFilters.getOrNull(1)?.value?.let { index ->
            if (index > 0) {
                val statuses = listOf("ONGOING", "COMPLETED", "ON_HIATUS", "CANCELLED")
                if (index <= statuses.size) {
                    queryParams.add("status=${statuses[index - 1]}")
                }
            }
        }

        selectFilters.getOrNull(2)?.value?.let { index ->
            if (index > 0) {
                val genres = listOf(
                    "action", "adventure", "comedy", "drama", "fantasy",
                    "harem", "historical", "horror", "isekai", "martial-arts",
                    "mature", "mystery", "psychological", "romance", "sci-fi",
                    "seinen", "slice-of-life", "supernatural", "tragedy"
                )
                if (index <= genres.size) {
                    queryParams.add("genre=${genres[index - 1]}")
                }
            }
        }

        return try {
            val url = "$baseUrl/api/series?${queryParams.joinToString("&")}"
            val response = client.get(requestBuilder(url))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val meta = json["meta"]?.jsonObject
            val hasMore = meta?.get("hasMore")?.jsonPrimitive?.boolean ?: false

            MangasPageInfo(
                data.map { parseMangaFromJson(it.jsonObject) },
                hasMore
            )
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private suspend fun searchByTitle(query: String, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/search?q=$query&limit=20"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val series = json["series"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)

            val mangaList = series.map { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
                val coverImage = obj["coverImage"]?.jsonPrimitive?.content ?: ""
                val fullCover = if (coverImage.startsWith("http")) coverImage else "$baseUrl$coverImage"
                MangaInfo(key = "$baseUrl/series/novel/$slug", title = title, cover = fullCover)
            }

            // Search endpoint doesn't support pagination, return all results
            MangasPageInfo(mangaList, false)
        } catch (e: Exception) {
            Log.error { "Error searching: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private fun parseMangaFromJson(obj: JsonObject): MangaInfo {
        val title = obj["title"]?.jsonPrimitive?.content ?: ""
        val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
        val coverImage = obj["coverImage"]?.jsonPrimitive?.content ?: ""
        val fullCover = if (coverImage.startsWith("http")) coverImage else "$baseUrl$coverImage"
        return MangaInfo(key = "$baseUrl/series/novel/$slug", title = title, cover = fullCover)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        // Check for pre-fetched HTML from WebView
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                return parseDetailsFromHtml(cmd.html, manga)
            }
        }

        // Always use browser engine - plain HTTP returns "JavaScript Required"
        val html = fetchBrowser(url = manga.key, selector = "h1")
            ?: return manga  // Keep original title/cover from API list

        return parseDetailsFromHtml(html, manga)
    }

    private fun parseDetailsFromHtml(html: String, manga: MangaInfo): MangaInfo {
        val doc = com.fleeksoft.ksoup.Ksoup.parse(html)

        // Only overwrite title if scraped one is valid
        val scrapedTitle = doc.selectFirst("h1")?.text()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
        val title = if (!scrapedTitle.isNullOrBlank() &&
                        !scrapedTitle.contains("JavaScript", ignoreCase = true) &&
                        !scrapedTitle.contains("Loading", ignoreCase = true) &&
                        !scrapedTitle.contains("Just a moment", ignoreCase = true) &&
                        !scrapedTitle.contains("noveldex.io", ignoreCase = true) &&
                        !scrapedTitle.contains("Chapter Not Found", ignoreCase = true)) {
            scrapedTitle
        } else {
            manga.title
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

        // Check for pre-fetched HTML from WebView
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                return parseChaptersFromHtml(cmd.html, slug)
            }
        }

        // Use browser to get full chapter list
        val html = fetchBrowser(url = manga.key, selector = "h1")
            ?: return emptyList()

        // Try RSC payload first (has all chapters as JSON)
        val rscChapters = extractChaptersFromRscPayload(html, slug)
        if (rscChapters.isNotEmpty()) {
            return rscChapters
        }

        // Fallback: parse chapter links from rendered HTML
        return parseChaptersFromHtml(html, slug)
    }

    private fun extractChaptersFromRscPayload(html: String, slug: String): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()

        val rscPattern = Regex("""self\.__next_f\.push\(\[1,"(.*?)"\]\)""", RegexOption.DOT_MATCHES_ALL)
        val rscMatches = rscPattern.findAll(html)

        for (match in rscMatches) {
            val payload = match.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")

            val allChaptersPattern = Regex(""""allChapters":\s*(\[.*?\])\s*,\s*"prevChapter"""", RegexOption.DOT_MATCHES_ALL)
            val allChaptersMatch = allChaptersPattern.find(payload)

            if (allChaptersMatch != null) {
                try {
                    val chaptersJson = Json.parseToJsonElement(allChaptersMatch.groupValues[1]).jsonArray
                    for (chapterElement in chaptersJson) {
                        val chapterObj = chapterElement.jsonObject
                        val number = chapterObj["number"]?.jsonPrimitive?.int ?: continue
                        val title = chapterObj["title"]?.jsonPrimitive?.content ?: ""
                        val isLocked = chapterObj["isLocked"]?.jsonPrimitive?.boolean ?: true
                        val isFree = chapterObj["isFree"]?.jsonPrimitive?.boolean ?: false

                        // Skip locked chapters
                        if (isLocked && !isFree) continue

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

        return chapters.sortedBy { chapterNumber(it.key) }
    }

    private fun parseChaptersFromHtml(html: String, slug: String): List<ChapterInfo> {
        val doc = com.fleeksoft.ksoup.Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()

        doc.select("a[href*='/chapter/']").forEach { link ->
            val href = link.attr("href")
            val linkText = link.text().trim()

            // Skip non-chapter links
            if (linkText.contains("Start Reading", ignoreCase = true) ||
                linkText.contains("Begin Reading", ignoreCase = true) ||
                linkText.isBlank()) {
                return@forEach
            }

            val chapterMatch = Regex("/chapter/(\\d+)").find(href)
            if (chapterMatch != null) {
                val chapterNumber = chapterMatch.groupValues[1].toIntOrNull() ?: return@forEach
                chapters.add(
                    ChapterInfo(
                        name = linkText.ifBlank { "Chapter $chapterNumber" },
                        key = "$baseUrl/series/novel/$slug/chapter/$chapterNumber",
                    )
                )
            }
        }

        return chapters.distinctBy { it.key }.sortedBy { chapterNumber(it.key) }
    }

    private fun chapterNumber(key: String): Int {
        return Regex("/chapter/(\\d+)").find(key)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        // Check for pre-fetched HTML from WebView
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                return parseContentFromHtml(cmd.html)
            }
        }

        // Use browser engine - plain HTTP returns no content
        val html = fetchBrowser(url = chapter.key, selector = ".protected-content")
            ?: return listOf(Text("Chapter content not available."))

        return parseContentFromHtml(html)
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = com.fleeksoft.ksoup.Ksoup.parse(html)
        val contentDiv = doc.selectFirst(".protected-content")

        if (contentDiv != null) {
            val paragraphs = contentDiv.select("p").map { it.text() }.filter { it.isNotBlank() }
            if (paragraphs.isNotEmpty()) {
                return paragraphs.map { Text(it) }
            }

            val text = contentDiv.text()
            if (text.isNotBlank()) {
                return text.split("\n").filter { it.isNotBlank() }.map { Text(it) }
            }
        }

        val text = doc.body()?.text() ?: ""
        return text.split("\n").filter { it.isNotBlank() }.map { Text(it) }
    }
}
