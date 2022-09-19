package ireader.novelhall

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.ParsedHttpSource
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class NovelHall(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "NovelHall"

    override val id: Long
        get() = 27
    override val baseUrl = "https://www.novelhall.com"

    override val lang = "en"

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

    override fun getListings(): List<Listing> {
        return listOf(
            SourceFactory.LatestListing(),

        )
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return getSearch(query, page)
        }
        return when (sorts) {
            0 -> getLatest(page)
            1 -> getPopular(page)
            else -> getLatest(page)
        }
    }

    suspend fun getLatest(page: Int): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchLatestEndpoint(page))
        return bookListParse(
            client.get(res).asJsoup(),
            latestSelector(),
            latestNextPageSelector()
        ) { latestFromElement(it) }
    }

    suspend fun getPopular(page: Int): MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchPopularEndpoint(page))
        return bookListParse(
            client.get(res).asJsoup(),
            popularSelector(),
            popularNextPageSelector()
        ) { popularFromElement(it) }
    }

    suspend fun getSearch(query: String, page: Int): MangasPageInfo {
        val res = requestBuilder("$baseUrl/index.php?s=so&module=book&keyword=$query")
        return bookListParse(
            client.get(res).asJsoup(),
            ".section3 table tr",
            null
        ) { searchFromElement(it) }
    }

    fun fetchLatestEndpoint(page: Int): String? =
        "/all-$page.html"

    fun fetchPopularEndpoint(page: Int): String? =
        "/all-$page.html"

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

    fun popularSelector() = ".type li.btm"

    fun popularFromElement(element: Element): MangaInfo {
        val url = baseUrl + element.select(".btm a").attr("href")
        val title = element.select("li.btm a").text()
        val thumbnailUrl = baseUrl + element.select(".col-xs-3 img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun popularNextPageSelector() = ".container"
    fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("tr > td:nth-child(2) > a").text()
        val url = baseUrl + element.select("tr > td:nth-child(2) > a").attr("href")
        val thumbnailUrl = element.select("div.pic img").attr("src")
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
        val title = document.select(".book-info h1").text()
        val authorBookSelector =
            document.select(".book-info .blue:nth-child(2)").text().replace("Author：", "")
                .replace(".p", "").replace("0", "")
        val cover = document.select(".book-img.hidden-xs img").attr("src")
        val description = document.select("span.js-close-wrap").eachText().joinToString("\n")
            .replace("back<<", "")
        // not sure why its not working.
        val category = document.select("a.red")
            .eachText()

        val status = document.select(".book-info .blue:nth-child(3)")
            .next()
            .text()
            .replace("Status：Active", "OnGoing")
            .replace("Status：", "")
            .handleStatus()

        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector ?: "",
            genres = category,
            key = "",
            status = status
        )
    }

    private fun String.handleStatus(): Long {
        return when (this) {
            "OnGoing" -> MangaInfo.ONGOING
            "Complete" -> MangaInfo.COMPLETED
            else -> MangaInfo.ONGOING
        }
    }

    // chapters
    override fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }

    override fun chaptersSelector() = "#morelist a"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href")
        val name = element.select("a").text()

        return ChapterInfo(name = name, key = link)
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
        return document.select("div#htmlContent").html().split("<br>", "<p>")
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
