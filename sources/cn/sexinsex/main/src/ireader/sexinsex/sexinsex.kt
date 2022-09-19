package ireader.sexinsex

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.ParsedHttpSource
import ireader.sourcefactory.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class sexinsex(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "sexinsex"

    override val id: Long
        get() = 39
    override val baseUrl = "http://154.84.5.213/"

    override val lang = "cn"

    fun getTypeIdFromGenreIndex(index: Int): String {
        return when (index) {
            1 -> "1326"
            2 -> "1327"
            3 -> "1328"
            4 -> "1329"
            5 -> "1330"
            6 -> "1331"
            7 -> "1332"
            8 -> "1333"
            9 -> "1334"
            10 -> "1336"
            11 -> "1337"
            12 -> "1338"
            13 -> "1339"
            14 -> "1340"
            15 -> "1341"
            16 -> "1342"
            17 -> "1343"
            18 -> "1344"
            19 -> "1345"
            20 -> "1347"
            21 -> "1335"
            22 -> "1346"
            23 -> "1371"
            24 -> "46"
            else -> ""
        }
    }

    override fun getFilters(): FilterList {
        return listOf(

            Filter.Sort(
                "分类",
                arrayOf(
                    "最新",
                    "玄学幻想",
                    "魔法奇幻",
                    "修真仙侠",
                    "侠骨柔情",
                    "科幻未来",
                    "同人衍生",
                    "穿越架空",
                    "催眠控制",
                    "历史古香",
                    "都市情缘",
                    "青葱校园",
                    "乡土田园",
                    "唯美纯爱",
                    "绿意盎然",
                    "凌辱虐情",
                    "禁忌之恋",
                    "星梦奇缘",
                    "玉足恋物",
                    "秀色冰恋",
                    "强奸迷奸",
                    "军事战争",
                    "另类小众",
                    "女警英雌",
                    "其它",
                )
            )
        )
    }

    private fun buildUrl(page: Int, genre: Int): String {
        if (genre == 0) {
            return baseUrl + "luntan/forum-383-$page.html"
        }
        var type_id = getTypeIdFromGenreIndex(genre)
        return baseUrl + "luntan/forumdisplay.php?fid=383&filter=type&typeid=$type_id&page=$page"
    }

    override fun getListings(): List<Listing> {
        return listOf(
            SourceFactory.LatestListing(),

        )
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page, 0)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value!!.index

        return getLatest(page, sorts)
    }

    suspend fun getLatest(page: Int, genreIndex: Int): MangasPageInfo {
        var url = buildUrl(page, genreIndex)
        val res = requestBuilder(url)
        return bookListParse(
            client.get(res).asJsoup(),
            latestSelector(),
            latestNextPageSelector()
        ) { latestFromElement(it) }
    }

    suspend fun getSearch(query: String, page: Int): MangasPageInfo {
        throw Error("not supported")
    }

    fun fetchLatestEndpoint(page: Int): String? =
        "luntan/forum-383-$page.html"

    fun fetchPopularEndpoint(page: Int): String? =
        "luntan/forumdisplay.php?fid=383&filter=digest&page=$page"

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
            )
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }

    fun popularSelector() =
        ".Item:matches((玄学幻想|魔法奇幻|修真仙侠|侠骨柔情|科幻未来|同人衍生|穿越架空|催眠控制|历史古香|都市情缘|青葱校园|乡土田园|唯美纯爱|绿意盎然|凌辱虐情|禁忌之恋|星梦奇缘|玉足恋物|秀色冰恋|强奸迷奸|其它|军事战争|另类小众|女警英雌))"

    fun popularFromElement(element: Element): MangaInfo {
        var linkEl = element.select(".Title a[href*=thread]")
        val url = baseUrl + "luntan/" + linkEl.attr("href")
        val title = linkEl.text()
        val thumbnailUrl = ""
        var author = element.select("a[href^=space.php]").text()
        return MangaInfo(key = url, title = title, cover = thumbnailUrl, author = author)
    }

    fun popularNextPageSelector() = ".next"
    fun searchFromElement(element: Element): MangaInfo {
        val url = baseUrl + element.select(".ix-list-img-square a").attr("href")
        val title = element.select("h3.nowrap").text()
        val thumbnailUrl = element.select(".ix-list-img-square a img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    // latest

    fun latestRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchLatestEndpoint(page)!!)
            headers { headers }
        }
    }

    fun latestSelector(): String = popularSelector()

    fun latestFromElement(element: Element): MangaInfo = popularFromElement(element)

    fun latestNextPageSelector() = popularNextPageSelector()

    fun searchNextPageSelector(): String? = popularNextPageSelector()

    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select(".mainbox.viewthread h1").text()
        val authorBookSelector =
            document.select(".mainbox.viewthread .postauthor cite a").text()

        val cover = ""
        val description = ""

        val category = document.select(".mainbox.viewthread h1 a")
            .text()
        val genres: ArrayList<String> = ArrayList<String>(1)
        genres.add(category)

        val status = MangaInfo.COMPLETED

        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector ?: "",
            genres = genres,
            key = "",
            status = status
        )
    }

    // chapters
    override fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }

    override fun chaptersSelector() = "body"

    override fun chapterFromElement(element: Element): ChapterInfo {
        var link = element.baseUri()
        return ChapterInfo(name = "第一章", key = link)
    }

    private fun uniqueChaptersRequest(book: MangaInfo, page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(
                book.key + "?page=$page"
            )
            headers { headers }
        }
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val request = client.get(chaptersRequest(manga)).asJsoup()
        return chaptersParse(request)
    }

    suspend fun parseMaxPage(book: MangaInfo): Int {
        val page = client.get(chaptersRequest(book = book)).asJsoup()
        val maxPage = page.select("li.last > a").attr("data-page")
        return maxPage.toInt()
    }

    override fun pageContentParse(document: Document): List<String> {
        return document.select(".t_msgfont.noSelect").html().split("<br>", "<p>")
            .map { Jsoup.parse(it).text() }
    }

    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get(contentRequest(chapter)).asJsoup())
    }

    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }
}
