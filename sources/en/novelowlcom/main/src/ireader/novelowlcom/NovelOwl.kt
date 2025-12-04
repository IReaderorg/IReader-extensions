package ireader.novelowlcom

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
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
import ireader.core.util.DefaultDispatcher
import kotlinx.coroutines.withContext
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import tachiyomix.annotations.Extension
import ireader.common.utils.DateParser

@Extension
abstract class NovelOwl(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "NovelWol"
    override val id: Long
        get() = 11

    override val baseUrl = "https://novelowl.com"

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
        val res = requestBuilder("$baseUrl/novel/page/$page/?m_orderby=latest")
        return bookListParse(
            client.get(res).asJsoup(),
            ".badge-pos-1",
            popularNextPageSelector()
        ) { latestFromElement(it) }
    }

    suspend fun getPopular(page: Int): MangasPageInfo {
        val res = requestBuilder("$baseUrl/novel/page/$page/?m_orderby=trending")
        return bookListParse(
            client.get(res).asJsoup(),
            ".badge-pos-1",
            popularNextPageSelector()
        ) { latestFromElement(it) }
    }

    suspend fun getSearch(query: String, page: Int): MangasPageInfo {
        val res =
            requestBuilder("$baseUrl/page/$page/?s=$query&post_type=wp-manga")
        return bookListParse(
            client.get(res).asJsoup(),
            "div.c-tabs-item__content",
            ".nextpostslink"
        ) { searchFromElement(it) }
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

    fun popularNextPageSelector() = ".wp-pagenavi .last"

    fun latestFromElement(element: Element): MangaInfo {
        val title = element.select("a").attr("title")
        val url = element.select("a").attr("href")
        val thumbnailUrl = element.select("img").attr("data-src")

        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("div.post-title h3.h4 a").text()
        val url = element.select("div.post-title h3.h4 a").attr("href")
        val thumbnailUrl = element.select("img").attr("data-src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    // manga details

    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("div.post-title>h1").text()
        val cover = document.select("div.summary_image a img").attr("data-src")

        val authorBookSelector = document.select("div.author-content>a").text()
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
            status = parseStatus(status),
            key = ""
        )
    }

    private fun parseStatus(string: String): Long {
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
        val link = element.select("a").attr("href")
        val name = element.select("a").text()
        val dateUploaded = element.select("i").text()

        return ChapterInfo(name = name, key = link, dateUpload = parseChapterDate(dateUploaded))
    }

    fun parseChapterDate(date: String): Long {
        return DateParser.parseRelativeOrAbsoluteDate(date)
    }

    override fun chaptersSelector(): String {
        return "li.wp-manga-chapter"
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return kotlin.runCatching {
            return@runCatching withContext(DefaultDispatcher) {
                var chapters =
                    chaptersParse(
                        client.post(requestBuilder(manga.key + "ajax/chapters/")).asJsoup(),
                    )
                if (chapters.isEmpty()) {
                    chapters = chaptersParse(client.post(requestBuilder(manga.key)).asJsoup())
                }
                return@withContext chapters.reversed()
            }
        }.getOrThrow()
    }

    override fun pageContentParse(document: Document): List<String> {
        val par = document.select("div.read-container .reading-content p").eachText()
            .map { it.replace("Read latest Chapters at Wuxia World . Site Only", "") }
        val head = document.select("#chapter-heading ").text()

        return listOf(head) + par
    }

    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(
            client.get(contentRequest(chapter)).asJsoup()
        ).map { it.replace("Come and read on our website wuxia worldsite. Thanks", "") }
    }

    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }

    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return client to requestBuilder(url) {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
            )
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, "https://wuxiaworld.site/")
        }
    }
}
