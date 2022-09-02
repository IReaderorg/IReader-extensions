package ireader.webnovelcom

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.ParsedHttpSource
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.Command
import org.ireader.core_api.source.model.Filter
import org.ireader.core_api.source.model.FilterList
import org.ireader.core_api.source.model.Listing
import org.ireader.core_api.source.model.MangaInfo
import org.ireader.core_api.source.model.MangasPageInfo
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Extension
abstract class Webnovel(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "Webnovel.com"

    override val baseUrl = "https://www.webnovel.com"

    override val lang = "en"

    override val id: Long = 16

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
                    "Latest",
                    "Popular"
                )
            ),
        )
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return getSearch(page, query)
        }
        return when (sorts) {
            0 -> getLatest(page)
            1 -> getPopular(page)
            else -> getLatest(page)
        }
    }

    suspend fun getLatest(page: Int): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchLatestEndpoint(page))
        return bookListParse(client.get(res).asJsoup(), latestSelector(), latestNextPageSelector()) { latestFromElement(it) }
    }
    suspend fun getPopular(page: Int): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchPopularEndpoint(page))
        return bookListParse(client.get(res).asJsoup(), popularSelector(), popularNextPageSelector()) { popularFromElement(it) }
    }
    suspend fun getSearch(page: Int, query: String): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchSearchEndpoint(page, query))
        return bookListParse(client.get(res).asJsoup(), searchSelector(), searchNextPageSelector()) { searchFromElement(it) }
    }

    fun fetchLatestEndpoint(page: Int): String? =
        "/stories/novel?pageIndex=$page&orderBy=5"

    fun fetchPopularEndpoint(page: Int): String? =
        "/stories/novel?pageIndex=$page&orderBy=1"

    fun fetchSearchEndpoint(page: Int, query: String): String? =
        "/search?keywords=$query?pageIndex=$page"

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM dd,yyyy", Locale.US)

    private fun headersBuilder() = io.ktor.http.Headers.build {
        append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
        append(HttpHeaders.CacheControl, "max-age=0")
        append(HttpHeaders.Referrer, baseUrl)
    }

    // popular
    fun popularRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchPopularEndpoint(page = page)!!)
        }
    }

    fun popularSelector() = "div.j_category_wrapper li.fl a.g_thumb"

    fun popularFromElement(element: Element): MangaInfo {
        val url = baseUrl + element.attr("href")
        val title = element.attr("title")
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun popularNextPageSelector() = "[rel=next]"

    // latest

    fun latestRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchLatestEndpoint(page)!!)
            headers { headers }
        }
    }

    fun latestSelector(): String = popularSelector()

    fun latestFromElement(element: Element) = popularFromElement(element)

    fun latestNextPageSelector() = popularNextPageSelector()

    // search
    fun searchRequest(
        page: Int,
        query: String,
        filters: List<Filter<*>>,
    ): HttpRequestBuilder {
        val filters = if (filters.isEmpty()) getFilters() else filters
        val genre = filters.findInstance<GenreList>()?.toUriPart()
        val order = filters.findInstance<OrderByFilter>()?.toUriPart()
        val status = filters.findInstance<StatusFilter>()?.toUriPart()

        return when {
            query.isNotEmpty() -> requestBuilder("$baseUrl/search?keywords=$query&type=1&pageIndex=$page")
            else -> requestBuilder("$baseUrl/category/$genre" + "_comic_page1?&orderBy=$order&bookStatus=$status")
        }
    }

    fun searchSelector() = popularSelector()

    fun searchFromElement(element: Element) = popularFromElement(element)

    fun searchNextPageSelector() = popularNextPageSelector()

    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val thumbnailUrl = document.select("i.g_thumb img:first-child").attr("src")
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
        val date_upload = parseChapterDate(element.select(".oh small").text())

        return ChapterInfo(name = name, dateUpload = date_upload, key = key)
    }

    override suspend fun getChapterList(
        book: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {

                val request = client.get(chaptersRequest(book = book)).asJsoup()

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

    fun parseChapterDate(date: String): Long {
        return if (date.contains("ago")) {
            val value = date.split(' ')[0].toInt()
            when {
                "min" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "hour" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "day" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "week" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "month" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "year" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                }.timeInMillis
                else -> {
                    0L
                }
            }
        } else {
            try {
                dateFormat.parse(date)?.time ?: 0
            } catch (_: Exception) {
                0L
            }
        }
    }

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("0", "All"),
            Pair("1", "Ongoing"),
            Pair("2", "Completed")
        )
    )

    private class OrderByFilter : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("1", "Default"),
            Pair("1", "Popular"),
            Pair("2", "Recommendation"),
            Pair("3", "Collection"),
            Pair("4", "Rates"),
            Pair("5", "Updated")
        )
    )

    private class GenreList : UriPartFilter(
        "Select Genre",
        arrayOf(
            Pair("0", "All"),
            Pair("60002", "Action"),
            Pair("60014", "Adventure"),
            Pair("60011", "Comedy"),
            Pair("60009", "Cooking"),
            Pair("60027", "Diabolical"),
            Pair("60024", "Drama"),
            Pair("60006", "Eastern"),
            Pair("60022", "Fantasy"),
            Pair("60017", "Harem"),
            Pair("60018", "History"),
            Pair("60015", "Horror"),
            Pair("60013", "Inspiring"),
            Pair("60029", "LGBT+"),
            Pair("60016", "Magic"),
            Pair("60008", "Mystery"),
            Pair("60003", "Romance"),
            Pair("60007", "School"),
            Pair("60004", "Sci-fi"),
            Pair("60019", "Slice of Life"),
            Pair("60023", "Sports"),
            Pair("60012", "Transmigration"),
            Pair("60005", "Urban"),
            Pair("60010", "Wuxia")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[value].first
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
