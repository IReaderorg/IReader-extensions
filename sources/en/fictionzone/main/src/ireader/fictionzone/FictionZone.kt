package ireader.fictionzone

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
abstract class FictionZone(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://fictionzone.net"
    override val id: Long get() = 91L
    override val name: String get() = "Fiction Zone"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val cdnUrl = "https://cdn.fictionzone.net"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By",
            arrayOf("Bookmarks", "Latest")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // API request helper
    private suspend fun getData(path: String): JsonObject {
        val requestBody = buildJsonObject {
            put("path", path)
            putJsonArray("headers") {
                addJsonArray {
                    add("content-type")
                    add("application/json")
                }
                addJsonArray {
                    add("x-request-time")
                    add(System.currentTimeMillis().toString())
                }
            }
            put("method", "GET")
        }

        val response = client.post("$baseUrl/api/__api_party/fictionzone") {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }.bodyAsText()

        return json.parseToJsonElement(response).jsonObject
    }


    // Build cover URL
    private fun buildCoverUrl(image: String): String {
        return "$cdnUrl/insecure/rs:fill:165:250/$image.webp"
    }

    // Parse novels from API response
    private fun parseNovelsFromApi(data: JsonObject): List<MangaInfo> {
        val dataObj = data["data"]?.jsonObject ?: return emptyList()
        val novels = dataObj["novels"]?.jsonArray ?: return emptyList()

        return novels.mapNotNull { novelElement ->
            val novel = novelElement.jsonObject
            val title = novel["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val slug = novel["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val image = novel["image"]?.jsonPrimitive?.contentOrNull ?: ""

            MangaInfo(
                key = "novel/$slug",
                title = title,
                cover = buildCoverUrl(image),
            )
        }
    }

    // Popular novels
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val data = getData("/platform/browse?page=$page&page_size=20&sort_by=bookmark_count&sort_order=desc&include_genres=true")
        val novels = parseNovelsFromApi(data)
        return MangasPageInfo(novels, novels.size >= 20)
    }

    // Search and filtered list
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val sortIndex = sortFilter?.value?.index ?: 0

        val sortBy = when (sortIndex) {
            0 -> "bookmark_count"
            1 -> "created_at"
            else -> "bookmark_count"
        }

        val path = if (!query.isNullOrBlank()) {
            "/platform/browse?search=${query.encodeURLParameter()}&page=$page&page_size=20&search_in_synopsis=true&sort_by=$sortBy&sort_order=desc&include_genres=true"
        } else {
            "/platform/browse?page=$page&page_size=20&sort_by=$sortBy&sort_order=desc&include_genres=true"
        }

        val data = getData(path)
        val novels = parseNovelsFromApi(data)
        return MangasPageInfo(novels, novels.size >= 20)
    }

    // Novel details
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        if (detailFetch != null && detailFetch.html.isNotBlank()) {
            return parseDetailsFromHtml(manga, detailFetch.html.asJsoup())
        }

        val novelSlug = manga.key.removePrefix("novel/")
        val data = getData("/platform/novel-details?slug=$novelSlug")
        val novelData = data["data"]?.jsonObject ?: return manga

        val title = novelData["title"]?.jsonPrimitive?.contentOrNull ?: manga.title
        val image = novelData["image"]?.jsonPrimitive?.contentOrNull ?: ""
        val synopsis = novelData["synopsis"]?.jsonPrimitive?.contentOrNull ?: ""
        val statusCode = novelData["status"]?.jsonPrimitive?.intOrNull

        // Parse genres and tags
        val genres = mutableListOf<String>()
        novelData["genres"]?.jsonArray?.forEach { g ->
            g.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.let { genres.add(it) }
        }
        novelData["tags"]?.jsonArray?.forEach { t ->
            t.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.let { genres.add(it) }
        }

        // Parse author from contributors
        var author = ""
        novelData["contributors"]?.jsonArray?.forEach { c ->
            val contributor = c.jsonObject
            if (contributor["role"]?.jsonPrimitive?.contentOrNull == "author") {
                author = contributor["display_name"]?.jsonPrimitive?.contentOrNull ?: ""
            }
        }

        val status = when (statusCode) {
            1 -> MangaInfo.ONGOING
            0 -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            cover = buildCoverUrl(image),
            author = author,
            description = synopsis,
            genres = genres,
            status = status,
        )
    }

    private fun parseDetailsFromHtml(manga: MangaInfo, document: com.fleeksoft.ksoup.nodes.Document): MangaInfo {
        val title = document.selectFirst("h1")?.text()?.trim() ?: manga.title
        val cover = document.selectFirst("img[src*=cdn.fictionzone]")?.attr("src") ?: manga.cover
        val description = document.selectFirst(".synopsis, .summary")?.text()?.trim() ?: ""

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
        )
    }


    // Fetch chapters via API
    private suspend fun getChaptersFromApi(novelId: String, novelPath: String): List<ChapterInfo> {
        val data = getData("/platform/chapter-lists?novel_id=$novelId")
        val chaptersData = data["data"]?.jsonObject?.get("chapters")?.jsonArray ?: return emptyList()

        return chaptersData.mapNotNull { chapterElement ->
            val chapter = chapterElement.jsonObject
            val title = chapter["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val chapterId = chapter["chapter_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val chapterNumber = chapter["chapter_number"]?.jsonPrimitive?.floatOrNull ?: 0f

            // Store both the display path and the API path for content fetching
            val key = "$novelPath/$chapterId|/platform/chapter-content?novel_id=$novelId&chapter_id=$chapterId"

            ChapterInfo(
                name = title,
                key = key,
                number = chapterNumber,
            )
        }
    }

    // Chapter list
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup(), manga.key)
        }

        // Get novel details to extract ID
        val novelSlug = manga.key.removePrefix("novel/")
        val data = getData("/platform/novel-details?slug=$novelSlug")
        val novelId = data["data"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: return emptyList()

        return getChaptersFromApi(novelId, manga.key)
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document, novelPath: String): List<ChapterInfo> {
        return document.select("a[href*=/chapter/], a.chapter").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href,
            )
        }
    }

    // Chapter content
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        if (contentFetch != null && contentFetch.html.isNotBlank()) {
            return parseContentFromHtml(contentFetch.html.asJsoup())
        }

        // Extract API path from chapter key
        val apiPath = chapter.key.split("|").getOrNull(1)
        if (apiPath.isNullOrBlank()) {
            return listOf(Text("Failed to get chapter content. Please read in WebView."))
        }

        val data = getData(apiPath)
        val content = data["data"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull ?: ""

        // Format content with paragraph tags
        return content.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private fun parseContentFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<Page> {
        return document.select(".chapter-content p, article p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    // Declarative fetchers as fallback
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/library?page={page}",
                selector = "div.novel-card",
                nameSelector = "h1, .title",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "img[src*=cdn.fictionzone]",
            coverAtt = "src",
            descriptionSelector = ".synopsis, .summary",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a.chapter",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".chapter-content p",
        )
}
