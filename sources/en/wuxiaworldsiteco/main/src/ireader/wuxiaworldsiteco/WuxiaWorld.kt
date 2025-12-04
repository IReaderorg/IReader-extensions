package ireader.wuxiaworldsiteco

import ireader.core.log.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
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
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import tachiyomix.annotations.Extension
import ireader.common.utils.DateParser

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
                "Sort By:",
                arrayOf(
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

    private fun parseStatus(string: String): Long {
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
        return DateParser.parseRelativeOrAbsoluteDate(date)
    }

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
        Log.debug { "parser: $parser" }
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
