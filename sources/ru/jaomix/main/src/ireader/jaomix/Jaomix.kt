package ireader.jaomix

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import tachiyomix.annotations.Extension

@Extension
abstract class Jaomix(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ru"
    override val baseUrl: String get() = "https://jaomix.ru"
    override val id: Long get() = 82L
    override val name: String get() = "Jaomix"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Сортировка",
            arrayOf("Топ недели", "По алфавиту", "По дате обновления", "По дате создания", "По просмотрам")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val sortValues = arrayOf("topweek", "alphabet", "upd", "new", "count")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/?searchrn&sortby=topweek&gpage=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val sortIndex = sortFilter?.value?.index ?: 0
        val sortBy = sortValues.getOrElse(sortIndex) { "topweek" }

        val url = if (!query.isNullOrBlank()) {
            "$baseUrl/?searchrn=${query.encodeURLParameter()}&but=Поиск по названию&sortby=upd&gpage=$page"
        } else {
            "$baseUrl/?searchrn&sortby=$sortBy&gpage=$page"
        }

        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("div.block-home > div.one").mapNotNull { element ->
            val linkElement = element.selectFirst("div.img-home > a") ?: return@mapNotNull null
            val name = linkElement.attr("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = linkElement.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cover = element.selectFirst("div.img-home > a > img")?.attr("src")
                ?.replace("-150x150", "") ?: ""

            MangaInfo(
                key = href.removePrefix(baseUrl),
                title = name,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }


    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("div.desc-book > h1")?.text()?.trim() ?: manga.title
        val cover = document.selectFirst("div.img-book > img")?.attr("src") ?: manga.cover
        val description = document.selectFirst("div#desc-tab")?.text()?.trim() ?: ""

        var author = ""
        var genres = listOf<String>()
        var status = MangaInfo.UNKNOWN

        document.select("#info-book > p").forEach { p ->
            val text = p.text().replace(",", "").split(" ")
            when (text.firstOrNull()) {
                "Автор:" -> author = text.drop(1).joinToString(" ")
                "Жанры:" -> genres = text.drop(1)
                "Статус:" -> status = if (text.contains("продолжается")) MangaInfo.ONGOING else MangaInfo.COMPLETED
            }
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
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        val totalChapters = document.select("div.title").size

        return document.select("div.title").mapIndexedNotNull { index, element ->
            val linkElement = element.selectFirst("a") ?: return@mapIndexedNotNull null
            val name = linkElement.attr("title").takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val href = linkElement.attr("href").takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null

            ChapterInfo(
                name = name,
                key = href.removePrefix(baseUrl),
                number = (totalChapters - index).toFloat(),
            )
        }.reversed()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${chapter.key}")).asJsoup()
        }

        document.select("div.adblock-service").remove()
        val content = document.selectFirst("div.entry-content")?.html() ?: ""

        // Remove links but keep text
        val cleanedContent = content.replace(Regex("<a[^>]*>(.*?)</a>"), "$1")

        return cleanedContent.split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/?searchrn&sortby=topweek&gpage={page}",
                selector = "div.block-home > div.one",
                nameSelector = "div.img-home > a",
                nameAtt = "title",
                coverSelector = "div.img-home > a > img",
                coverAtt = "src",
                linkSelector = "div.img-home > a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.desc-book > h1",
            coverSelector = "div.img-book > img",
            coverAtt = "src",
            descriptionSelector = "div#desc-tab",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.title",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.entry-content p",
        )
}
