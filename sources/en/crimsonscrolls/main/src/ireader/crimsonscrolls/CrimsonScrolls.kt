package ireader.crimsonscrolls

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension

@Extension
abstract class CrimsonScrolls(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://crimsonscrolls.net"
    override val id: Long get() = 81L
    override val name: String get() = "Crimson Scrolls"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // Query the WordPress AJAX API
    private suspend fun queryAPI(action: String, params: Map<String, String>): com.fleeksoft.ksoup.nodes.Document {
        val formData = buildString {
            append("action=$action")
            params.forEach { (key, value) -> append("&$key=${value.encodeURLParameter()}") }
        }

        val response = client.post("$baseUrl/wp-admin/admin-ajax.php") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(formData)
        }.bodyAsText()

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val html = jsonObj["html"]?.jsonPrimitive?.contentOrNull ?: ""
        return "<html>$html</html>".asJsoup()
    }


    // Parse novels from API response HTML
    private fun parseNovels(document: com.fleeksoft.ksoup.nodes.Document): List<MangaInfo> {
        return document.select("a.live-search-item, div.novel-list-card").mapNotNull { element ->
            val name = element.selectFirst("div.live-search-title, h3.novel-title")?.text()?.trim()
                ?.split(" ")?.filter { it.isNotBlank() }?.joinToString(" ")
                ?: return@mapNotNull null

            val cover = element.selectFirst("img.live-search-cover, div.novel-cover img")?.attr("src") ?: ""

            val href = element.selectFirst("a")?.attr("href") ?: element.attr("href")
            if (href.isBlank()) return@mapNotNull null

            // Extract novel path (slug) from URL
            val path = href.replace(baseUrl, "").split("/").getOrNull(2) ?: return@mapNotNull null

            MangaInfo(
                key = path,
                title = name,
                cover = cover,
            )
        }
    }

    // Popular novels via AJAX
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val document = queryAPI("load_novels", mapOf("page" to page.toString()))
        val novels = parseNovels(document)
        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    // Search and filtered list
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        return if (!query.isNullOrBlank()) {
            val document = queryAPI("live_novel_search", mapOf("query" to query))
            val novels = parseNovels(document)
            MangasPageInfo(novels, false)
        } else {
            getMangaList(null, page)
        }
    }

    // Novel details
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/novel/${manga.key}")).asJsoup()
        }

        val novelInfo = document.selectFirst("#single-novel-content-wrapper")

        val title = novelInfo?.selectFirst("h1.chapter-title")?.text()?.trim() ?: manga.title
        val cover = novelInfo?.selectFirst(".single-novel-cover > img")?.attr("data-src") ?: manga.cover
        val description = novelInfo?.selectFirst("#synopsis-full")?.text()?.trim() ?: ""

        // Extract author from meta
        var author = ""
        var genres = ""

        novelInfo?.select(".single-novel-meta strong")?.forEach { strong ->
            val label = strong.text().lowercase()
            val value = strong.nextSibling()?.toString()?.trim() ?: ""

            when {
                label.contains("author") -> author = value
                label.contains("genre") -> genres = value.split(",").map { it.trim() }.joinToString(", ")
            }
        }

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            description = description,
            genres = genres.split(", ").filter { it.isNotBlank() },
            status = MangaInfo.UNKNOWN,
        )
    }


    // Fetch chapters via REST API with pagination
    private suspend fun fetchChaptersFromApi(novelId: String, page: Int = 1): List<JsonObject> {
        val url = "$baseUrl/wp-json/cs/v1/novels/$novelId/chapters?per_page=75&order=asc&page=$page"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject

        val items = jsonObj["items"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        val currentPage = jsonObj["page"]?.jsonPrimitive?.intOrNull ?: page
        val totalPages = jsonObj["total_pages"]?.jsonPrimitive?.intOrNull ?: 1

        return if (currentPage < totalPages) {
            items + fetchChaptersFromApi(novelId, currentPage + 1)
        } else {
            items
        }
    }

    // Chapter list
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup())
        }

        // Get novel page to extract novel ID
        val document = client.get(requestBuilder("$baseUrl/novel/${manga.key}")).asJsoup()
        val novelId = document.selectFirst("#chapter-list")?.attr("data-novel") ?: ""

        if (novelId.isBlank()) {
            return parseChaptersFromHtml(document)
        }

        // Fetch chapters via API
        val chaptersData = fetchChaptersFromApi(novelId)

        return chaptersData.mapIndexedNotNull { index, chapter ->
            val title = chapter["title"]?.jsonPrimitive?.contentOrNull ?: "Chapter ${index + 1}"
            val url = chapter["url"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val isLocked = chapter["locked"]?.jsonPrimitive?.booleanOrNull == true

            // Extract chapter path from URL
            val chapterPath = url.replace(baseUrl, "").split("/").getOrNull(2) ?: return@mapIndexedNotNull null

            ChapterInfo(
                name = if (isLocked) "🔒 $title" else title,
                key = chapterPath,
                number = (index + 1).toFloat(),
            )
        }
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<ChapterInfo> {
        return document.select("a[href*=/chapter/]").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: "Chapter"
            val chapterPath = href.replace(baseUrl, "").split("/").getOrNull(2) ?: return@mapNotNull null

            ChapterInfo(
                name = name,
                key = chapterPath,
            )
        }
    }

    // Chapter content
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        if (contentFetch != null && contentFetch.html.isNotBlank()) {
            return parseContentFromHtml(contentFetch.html.asJsoup())
        }

        val chapterUrl = "$baseUrl/chapter/${chapter.key}"
        val document = client.get(requestBuilder(chapterUrl)).asJsoup()

        // Remove attribution elements
        document.select("#chapter-display hr.cs-attrib-divider:last-of-type").remove()
        document.select("#chapter-display div.cs-attrib:last-of-type").remove()
        document.select("#chapter-display p.cs-chapter-attrib:last-of-type").remove()

        val content = document.selectFirst("#chapter-display")?.html() ?: ""

        return content.split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private fun parseContentFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<Page> {
        return document.select("#chapter-display p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    // Declarative fetchers as fallback
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/",
                selector = "div.novel-list-card",
                nameSelector = "h3.novel-title",
                coverSelector = "div.novel-cover img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.chapter-title",
            coverSelector = ".single-novel-cover > img",
            coverAtt = "data-src",
            descriptionSelector = "#synopsis-full",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a[href*=/chapter/]",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#chapter-display p",
        )
}
