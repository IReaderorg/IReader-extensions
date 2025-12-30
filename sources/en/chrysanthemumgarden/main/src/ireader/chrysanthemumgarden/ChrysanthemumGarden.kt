package ireader.chrysanthemumgarden

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
abstract class ChrysanthemumGarden(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://chrysanthemumgarden.com"
    override val id: Long get() = 88L
    override val name: String get() = "Chrysanthemum Garden"

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

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = if (page == 1) "$baseUrl/books/" else "$baseUrl/books/page/$page/"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank() && page == 1) {
            return searchNovels(query)
        }

        return getMangaList(null, page)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("article").mapNotNull { element ->
            // Skip manhua
            if (element.selectFirst("div.series-genres > a")?.text()?.contains("Manhua") == true) {
                return@mapNotNull null
            }

            val linkElement = element.selectFirst("h2.novel-title > a") ?: return@mapNotNull null
            val href = linkElement.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = linkElement.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cover = element.selectFirst("div.novel-cover > img")?.attr("data-breeze") ?: ""

            MangaInfo(
                key = href.removePrefix(baseUrl).removeSuffix("/").removePrefix("/"),
                title = name,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    private suspend fun searchNovels(searchTerm: String): MangasPageInfo {
        val response = client.get(requestBuilder("$baseUrl/wp-json/cg/novels")).bodyAsText()
        val novelsArray = json.parseToJsonElement(response).jsonArray

        val novels = novelsArray.mapNotNull { element ->
            val novel = element.jsonObject
            val name = novel["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val link = novel["link"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            if (!name.lowercase().contains(searchTerm.lowercase())) return@mapNotNull null

            MangaInfo(
                key = link.removePrefix(baseUrl).removeSuffix("/").removePrefix("/"),
                title = name,
                cover = "",
            )
        }

        return MangasPageInfo(novels, false)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/${manga.key}/")).asJsoup()
        }

        document.selectFirst("h1.novel-title > span.novel-raw-title")?.remove()

        val title = document.selectFirst("h1.novel-title")?.text()?.trim() ?: manga.title
        val cover = document.selectFirst("div.novel-cover > img")?.attr("data-breeze") ?: manga.cover
        val description = document.select("div.entry-content > p")
            .joinToString("\n\n") { it.text() }

        val novelInfoHtml = document.selectFirst("div.novel-info")?.html() ?: ""
        val author = Regex("""Author:\s*([^<]*)<br>""").find(novelInfoHtml)?.groupValues?.get(1)?.trim() ?: ""

        val genres = mutableListOf<String>()
        document.select("div.series-genres > a").forEach { genres.add(it.text().trim()) }
        document.select("a.series-tag").forEach {
            genres.add(it.text().split("(").first().trim())
        }

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            description = description,
            genres = genres,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/${manga.key}/")).asJsoup()
        }

        return document.select("div.chapter-item > a").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href.removePrefix(baseUrl).removeSuffix("/").removePrefix("/"),
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/${chapter.key}/")).asJsoup()
        }

        val content = document.selectFirst("div#novel-content")?.html() ?: ""

        return content.split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/books/",
                selector = "article",
                nameSelector = "h2.novel-title > a",
                coverSelector = "div.novel-cover > img",
                coverAtt = "data-breeze",
                linkSelector = "h2.novel-title > a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.novel-title",
            coverSelector = "div.novel-cover > img",
            coverAtt = "data-breeze",
            descriptionSelector = "div.entry-content > p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.chapter-item > a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div#novel-content",
        )
}
