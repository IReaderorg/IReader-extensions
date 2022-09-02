package ireader.aixdzs

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.ParsedHttpSource
import ireader.sourcefactory.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.Command
import org.ireader.core_api.source.model.Filter
import org.ireader.core_api.source.model.FilterList
import org.ireader.core_api.source.model.Listing
import org.ireader.core_api.source.model.MangaInfo
import org.ireader.core_api.source.model.MangasPageInfo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class Aixdzs(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "Aixdzs"

    override val id: Long
        get() = 33
    override val baseUrl = "https://m.aixdzs.com"

    override val lang = "cn"

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
        val res = requestBuilder("$baseUrl/search?k=$query")
        return bookListParse(
            client.get(res).asJsoup(),
            ".ix-list.ix-border-t li",
            null
        ) { searchFromElement(it) }
    }

    fun fetchLatestEndpoint(page: Int): String? =
        "/new/?page=$page"

    fun fetchPopularEndpoint(page: Int): String? =
        "/hot/?page=$page"

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

    fun popularSelector() = ".ix-list.ix-border-t li"

    fun popularFromElement(element: Element): MangaInfo {
        val url = baseUrl + element.select(".ix-list-img-square a").attr("href")
        val title = element.select("h3.nowrap").text()
        val thumbnailUrl = element.select(".ix-list-img-square a img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun popularNextPageSelector() = ".icon-arrow-r"
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
        val title = document.select(".ix-header.ix-border.ix-page h1").text()
        val authorBookSelector =
            document.select("p.ix-nowrap:nth-child(1)").text().replace("作者:", "")

        val cover = document.select(".ix-list-img-square img").attr("src")
        val description = document.select("#intro font").eachText().joinToString("\n")

        val category = document.select("p.ix-nowrap:first-child a")
            .eachText()

        val status = document.select("p.ix-nowrap:first-child span)")
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

    private fun String.handleStatus(): Int {
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

    override fun chaptersSelector() = "ul.chapter li"

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
        return document.select("article.page-content").html().split("<br>", "<p>")
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
