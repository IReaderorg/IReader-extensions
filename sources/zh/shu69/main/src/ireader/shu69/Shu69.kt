package ireader.shu69

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import tachiyomix.annotations.Extension

@Extension
abstract class Shu69(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "zh"
    override val baseUrl: String get() = "https://www.69shu.xyz"
    override val id: Long get() = 87L
    override val name: String get() = "69书吧"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "排行榜",
            arrayOf("总排行榜", "月排行榜", "周排行榜", "日排行榜", "收藏榜", "字数榜", "推荐榜", "新书榜", "更新榜")
        ),
        Filter.Select(
            "分类",
            arrayOf("无", "全部", "玄幻", "仙侠", "都市", "历史", "游戏", "科幻", "灵异", "言情", "其它")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val rankValues = arrayOf("allvisit", "monthvisit", "weekvisit", "dayvisit", "goodnum", "words", "allvote", "postdate", "lastupdate")
    private val sortValues = arrayOf("none", "all", "xuanhuan", "xianxia", "dushi", "lishi", "youxi", "kehuan", "kongbu", "nvsheng", "qita")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/rank/allvisit/$page.html"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            if (page > 1) return MangasPageInfo(emptyList(), false)
            return searchNovels(query)
        }

        val sortFilter = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val selectFilters = filters.filterIsInstance<Filter.Select>()
        val categoryIndex = selectFilters.firstOrNull()?.value ?: 0

        val rankValue = rankValues.getOrElse(sortFilter) { "allvisit" }
        val sortValue = sortValues.getOrElse(categoryIndex) { "none" }

        val url = if (sortValue == "none") {
            "$baseUrl/rank/$rankValue/$page.html"
        } else {
            "$baseUrl/sort/$sortValue/$page.html"
        }

        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private suspend fun searchNovels(searchTerm: String): MangasPageInfo {
        val response = client.submitForm(
            url = "$baseUrl/search",
            formParameters = Parameters.build {
                append("searchkey", searchTerm)
            }
        ).asJsoup()

        return parseNovelList(response)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("div.book-coverlist").mapNotNull { element ->
            val link = element.selectFirst("a.cover")
            val novelUrl = link?.attr("href")
            val novelName = element.selectFirst("h4.name")?.text()?.trim()
            val novelCover = element.selectFirst("a.cover > img")?.attr("src")

            if (novelUrl != null && !novelName.isNullOrBlank()) {
                MangaInfo(
                    key = novelUrl.replace(baseUrl, ""),
                    title = novelName,
                    cover = novelCover ?: "",
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
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("h1")?.text()?.trim() ?: manga.title
        val cover = document.selectFirst("div.cover > img")?.attr("src") ?: manga.cover
        val description = document.selectFirst("#bookIntro")?.text()?.trim() ?: ""
        val author = document.selectFirst("div.caption-bookinfo > p a")?.attr("title") ?: ""
        
        val bookInfo = document.selectFirst("div.caption-bookinfo > p")?.text() ?: ""
        val status = if (bookInfo.contains("连载")) MangaInfo.ONGOING else MangaInfo.COMPLETED

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
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

        val allUrl = document.selectFirst("dd.all > a")?.attr("href")
        
        val chapters = mutableListOf<ChapterInfo>()
        
        if (allUrl != null) {
            // Fetch chapters with pagination
            var currentUrl = if (allUrl.startsWith("http")) allUrl else "$baseUrl$allUrl"
            var hasMorePages = true

            while (hasMorePages) {
                val chaptersDoc = client.get(requestBuilder(currentUrl)).asJsoup()

                chaptersDoc.select("dl.panel-chapterlist dd").forEach { element ->
                    val chapterLink = element.selectFirst("a")
                    val chapterUrl = chapterLink?.attr("href")
                    val chapterName = chapterLink?.text()?.trim()

                    if (chapterUrl != null && !chapterName.isNullOrBlank()) {
                        val relativeUrl = if (chapterUrl.startsWith("http")) {
                            chapterUrl.replace(baseUrl, "")
                        } else {
                            chapterUrl
                        }
                        
                        if (!chapters.any { it.key == relativeUrl }) {
                            chapters.add(ChapterInfo(
                                name = chapterName,
                                key = relativeUrl,
                            ))
                        }
                    }
                }

                // Find next page
                val nextPageLink = chaptersDoc.select("div.listpage a.onclick")
                    .firstOrNull { it.text().contains("下一页") }
                    ?.attr("href")

                if (nextPageLink != null && nextPageLink != "javascript:void(0);") {
                    val nextUrl = if (nextPageLink.startsWith("http")) nextPageLink else "$baseUrl$nextPageLink"
                    if (nextUrl != currentUrl) {
                        currentUrl = nextUrl
                    } else {
                        hasMorePages = false
                    }
                } else {
                    hasMorePages = false
                }
            }
        } else {
            // Fallback: get chapters from main page
            document.select("div.panel.hidden-xs > dl.panel-chapterlist:nth-child(2) > dd").forEach { element ->
                val chapterLink = element.selectFirst("a")
                val chapterUrl = chapterLink?.attr("href")
                val chapterName = chapterLink?.text()?.trim()

                if (chapterUrl != null && !chapterName.isNullOrBlank()) {
                    val relativeUrl = if (chapterUrl.startsWith("http")) {
                        chapterUrl.replace(baseUrl, "")
                    } else {
                        chapterUrl
                    }
                    chapters.add(ChapterInfo(
                        name = chapterName,
                        key = relativeUrl,
                    ))
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
            val chapterUrl = if (chapter.key.startsWith("http")) chapter.key else "$baseUrl${chapter.key}"
            client.get(requestBuilder(chapterUrl)).asJsoup()
        }

        return document.select("#chaptercontent p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.contains("69书吧") }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/rank/allvisit/{page}.html",
                selector = "div.book-coverlist",
                nameSelector = "h4.name",
                linkSelector = "a.cover",
                linkAtt = "href",
                coverSelector = "a.cover > img",
                coverAtt = "src",
                addBaseUrlToLink = true,
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "div.cover > img",
            coverAtt = "src",
            descriptionSelector = "#bookIntro",
            authorBookSelector = "div.caption-bookinfo > p a",
            authorBookAtt = "title",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "dl.panel-chapterlist dd",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#chaptercontent p",
        )
}
