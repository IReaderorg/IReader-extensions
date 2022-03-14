package ireader.realwebnovel

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
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
abstract class RealWebNovel(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "RealWebNovel"
    override val id: Long
        get() = 858825962

    override val baseUrl = "https://readwebnovels.net"

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


    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return getSearch(page,query)
        }
        return when(sorts) {
            0 -> getLatest(page)
            1 -> getPopular(page)
            else -> getLatest(page)
        }
    }

    suspend fun getLatest(page: Int) : MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchLatestEndpoint(page))
        return bookListParse(client.get<String>(res).parseHtml(),latestSelector(),latestNextPageSelector()) { latestFromElement(it) }
    }
    suspend fun getPopular(page: Int) : MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchPopularEndpoint(page))
        return bookListParse(client.get<String>(res).parseHtml(),popularSelector(),popularNextPageSelector()) { popularFromElement(it) }
    }
    suspend fun getSearch(page: Int,query: String) : MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchSearchEndpoint(page,query))
        return bookListParse(client.get<String>(res).parseHtml(),searchSelector(),searchNextPageSelector()) { searchFromElement(it) }
    }

     fun fetchLatestEndpoint(page: Int): String? =
        "/manga-2/page/${page}/?m_orderby=latest"

     fun fetchPopularEndpoint(page: Int): String? =
        "/manga-2/page/${page}/?m_orderby=trending"

     fun fetchSearchEndpoint(page: Int, query: String): String? =
        "/?s=${query}&post_type=wp-manga&op=&author=&artist=&release=&adult="


    private fun headersBuilder() = io.ktor.http.Headers.build {
        append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
        append(HttpHeaders.CacheControl, "max-age=0")
        append(HttpHeaders.Referrer, baseUrl)
    }

    override val headers: io.ktor.http.Headers = headersBuilder()


    // popular
     fun popularRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchPopularEndpoint(page = page))
        }
    }

     fun popularSelector() = "div.page-item-detail"

     fun popularFromElement(element: Element): MangaInfo {
        val title = element.select("a").attr("title")
        val url = element.select("a").attr("href")
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

     fun popularNextPageSelector() = "div.nav-previous>a"

     fun latestSelector(): String = popularSelector()


     fun latestFromElement(element: Element): MangaInfo =popularFromElement(element)

     fun latestNextPageSelector() = popularNextPageSelector()

     fun searchSelector() = "div.c-tabs-item__content"

     fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("h3.h4 a").text()
        val url = element.select("div.tab-thumb a").attr("href")
        val thumbnailUrl = element.select("div.tab-thumb a img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

     fun searchNextPageSelector(): String? = null


    // manga details

    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("div.post-title h1").text()
        val cover = document.select("div.summary__content").attr("src")
        val link = baseUrl + document.select("div.cur div.wp a:nth-child(5)").attr("href")
        val authorBookSelector = document.select("div.author-content a").text()
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

    override fun chaptersSelector() = "li.wp-manga-chapter"

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

    override suspend fun getChapterList(book: MangaInfo): List<ChapterInfo> {
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {
                var chapters =
                    chaptersParse(
                        client.post<String>(requestBuilder(book.key + "ajax/chapters/")).parseHtml()
                    )
                if (chapters.isEmpty()) {
                    chapters = chaptersParse(client.post<Document>(requestBuilder(book.key)))
                }
                return@withContext chapters.reversed()
            }
        }.getOrThrow()
    }


    override fun pageContentParse(document: Document): List<String> {
        return document.select("div.read-container h3,p").eachText()
    }


    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get<String>(contentRequest(chapter)).parseHtml())
    }


    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }


}