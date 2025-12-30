package ireader.mvlempyr

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.doubleOrNull
import tachiyomix.annotations.Extension

@Extension
abstract class Mvlempyr(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://www.mvlempyr.io"
    override val id: Long get() = 79L
    override val name: String get() = "MVLEMPYR"

    private val chapSite = "https://chap.heliosarchive.online"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Cache for all novels
    private var allNovelsCache: List<NovelData>? = null

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Order By",
            arrayOf("Most Reviewed", "Best Rated", "Chapter Count", "Latest Added")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // Novel list fetching via API
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val sortIndex = sortFilter?.value?.index ?: 0

        val allNovels = getAllNovels()

        val filtered = if (!query.isNullOrBlank()) {
            allNovels.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            allNovels
        }

        val sorted = when (sortIndex) {
            0 -> filtered.sortedByDescending { it.reviewCount }
            1 -> filtered.sortedByDescending { it.avgReview }
            2 -> filtered.sortedByDescending { it.chapterCount }
            3 -> filtered.sortedByDescending { it.created }
            else -> filtered
        }

        val startIndex = (page - 1) * 20
        val endIndex = minOf(startIndex + 20, sorted.size)
        val pageNovels = if (startIndex < sorted.size) sorted.subList(startIndex, endIndex) else emptyList()

        val novels = pageNovels.map { novel ->
            MangaInfo(
                key = "/novel/${novel.slug}",
                title = novel.name,
                cover = novel.cover,
            )
        }

        return MangasPageInfo(novels, endIndex < sorted.size)
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getMangaList(emptyList(), page)
    }

    private suspend fun getAllNovels(): List<NovelData> {
        if (allNovelsCache != null) return allNovelsCache!!

        val url = "$chapSite/wp-json/wp/v2/mvl-novels?per_page=10000"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonArray = json.parseToJsonElement(response).jsonArray

        allNovelsCache = jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val novelCode = obj["novel-code"]?.jsonPrimitive?.contentOrNull ?: ""
            val avgReview = obj["average-review"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val reviewCount = obj["total-reviews"]?.jsonPrimitive?.intOrNull ?: 0
            val chapterCount = obj["total-chapters"]?.jsonPrimitive?.intOrNull ?: 0
            val createdOn = obj["createdOn"]?.jsonPrimitive?.contentOrNull ?: ""

            NovelData(
                name = name,
                slug = slug,
                cover = "https://assets.mvlempyr.app/images/600/$novelCode.webp",
                avgReview = avgReview,
                reviewCount = reviewCount,
                chapterCount = chapterCount,
                created = try { createdOn.toLongOrNull() ?: 0L } catch (e: Exception) { 0L }
            )
        }

        return allNovelsCache!!
    }

    // Novel detail parsing from HTML + API for chapters
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("h1.novel-title")?.text()?.trim() ?: manga.title
        val cover = document.selectFirst("img.novel-image")?.attr("src") ?: manga.cover
        val description = document.selectFirst("div.synopsis.w-richtext")?.text()?.trim() ?: ""
        val status = document.selectFirst(".novelstatustextlarge")?.text()?.trim() ?: ""

        // Parse author from additionalinfo
        val author = document.select("div.additionalinfo.tm10 > div.textwrapper")
            .find { it.selectFirst("div")?.text()?.contains("Author:") == true }
            ?.select("div")?.getOrNull(1)?.text()?.trim() ?: ""

        val genres = document.select(".genre-tags").map { it.text().trim() }

        val mangaStatus = when {
            status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
            status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
            status.contains("Hiatus", ignoreCase = true) -> MangaInfo.ON_HIATUS
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            description = description,
            genres = genres,
            status = mangaStatus,
        )
    }

    // Chapter list fetching via WordPress API
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup())
        }

        // Get novel code from page
        val document = client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        val novelCode = document.selectFirst("#novel-code")?.text()?.trim() ?: return emptyList()

        // Convert novel ID using the algorithm from TypeScript
        val newNovelId = convertNovelId(novelCode.toLongOrNull() ?: return emptyList())

        // Fetch chapters from WordPress API
        val chapters = mutableListOf<ChapterInfo>()
        var page = 1

        while (true) {
            val url = "$chapSite/wp-json/wp/v2/posts?tags=$newNovelId&per_page=500&page=$page"
            val response = client.get(requestBuilder(url))

            val totalPages = response.headers["X-Wp-Totalpages"]?.toIntOrNull() ?: 1
            val jsonArray = json.parseToJsonElement(response.bodyAsText()).jsonArray

            jsonArray.forEach { element ->
                val obj = element.jsonObject
                val acf = obj["acf"]?.jsonObject ?: return@forEach
                val chName = acf["ch_name"]?.jsonPrimitive?.contentOrNull ?: "Chapter"
                val chapterNumber = acf["chapter_number"]?.jsonPrimitive?.contentOrNull ?: "0"
                val novelCodeAcf = acf["novel_code"]?.jsonPrimitive?.contentOrNull ?: ""
                val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: ""

                chapters.add(
                    ChapterInfo(
                        name = chName,
                        key = "/chapter/$novelCodeAcf-$chapterNumber",
                        number = chapterNumber.toFloatOrNull() ?: -1f,
                    )
                )
            }

            if (page >= totalPages) break
            page++
        }

        return chapters.reversed()
    }

    private fun convertNovelId(code: Long): Long {
        val t = 1999999997L
        var u = 1L
        var c = 7L % t
        var d = code

        while (d > 0) {
            if ((d and 1L) == 1L) {
                u = (u * c) % t
            }
            c = (c * c) % t
            d = d shr 1
        }

        return u
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<ChapterInfo> {
        return document.select("a[href*=/chapter/]").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href.removePrefix(baseUrl),
            )
        }
    }

    // Chapter content fetching
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        if (contentFetch != null && contentFetch.html.isNotBlank()) {
            return parseContentFromHtml(contentFetch.html.asJsoup())
        }

        val document = client.get(requestBuilder("$baseUrl${chapter.key}")).asJsoup()
        val content = document.selectFirst("#chapter > span")?.html() ?: ""

        return content.split("<br>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private fun parseContentFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<Page> {
        val content = document.selectFirst("#chapter > span, #chapter")
        return content?.select("p, br")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.map { Text(it) }
            ?: listOf(Text(document.body().text()))
    }

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append(HttpHeaders.Accept, "application/json, text/html, */*")
        }
    }

    // Declarative fetchers as fallback
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Browse",
                endpoint = "/browse",
                selector = "div.novel-card",
                nameSelector = "h1.novel-title",
                coverSelector = "img.novel-image",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.novel-title",
            coverSelector = "img.novel-image",
            coverAtt = "src",
            authorBookSelector = "div.textwrapper:contains(Author) div:last-child",
            descriptionSelector = "div.synopsis.w-richtext",
            categorySelector = ".genre-tags",
            statusSelector = ".novelstatustextlarge",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a[href*=/chapter/]",
            nameSelector = "span",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#chapter > span",
        )

    data class NovelData(
        val name: String,
        val slug: String,
        val cover: String,
        val avgReview: Double,
        val reviewCount: Int,
        val chapterCount: Int,
        val created: Long,
    )
}
