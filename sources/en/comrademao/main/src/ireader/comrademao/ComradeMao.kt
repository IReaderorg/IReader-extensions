package ireader.comrademao

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.ireader.core_api.http.okhttp
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Extension
abstract class ComradeMao(private val deps: Dependencies) : HttpSource(deps) {


    override val name = "Comrademao"


    override val id: Long
        get() = 9999999998
    override val baseUrl = "https://comrademao.com"

    override val lang = "en"


    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            sorts,
        )
    }

    val sorts = Filter.Sort(
        "Sort By:", arrayOf(
            "Chinese",
            "Japanese",
            "Korean",
        )
    )

    class Chinese : Listing("Chinese")
    class Japanese : Listing("Japanese")
    class Korean : Listing("Korean")

    override fun getListings(): List<Listing> {
        return listOf(
            Chinese(),
        )
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sort = filters.findInstance<Filter.Sort>()?.value

        if (query != null && query.isNotBlank()) {
            return getSearch(query, filters, page)
        } else {
            return getNovels(page, sort = sort)
        }

    }

    suspend fun getSearch(query: String, filters: FilterList, page: Int): MangasPageInfo {
        val request = client.get(searchRequest(page, query, filters)).asJsoup()
        return searchParse(request)
    }

    fun fromSearchElement(element: Element): MangaInfo {
        val name = element.select("h3 a").text()
        val img = element.select("a.imgbox img").attr("src")
        val url = element.select("h3 a").attr("href")

        return MangaInfo(title = name, cover = img, key = url)
    }

    open fun searchParse(document: Document): MangasPageInfo {
        val books = document.select(".newbox ul li").map { element ->
            fromSearchElement(element)
        }

        return MangasPageInfo(books, false)
    }

    override val client: HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = clientBuilder()
        }
    }

    fun selectors() = ".listupd .bsx"
    fun nextPageSelector() = "div.pagination a:nth-child(2)"

    fun fromElement(element: Element): MangaInfo {
        val name = element.select("a").text()
        val img = element.select("img").attr("src")
        val url = element.select("a").attr("href")

        return MangaInfo(title = name, cover = img, key = url)
    }

    private fun clientBuilder(): OkHttpClient = deps.httpClients.default.okhttp
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun headersBuilder() = Headers.Builder().apply {
        add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
        )
        add("cache-control", "max-age=0")
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getNovels(page, sort = sorts.value)
    }

    private suspend fun getNovels(page: Int, sort: Filter.Sort.Selection?): MangasPageInfo {
        val req = when (sort?.index) {
            0 -> HttpRequestBuilder().apply {
                url("$baseUrl/mtype/chinese/page/$page/")
                headers { headers }
            }
            1 -> HttpRequestBuilder().apply {
                url("$baseUrl/mtype/japanese/page/$page/")
                headers { headers }
            }
            2 -> HttpRequestBuilder().apply {
                url("$baseUrl/mtype/korean/page/$page/")
                headers { headers }
            }
            else -> HttpRequestBuilder().apply {
                url("$baseUrl/mtype/chinese/page/$page/")
                headers { headers }
            }
        }
        val request = client.get(req).asJsoup()

        return novelsParse(request)
    }

    private fun novelsParse(document: Document): MangasPageInfo {
        val books = document.select(selectors()).map { element ->
            fromElement(element)
        }

        val hasNextPage = document.select(nextPageSelector()).text() == "Next »"

        return MangasPageInfo(books, hasNextPage)
    }

    fun detailRequest(manga: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(manga.key)
            headers { headers }
        }
    }

    fun searchRequest(
        page: Int,
        query: String,
        filters: List<Filter<*>>,
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url("$baseUrl/?s=$query&post_type=novel")
            headers { headers }
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return novelParsing(client.get(detailRequest(manga)).asJsoup())
    }

    private fun novelParsing(document: Document): MangaInfo {
        val name =
            document.select(".entry-title")
                .text().trim()
        val img = document.select(".thumb img").attr("src")
        val summary = document.select(".wd-full").last()?.text()?.trim()
        val genre = document.select(".wd-full").next().next().next().select("a").eachText()
            .filter { !it.contains("Genre") }
        val author = document.select(".wd-full").first()?.text()
        val status = document.select(".wd-full").next().text()
        val fStatus = when (status) {
            "OnGoing" -> MangaInfo.ONGOING
            "Complete" -> MangaInfo.COMPLETED
            else -> MangaInfo.ONGOING
        }


        return MangaInfo(
            title = name,
            cover = img,
            key = "",
            description = summary?:"",
            genres = genre,
            author = author ?: "",
            status = fStatus
        )
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {

        val body: HttpResponse = client.get(Url(manga.key))
        return chaptersParse(body.asJsoup()).reversed()
    }


    private fun uniqueChaptersRequest(book: MangaInfo, page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(
                book.key + "page/$page/"
            )
            headers { headers }
        }
    }

    private fun chaptersParse(document: Document): List<ChapterInfo> {
        return document.select(chaptersSelector()).map { chapterFromElement(it) }
    }



    fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }

    fun chaptersSelector() = ".chbox a"

    fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.select("a").attr("href")
        val name = element.select("a .chapternum").text()
        val dateUploaded = element.select("a .chapterdate").text()

        return ChapterInfo(name = name, key = link, dateUpload = parseChapterDate(dateUploaded))
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

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM dd,yyyy", Locale.US)

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return getContents(chapter).map { Text(it) }
    }

    private suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get(contentRequest(chapter)).asJsoup())
    }

    fun pageContentParse(document: Document): List<String> {
        val header = document.select("h3").text()
        val body = document.select("#chaptercontent p").eachText()
        return listOf(header) + body
    }

    fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }

}