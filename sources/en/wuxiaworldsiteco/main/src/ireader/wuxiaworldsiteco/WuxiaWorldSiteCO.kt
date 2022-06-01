package ireader.wuxiaworldsiteco

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

import org.ireader.core_api.http.okhttp
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.ParsedHttpSource
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.*


@Extension
abstract class WuxiaWorld(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "WuxiaWorldSite.co"
    override val id: Long
        get() = 18

    override val baseUrl = "https://wuxiaworldsite.co"

    override val lang = "en"

    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            Filter.Sort(
                "Sort By:", arrayOf(
                    "Latest",
                    "Popular"
                )
            ),
        )
    }

    class LatestListing() : Listing("Latest")

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
            return getSearch(query, page)
        }
        return when (sorts) {
            0 -> getLatest(page)
            1 -> getPopular(page)
            else -> getLatest(page)
        }
    }


    suspend fun getLatest(page: Int): MangasPageInfo {
        val res = client.submitForm(
            url = "$baseUrl/ajax-story.ajax",
            formParameters = Parameters.build {
                append("count", "6")
                append("genres_include", "")
                append("keyword", "")
                append("limit", "6")
                append("order_by", "real_time")
                append("order_type", "DESC")
                append("page", page.toString())
            },
            encodeInQuery = false

        ) {
            headersBuilder()
        }
        return bookListParse(res.asJsoup(), ".item", popularNextPageSelector()) {
            latestFromElement(
                it
            )
        }
    }

    suspend fun getPopular(page: Int): MangasPageInfo {
        val res = client.submitForm(
            url = "$baseUrl/ajax-story.ajax",
            formParameters = Parameters.build {
                append("count", "6")
                append("genres_include", "")
                append("keyword", "")
                append("limit", "6")
                append("order_by", "views")
                append("order_type", "DESC")
                append("page", page.toString())
            },
            encodeInQuery = false

        ) {
            headersBuilder()
        }
        return bookListParse(res.asJsoup(), ".item", popularNextPageSelector()) {
            latestFromElement(
                it
            )
        }
    }

    suspend fun getSearch(query: String, page: Int): MangasPageInfo {
        val res = requestBuilder("$baseUrl/search/$query")
        return bookListParse(client.get(res).asJsoup(), ".item", null) { latestFromElement(it) }
    }


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


    fun popularNextPageSelector() = ".paging_section"


    fun latestFromElement(element: Element): MangaInfo {
        val title = element.select("a").attr("title")
        val url = baseUrl + element.select("a").attr("href")
        val thumbnailUrl = baseUrl + element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }


    // manga details

    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("h1.heading_read").text()
        val cover = baseUrl + document.select(".img-read img").attr("src")
        val link = ""
        val authorBookSelector = document.select(".content-reading p").text()
        val description =
            document.select(".story-introduction-content p").eachText()
                .joinToString("\n\n")
        val category = document.select(".tags a").eachText()
        val status = document.select(".a_tag_item:last-child").text()


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
            else -> MangaInfo.ONGOING
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
        val link = baseUrl + element.select("a").attr("href")
        val name = element.select("a:not(i)").text()
        val dateUploaded = element.select("i").text()

        return ChapterInfo(name = name, key = link, dateUpload = parseChapterDate(dateUploaded))
    }

    override fun chaptersParse(document: Document): List<ChapterInfo> {
        return document.select(chaptersSelector()).map {
            try {
                chapterFromElement(it)
            } catch (e: Exception) {
                ChapterInfo("", "")
            }

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

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM dd,yyyy", Locale.US)

    override fun chaptersSelector(): String = "a"

    suspend fun customRequest(document: Document): String? {
        return document.select(".story-introduction__toggler span").attr("data-id")
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val init = client.get(requestBuilder(manga.key)).asJsoup()
        val bookId = customRequest(init)
        val response =
            client.get(requestBuilder("$baseUrl/get-full-list.ajax?id=$bookId")).asJsoup()
        val parser = chaptersParse(response)
        Log.d("TAG", "parser: $parser")
        return parser
    }


    override fun pageContentParse(document: Document): List<String> {
        val par = document.select(".content-story p").eachText()
        return par.drop(1)
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