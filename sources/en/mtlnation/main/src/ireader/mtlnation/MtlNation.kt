package ireader.mtlnation

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import okhttp3.OkHttpClient

import org.ireader.core_api.http.okhttp
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.ParsedHttpSource
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.util.concurrent.TimeUnit

//I haven't finished configuring this extension because of its aggressive cloudflare protecion
@Extension
abstract class MtlNation(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "MtlNation"


    override val id: Long
        get() = 7
    override val baseUrl = "https://mtlnation.com"
    val translatorUrl = "https://mtlnation-com.translate.goog"
    val translatorEndPoint = "?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en-US&_x_tr_pto=op,wapp"

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

    private fun clientBuilder(): OkHttpClient = deps.httpClients.default.okhttp
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val client = HttpClient(OkHttp) {
        engine {
            preconfigured = clientBuilder()
        }
    }


    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
        )
    }

    class LatestListing() : Listing("Latest")

    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return client to HttpRequestBuilder(url).apply {
            url(url)
            headers {
                append(
                    HttpHeaders.UserAgent,
                    agent
                )
                append(HttpHeaders.CacheControl, "max-age=0")
                append(HttpHeaders.Referrer, translatorUrl)
            }
        }
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

    override fun getCommands(): CommandList {
        return listOf(
            Command.Chapter.Note("you can fetch chapters by going to WebView and click on Fetch Chapters "),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
            Command.Detail.Fetch()
        )
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val command = commands.find { it is Command.Detail.Fetch }
        if (command != null && command is Command.Detail.Fetch) {
            return detailParsed(Jsoup.parse(command.html), command.url)
        }
        val html = client.get(requestBuilder(manga.key))

        return detailParse(html.asJsoup())
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val command = commands.find { it is Command.Content.Fetch }
        if (command != null && command is Command.Content.Fetch) {
            return pageContentParse(Jsoup.parse(command.html)).map { Text(it) }
        }
        val url = chapter.key.replace(
            baseUrl,
            translatorUrl
        ) + "?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en-US&_x_tr_pto=op,wapp"
        val html = client.get(requestBuilder(url = url))
        return pageContentParse(html.asJsoup()).map { Text(it) }
    }

    private val agent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36"

    suspend fun getLatest(page: Int): MangasPageInfo {
        val html: HttpResponse =
            client.get(requestBuilder("https://mtlnation-com.translate.goog/novel/page/$page/?m_orderby=latest&_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en-US&_x_tr_pto=wapp"))

        return bookListParse(
            html.asJsoup(),
            "div.page-item-detail",
            ".last"
        ) { popularFromElement(it) }
    }


    suspend fun getPopular(page: Int): MangasPageInfo {
        val response =client.get(
            requestBuilder("https://mtlnation-com.translate.goog/novel/page/$page/?m_orderby=trending&_x_tr_sl=auto&_x_tr_tl=fa&_x_tr_hl=en-US&_x_tr_pto=op,wapp",)
        )
        return bookListParse(
            response.asJsoup(),
            "div.page-item-detail",
            "a.last"
        ) { popularFromElement(it) }
    }

    suspend fun getSearch(page: Int, query: String): MangasPageInfo {
        val response: HttpResponse =
            client.get(requestBuilder("https://mtlnation-com.translate.goog/?s=$query&post_type=wp-manga&_x_tr_sl=auto&_x_tr_tl=fa&_x_tr_hl=en-US&_x_tr_pto=op,wapp"))

        return bookListParse(
            response.asJsoup(),
            ".c-tabs-item__content",
            null
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


    fun popularFromElement(element: Element): MangaInfo {

        val url = element.select("h3.h5 a").attr("href").replace(translatorUrl,baseUrl)
        val title = element.select("h3.h5 a").text()
        val thumbnailUrl = element.select("img").attr("data-src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }


    fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("a").text()
        val url = baseUrl + element.select("a").attr("href")
        val thumbnailUrl = element.select("img").attr("data-src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    // manga details
    fun detailParsed(document: Document, url: String): MangaInfo {
        val title = document.select(".post-title h1").text()
        val cover = document.select(".summary_image img").attr("data-src")
        val link = url.ifBlank { document.select(".summary_image a").attr("href") }
        val authorBookSelector = document.select(".author-content a").text()
        val description = document.select(".summary__content p").eachText().joinToString("\n")
        //not sure why its not working.
        val category = document.select(".genres-content a")
            .eachText()

        val status = document.select(".post-status .summary-content")
            .text()
            .handleStatus()




        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector,
            genres = category,
            key = link,
            status = status
        )
    }

    override fun detailParse(document: Document): MangaInfo {
        val title = document.select(".post-title h1").text()
        val cover = document.select(".summary_image img").attr("data-src")
        val link = document.select(".summary_image a").attr("href")
        val authorBookSelector = document.select(".author-content a").text()
        val description = document.select(".summary__content p").eachText().joinToString("\n")
        //not sure why its not working.
        val category = document.select(".genres-content a")
            .eachText()

        val status = document.select(".post-status .summary-content")
            .text()
            .handleStatus()




        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector,
            genres = category,
            key = link,
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

    override fun chaptersSelector() = ".wp-manga-chapter a"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.select("a").attr("href")
        val name = element.select("a").text()

        return ChapterInfo(name = name, key = link)
    }


    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val command = commands.find { it is Command.Chapter.Fetch }
        if (command != null && command is Command.Chapter.Fetch) {
            return chaptersParse(Jsoup.parse(command.html)).reversed()
        }

        val url = manga.key
        val html = deps.httpClients.browser.fetch(
            url,
            chaptersSelector(),
            timeout = 50000,
            userAgent = agent
        ).responseBody
        return chaptersParse(html.asJsoup()).reversed()
    }


    override fun pageContentParse(document: Document): List<String> {
        return document.select("div.txt h4,p").eachText().drop(1)
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