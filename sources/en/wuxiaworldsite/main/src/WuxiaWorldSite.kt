package ireader.wuxiaworldsite

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import org.ireader.core.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.core.http.okhttp
import tachiyomi.source.Dependencies
import tachiyomi.source.model.*
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.*

@Extension
abstract class WuxiaWorld(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "WuxiaWorld.site"
    override val id: Long
        get() = 2499283573021320255

    override val baseUrl = "https://wuxiaworld.site"

    override val lang = "en"

    override fun getFilters(): FilterList {
        return listOf(
                Filter.Title(),
                Filter.Sort(
                        "Sort By:",arrayOf(
                        "Latest",
                        "Popular"
                )),
        )
    }

    override fun getListings(): List<Listing> {
        return listOf(LatestListing())
    }

    override val client: HttpClient
        get() = HttpClient(OkHttp) {
            engine {
                preconfigured = deps.httpClients.default.okhttp
            }
        }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return getSearch(query,page)
        }
        return when(sorts) {
            0 -> getLatest(page)
            1 -> getPopular(page)
            else -> getLatest(page)
        }
    }

    suspend fun getLatest(page: Int) : MangasPageInfo {
        val res = requestBuilder("$baseUrl/novel-list/page/$page/")
        return bookListParse(client.get<HttpResponse>(res).asJsoup(),"div.page-item-detail",popularNextPageSelector()) { latestFromElement(it) }
    }

    suspend fun getPopular(page: Int) : MangasPageInfo {
        val res = requestBuilder("$baseUrl/novel-list/page/$page/?m_orderby=views")
        return bookListParse(client.get<HttpResponse>(res).asJsoup(), "div.page-item-detail",popularNextPageSelector()) { popularFromElement(it) }
    }
    suspend fun getSearch(query: String,page: Int) : MangasPageInfo {
        val res = requestBuilder("$baseUrl/?s=$query&post_type=wp-manga&op=&author=&artist=&release=&adult=")
        return bookListParse(client.get<HttpResponse>(res).asJsoup(), "div.c-tabs-item__content",null) { searchFromElement(it) }
    }


    override fun HttpRequestBuilder.headersBuilder() {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }



    fun popularFromElement(element: Element): MangaInfo {
        val title = element.select("h3.h5 a").text()
        val url = element.select("h3.h5 a").attr("href")
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun popularNextPageSelector() = "div.nav-previous>a"



    fun latestFromElement(element: Element): MangaInfo {
        val title = element.select("h3.h5 a").text()
        val url = element.select("h3.h5 a").attr("href")
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }


    fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("div.post-title h3.h4 a").text()
        val url = element.select("div.post-title h3.h4 a").attr("href")
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }


    // manga details

    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("div.post-title>h1").text()
        val cover = document.select("div.summary_image a img").attr("src")
        val link = baseUrl + document.select("div.cur div.wp a:nth-child(5)").attr("href")
        val authorBookSelector = document.select("div.author-content>a").attr("title")
        val description =
            document.select("div.description-summary div.summary__content p").eachText()
                .joinToString("\n\n")
        val category = document.select("div.genres-content a").eachText()
        val rating = document.select("div.post-rating span.score").text()
        val status = document.select("div.post-status div.summary-content").text()


        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector,
            genres = category,
            key = link,
            status = parseStatus(status)
        )
    }

    private fun parseStatus(string: String): Int {
        return when {
            "OnGoing" in string -> MangaInfo.ONGOING
            "Completed" in string -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }
    }

    private fun paresRating(string: String): Int {
        return when {
            "1" in string -> 1
            "2" in string -> 2
            "3" in string -> 3
            "4" in string -> 4
            "5" in string -> 5
            else -> {
                0
            }
        }
    }

    // chapters
    override fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }


    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").text()
        val dateUploaded = element.select("i").text()

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

    override fun chaptersSelector(): String? {
        return "li.wp-manga-chapter"
    }
    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {
                var chapters =
                    chaptersParse(
                        client.post<HttpResponse>(requestBuilder(manga.key + "ajax/chapters/")).asJsoup(),
                    )
                if (chapters.isEmpty()) {
                    chapters = chaptersParse(client.post<Document>(requestBuilder(manga.key)))
                }
                return@withContext chapters.reversed()
            }
        }.getOrThrow()
    }


    override fun pageContentParse(document: Document): List<String> {
         val par = document.select("div.read-container .reading-content p").eachText()
         val head = document.select("div.read-container .reading-content h3").eachText()

        return head + par
    }


    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get<HttpResponse>(contentRequest(chapter)).asJsoup())
    }


    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }

}