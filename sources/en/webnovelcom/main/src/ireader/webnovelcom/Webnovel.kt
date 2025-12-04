package ireader.webnovelcom

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ireader.core.source.Dependencies
import ireader.core.source.ParsedHttpSource
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import tachiyomix.annotations.Extension
import ireader.common.utils.DateParser

@Extension
abstract class Webnovel(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "Webnovel.com"

    override val baseUrl = "https://www.webnovel.com"

    override val lang = "en"

    override val id: Long = 16

    override fun getUserAgent() = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
        )
    }
    class LatestListing() : Listing("Latest")

    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            Filter.Sort(
                "Sort By:",
                arrayOf(
                    "Latest Novel",
                    "Latest Fanfic",
                    "Popular Novel",
                    "Popular Fanfic",
                )
            ),
        )
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatestNovel(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return getSearch(page, query)
        }
        return when (sorts) {
            0 -> getLatestNovel(page)
            1 -> getLatestFanfic(page)
            2 -> getPopularNovel(page)
            3 -> getPopularFanfic(page)
            else -> getLatestNovel(page)
        }
    }

    private suspend fun getLatestNovel(page: Int): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchLatestNovelEndpoint(page))
        val html = client.get(res)
        return bookListParse(html.asJsoup(), latestSelector(), latestNextPageSelector()) { latestFromElement(it) }
    }
    private suspend fun getLatestFanfic(page: Int): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchLatestFanficEndpoint(page))
        val html = client.get(res)
        return bookListParse(html.asJsoup(), latestSelector(), latestNextPageSelector()) { latestFromElement(it) }
    }
    private suspend fun getPopularNovel(page: Int): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchPopularNovelEndpoint(page))
        return bookListParse(client.get(res).asJsoup(), popularSelector(), popularNextPageSelector()) { popularFromElement(it) }
    }
    private suspend fun getPopularFanfic(page: Int): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchPopularFanficEndpoint(page))
        return bookListParse(client.get(res).asJsoup(), popularSelector(), popularNextPageSelector()) { popularFromElement(it) }
    }
    private suspend fun getSearch(page: Int, query: String): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchSearchEndpoint(page, query))
        return bookListParse(client.get(res).asJsoup(), searchSelector(), searchNextPageSelector()) { searchFromElement(it) }
    }

    private fun fetchLatestNovelEndpoint(page: Int): String = "/stories/novel?pageIndex=$page&orderBy=5"

    private fun fetchLatestFanficEndpoint(page: Int): String = "/stories/fanfic?pageIndex=$page&orderBy=5"

    private fun fetchPopularNovelEndpoint(page: Int): String = "/stories/novel?pageIndex=$page&orderBy=1"

    private fun fetchPopularFanficEndpoint(page: Int): String = "/stories/fanfic?pageIndex=$page&orderBy=1"

    private fun fetchSearchEndpoint(page: Int, query: String): String {
        var type = "novel"
        var keywords = query
        if (query.startsWith("fanfic")) {
            type = "fanfic"
            keywords = query.removePrefix("fanfic")
        }
        keywords = keywords.replace(' ', '+')
        return "/search?keywords=${keywords}&type=$type&pageIndex=$page"
    }

    private fun popularSelector() = "div.j_category_wrapper li.fl a.g_thumb"

    private fun popularFromElement(element: Element): MangaInfo {
        val url = baseUrl + element.attr("href")
        val title = element.attr("title")
        val thumbnailUrl = "https:" + element.select("img").attr("data-original")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    private fun popularNextPageSelector() = "body"

    private fun latestSelector(): String = popularSelector()

    private fun latestFromElement(element: Element) = popularFromElement(element)

    private fun latestNextPageSelector() = popularNextPageSelector()

    private fun searchSelector() = "li a.g_thumb"

    private fun searchFromElement(element: Element): MangaInfo {
        val url = baseUrl + element.attr("href")
        val title = element.attr("title")
        val thumbnailUrl = "https:" + element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    private fun searchNextPageSelector() = popularNextPageSelector()

    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val thumbnailUrl = "http:" + document.select("i.g_thumb img:first-child").attr("src")
        val title = document.select("div.g_col h2").text()
        val description = document.select("div.g_txt_over p.c_000").text()
        val author = document.select("p.ell a.c_primary").text()

        return MangaInfo(
            title = title,
            description = description,
            cover = thumbnailUrl,
            author = author,
            key = ""
        )
    }

    // chapters
    override fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key + "/catalog")
            headers { headers }
        }
    }

    override fun chaptersSelector() = ".volume-item li a"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val key = baseUrl + element.attr("href")
        val name = if (element.select("svg").hasAttr("class")) {
            "\uD83D\uDD12 "
        } else {
            ""
        } +
            element.attr("title")
        val dateUpload = parseChapterDate(element.select(".oh small").text())

        return ChapterInfo(name = name, dateUpload = dateUpload, key = key)
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {

                val request = client.get(chaptersRequest(book = manga)).asJsoup()

                return@withContext chaptersParse(request)
            }
        }.getOrThrow()
    }

    override fun pageContentParse(document: Document): List<String> {
        val title: List<String> = listOf(document.select("div.cha-tit").text())
        val content: List<String> = document.select("div.cha-content p").eachText()
        return title + content
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

    private fun parseChapterDate(date: String): Long {
        return DateParser.parseRelativeOrAbsoluteDate(date)
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
