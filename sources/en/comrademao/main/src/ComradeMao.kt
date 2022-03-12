package ireader.comrademao

import com.tfowl.ktor.client.features.JsoupFeature
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.ireader.core.SearchListing
import org.ireader.core.findInstance
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.core.http.okhttp
import tachiyomi.source.Dependencies
import tachiyomi.source.HttpSource
import tachiyomi.source.model.*
import tachiyomix.annotations.Extension
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
    val sorts =  Filter.Sort(
            "Sort By:",arrayOf(
            "Chinese",
            "Japanese",
            "Korean",
    ))

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
             return  getSearch(query, filters, page)
        } else {
            return getNovels(page,sort= sort)
        }

    }

    suspend fun getSearch(query: String, filters: FilterList, page: Int): MangasPageInfo {
        val request = client.get<Document>(searchRequest(page, query, filters))
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

        return MangasPageInfo(books,false)
    }

    override val client: HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = clientBuilder()
        }
        install(JsoupFeature)
    }

    fun selectors() = ".columns"
    fun nextPageSelector() = "ul.pagination-list li:last-child"

    fun fromElement(element: Element): MangaInfo {
        val name = element.select("div.columns > div:nth-child(2) > a").text()
        val img = element.select("img").attr("src")
        val url = element.select("div.columns > div:nth-child(2) > a").attr("href")

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
        return getNovels(page,sort= sorts.value)
    }

    private suspend fun getNovels(page: Int,sort: Filter.Sort.Selection?): MangasPageInfo {
        val req = when(sort?.index) {
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
        val request = client.get<Document>(req)

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

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        return novelParsing(client.get<Document>(detailRequest(manga)))
    }

    private fun novelParsing(document: Document): MangaInfo {
        val name =
            document.select("#NovelInfo > div > div.column.is-one-third.has-text-centered > p")
                .text().trim()
        val img = document.select("img.attachment-post-thumbnail").attr("src")
        val summary = document.select("#NovelInfo > div > div:nth-child(2) > p").text().trim()
        val genre = document.select("NovelInfo > p:nth-child(2)").eachText()
            .filter { !it.contains("Genre") }
        val author = document.select("#NovelInfo > p:nth-child(4)").text()
        val status = document.select("#NovelInfo > p:nth-child(5)").text()
        val fStatus = when (status) {
            "OnGoing" -> MangaInfo.ONGOING
            "Complete" -> MangaInfo.COMPLETED
            else -> MangaInfo.ONGOING
        }


        return MangaInfo(
            title = name,
            cover = img,
            key = "",
            description = summary,
            genres = genre,
            author = author,
            status = fStatus
        )
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {
                val page = client.get<String>(chaptersRequest(book = manga))
                val maxPage = parseMaxPage(manga)
                val list = mutableListOf<Deferred<List<ChapterInfo>>>()
                for (i in 1..maxPage) {
                    val pChapters = async {
                        chaptersParse(
                            client.get<Document>(
                                uniqueChaptersRequest(
                                    book = manga,
                                    page = i
                                )
                            )
                        )
                    }
                    list.addAll(listOf(pChapters))
                }
                //  val request = client.get<String>(chaptersRequest(book = book))

                return@withContext list.awaitAll().flatten().reversed()
            }
        }.getOrThrow()
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

    suspend fun parseMaxPage(book: MangaInfo): Int {
        val page = client.get<Document>(chaptersRequest(book = book))
        val maxPage = page.select(".pagination-list li:last-child").prev().text()
        return maxPage.toInt()
    }

    fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }

    fun chaptersSelector() = "table > tbody tr"

    fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.select("a").attr("href")
        val name = element.select("a").text()
        val dateUploaded = element.select("span").text()

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

    override suspend fun getPageList(chapter: ChapterInfo): List<Page> {
        return getContents(chapter).map { Text(it) }
    }

    private suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get<Document>(contentRequest(chapter)))
    }

    fun pageContentParse(document: Document): List<String> {
        return document.select("#content p").eachText()
    }

    fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }

}