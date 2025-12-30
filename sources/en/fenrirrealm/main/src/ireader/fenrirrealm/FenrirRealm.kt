package ireader.fenrirrealm

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
abstract class FenrirRealm(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://fenrirealm.com"
    override val id: Long get() = 84L
    override val name: String get() = "Fenrir Realm"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By",
            arrayOf("Popular", "Latest", "Updated")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val sortValues = arrayOf("popular", "latest", "updated")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return fetchNovelsFromApi(page, "popular", null)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val sortIndex = sortFilter?.value?.index ?: 0
        val sortBy = sortValues.getOrElse(sortIndex) { "popular" }

        return if (!query.isNullOrBlank()) {
            fetchNovelsFromApi(page, sortBy, query)
        } else {
            fetchNovelsFromApi(page, sortBy, null)
        }
    }

    private suspend fun fetchNovelsFromApi(page: Int, sort: String, search: String?): MangasPageInfo {
        val url = buildString {
            append("$baseUrl/api/series/filter?page=$page&per_page=20&status=any&order=$sort")
            if (!search.isNullOrBlank()) {
                append("&search=${search.encodeURLParameter()}")
            }
        }

        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        val data = jsonObj["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)

        val novels = data.mapNotNull { element ->
            val novel = element.jsonObject
            val title = novel["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val slug = novel["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cover = novel["cover"]?.jsonPrimitive?.contentOrNull ?: ""

            MangaInfo(
                key = slug,
                title = title,
                cover = if (cover.isNotBlank()) "$baseUrl/$cover" else "",
            )
        }

        return MangasPageInfo(novels, novels.size >= 20)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/series/${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("h1.my-2")?.text()?.trim() ?: manga.title
        val description = document.select("div.overflow-hidden.transition-all p")
            .joinToString("\n\n") { it.text() }
        val author = document.selectFirst("div.flex-1 > div.mb-3 > a.inline-flex")?.text()?.trim() ?: ""
        val genres = document.select("div.flex-1 > div.flex:not(.mb-3, .mt-5) > a")
            .map { it.text().trim() }
        val statusText = document.selectFirst("div.flex-1 > div.mb-3 > span.rounded-md")?.text()?.trim() ?: ""

        // Extract cover from HTML
        val rawHtml = client.get(requestBuilder("$baseUrl/series/${manga.key}")).bodyAsText()
        val coverMatch = Regex(""",cover:"storage/(.+?)",cover_data_url""").find(rawHtml)
        val cover = coverMatch?.let { "$baseUrl/storage/${it.groupValues[1]}" } ?: manga.cover

        val status = when (statusText.lowercase()) {
            "ongoing" -> MangaInfo.ONGOING
            "completed" -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            description = description,
            genres = genres,
            status = status,
        )
    }


    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup(), manga.key)
        }

        val response = client.get(requestBuilder("$baseUrl/api/novels/chapter-list/${manga.key}")).bodyAsText()
        val chaptersArray = json.parseToJsonElement(response).jsonArray

        return chaptersArray.mapNotNull { element ->
            val chapter = element.jsonObject
            val title = chapter["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val number = chapter["number"]?.jsonPrimitive?.intOrNull ?: 0
            val isLocked = chapter["locked"]?.jsonObject?.get("price")?.jsonPrimitive?.intOrNull != null

            val groupSlug = chapter["group"]?.jsonObject?.get("slug")?.jsonPrimitive?.contentOrNull
            val groupIndex = chapter["group"]?.jsonObject?.get("index")?.jsonPrimitive?.intOrNull

            val chapterPath = buildString {
                append(manga.key)
                if (groupSlug != null) append("/$groupSlug")
                append("/chapter-$number")
            }

            val chapterName = buildString {
                if (isLocked) append("🔒 ")
                if (groupIndex != null) append("Vol $groupIndex ")
                append("Chapter $number")
                if (title.isNotBlank() && !title.equals("Chapter $number", ignoreCase = true)) {
                    val cleanTitle = title.replace(Regex("^chapter [0-9]+ . ", RegexOption.IGNORE_CASE), "")
                    append(" - $cleanTitle")
                }
            }

            ChapterInfo(
                name = chapterName,
                key = chapterPath,
                number = number.toFloat() + (groupIndex ?: 0) * 1000000000000f,
            )
        }.sortedBy { it.number }
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document, novelSlug: String): List<ChapterInfo> {
        return document.select("a[href*=/chapter-]").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href.removePrefix("$baseUrl/series/"),
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/series/${chapter.key}")).asJsoup()
        }

        // Remove comments
        val content = document.selectFirst("[id^=reader-area-]")
        content?.select("*")?.forEach { el ->
            el.childNodes().filter { it.nodeName() == "#comment" }.forEach { it.remove() }
        }

        val html = content?.html() ?: ""

        return html.split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/series",
                selector = "a[href*=/series/]",
                nameSelector = "h3",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.my-2",
            coverSelector = "img",
            coverAtt = "src",
            authorBookSelector = "div.flex-1 > div.mb-3 > a.inline-flex",
            descriptionSelector = "div.overflow-hidden.transition-all p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a[href*=/chapter-]",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "[id^=reader-area-] p",
        )
}
