package ireader.syosetu

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
abstract class Syosetu(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "jp"
    override val baseUrl: String get() = "https://yomou.syosetu.com"
    private val novelPrefix: String get() = "https://ncode.syosetu.com"
    override val id: Long get() = 89L
    override val name: String get() = "Syosetu"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Ranked by",
            arrayOf("日間", "週間", "月間", "四半期", "年間", "累計")
        ),
        Filter.Select(
            "Modifier",
            arrayOf("すべて", "連載中", "完結済", "短編")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val rankingValues = arrayOf("daily", "weekly", "monthly", "quarter", "yearly", "total")
    private val modifierValues = arrayOf("total", "r", "er", "t")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/rank/list/type/total_total/?p=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            val searchUrl = "$baseUrl/search.php?order=hyoka&p=$page&word=${query.encodeURLParameter()}"
            val document = client.get(requestBuilder(searchUrl)).asJsoup()
            return parseSearchResults(document)
        }

        val sortFilter = filters.findInstance<Filter.Sort>()?.value?.index ?: 5
        val selectFilters = filters.filterIsInstance<Filter.Select>()
        val modifierIndex = selectFilters.firstOrNull()?.value ?: 0

        val ranking = rankingValues.getOrElse(sortFilter) { "total" }
        val modifier = modifierValues.getOrElse(modifierIndex) { "total" }

        val url = "$baseUrl/rank/list/type/${ranking}_$modifier/?p=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val currentPage = document.selectFirst(".is-current")?.text()?.toIntOrNull() ?: 1
        
        val novels = document.select(".c-card").mapNotNull { element ->
            val anchor = element.selectFirst(".p-ranklist-item__title a")
            val url = anchor?.attr("href")
            val name = anchor?.text()?.trim()

            if (url != null && !name.isNullOrBlank()) {
                MangaInfo(
                    key = url.replace(novelPrefix, ""),
                    title = name,
                    cover = "",
                )
            } else null
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    private fun parseSearchResults(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select(".searchkekka_box").mapNotNull { element ->
            val novelDiv = element.selectFirst(".novel_h")
            val novelA = novelDiv?.selectFirst("a")
            val novelPath = novelA?.attr("href")?.replace(novelPrefix, "")
            val novelName = novelDiv?.text()?.trim()

            if (novelPath != null && !novelName.isNullOrBlank()) {
                MangaInfo(
                    key = novelPath,
                    title = novelName,
                    cover = "",
                )
            } else null
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$novelPrefix${manga.key}")).asJsoup()
        }

        val title = document.selectFirst(".p-novel__title")?.text()?.trim() ?: manga.title
        val author = document.selectFirst(".p-novel__author")?.text()
            ?.replace("作者：", "")?.trim() ?: ""
        val summary = document.selectFirst("#novel_ex")?.html() ?: ""
        val genres = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?.split(" ")?.joinToString(",") ?: ""

        val announceText = document.selectFirst(".c-announce")?.text() ?: ""
        val status = when {
            announceText.contains("連載中") || announceText.contains("未完結") -> MangaInfo.ONGOING
            announceText.contains("更新されていません") -> MangaInfo.ON_HIATUS
            announceText.contains("完結") -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            author = author,
            description = summary.replace(Regex("<[^>]+>"), ""),
            genres = genres.split(",").map { it.trim() }.filter { it.isNotBlank() },
            status = status,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$novelPrefix${manga.key}")).asJsoup()
        }

        val chapters = mutableListOf<ChapterInfo>()

        // Check for pagination
        val lastPageLink = document.selectFirst(".c-pager__item--last")?.attr("href")

        if (lastPageLink == null) {
            // No pagination, parse chapters from current page
            document.select(".p-eplist__sublist").forEach { element ->
                val chapterLink = element.selectFirst("a")
                val chapterUrl = chapterLink?.attr("href")
                val chapterName = chapterLink?.text()?.trim()
                val releaseDate = element.selectFirst(".p-eplist__update")?.text()?.trim()
                    ?.split(" ")?.firstOrNull()?.replace("/", "-") ?: ""

                if (chapterUrl != null && !chapterName.isNullOrBlank()) {
                    chapters.add(ChapterInfo(
                        name = chapterName,
                        key = chapterUrl.replace(novelPrefix, ""),
                    ))
                }
            }
        } else {
            // Has pagination, fetch all pages
            val lastPageMatch = Regex("""\?p=(\d+)""").find(lastPageLink)
            val totalPages = lastPageMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

            for (pageNum in 1..totalPages) {
                val pageDoc = if (pageNum == 1) {
                    document
                } else {
                    client.get(requestBuilder("$novelPrefix${manga.key}?p=$pageNum")).asJsoup()
                }

                pageDoc.select(".p-eplist__sublist").forEach { element ->
                    val chapterLink = element.selectFirst("a")
                    val chapterUrl = chapterLink?.attr("href")
                    val chapterName = chapterLink?.text()?.trim()
                    val releaseDate = element.selectFirst(".p-eplist__update")?.text()?.trim()
                        ?.split(" ")?.firstOrNull()?.replace("/", "-") ?: ""

                    if (chapterUrl != null && !chapterName.isNullOrBlank()) {
                        chapters.add(ChapterInfo(
                            name = chapterName,
                            key = chapterUrl.replace(novelPrefix, ""),
                        ))
                    }
                }
            }
        }

        return chapters.mapIndexed { index, chapter ->
            chapter.copy(number = (index + 1).toFloat())
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$novelPrefix${chapter.key}")).asJsoup()
        }

        val chapterTitle = document.selectFirst(".p-novel__title")?.html() ?: ""
        val chapterContent = document.selectFirst(".p-novel__body .p-novel__text:not([class*='p-novel__text--'])")?.html() ?: ""

        val fullContent = "<h1>$chapterTitle</h1>$chapterContent"

        return fullContent.split("\n", "</p>", "<p>", "<br>", "<br/>", "<br />")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/rank/list/type/total_total/?p={page}",
                selector = ".c-card",
                nameSelector = ".p-ranklist-item__title a",
                linkSelector = ".p-ranklist-item__title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".p-novel__title",
            coverSelector = "img",
            coverAtt = "src",
            descriptionSelector = "#novel_ex",
            authorBookSelector = ".p-novel__author",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".p-eplist__sublist",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".p-novel__body .p-novel__text p",
        )
}
