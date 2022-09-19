package ireader.mtlnovelcom

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import ireader.core.http.okhttp
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.util.concurrent.TimeUnit

@Extension
abstract class MtlNovel(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "MtlNovel"

    override val baseUrl = "https://www.mtlnovel.com"
    override val lang = "en"

    override val id: Long
        get() = 8
    override val client = HttpClient(OkHttp) {
        engine {
            preconfigured = clientBuilder()
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }

    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing()
        )
    }
    class LatestListing() : Listing("Latest")
    private fun clientBuilder(): OkHttpClient = deps.httpClients.default.okhttp
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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

    suspend fun getLatest(page: Int): MangasPageInfo {
        val res = requestBuilder("$baseUrl/novel-list/?orderby=date&order=desc&status=all&pg=$page")
        return bookListParse(client.get(res).asJsoup(), "div.box", "#pagination > a:nth-child(13)") { popularFromElement(it) }
    }
    suspend fun getPopular(page: Int): MangasPageInfo {
        val res = requestBuilder("$baseUrl/monthly-rank/page/$page/")
        return bookListParse(client.get(res).asJsoup(), "div.box", "#pagination > a:nth-child(13)") { popularFromElement(it) }
    }
    suspend fun getSearch(page: Int, query: String): MangasPageInfo {
        val res = requestBuilder("$baseUrl/wp-admin/admin-ajax.php?action=autosuggest&q=$query&__amp_source_origin=https%3A%2F%2Fwww.mtlnovel.com")
        return customJsonSearchParse(client.get(res).body())
    }

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }

    fun popularFromElement(element: Element): MangaInfo {
        val title = element.select("a.list-title").attr("aria-label")
        val url = element.select("a.list-title").attr("href")
        val thumbnailUrl = element.select("amp-img.list-img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("div.txt a").attr("title")
        val url = element.select("div.txt a").attr("href")
        val thumbnailUrl = element.select("div.pic img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("h1.entry-title").text()
        val cover = document.select("div.nov-head img").attr("src")
        val authorBookSelector = document.select("#author a").text()
        val description = document.select("div.desc p").eachText().joinToString("\n")
        val category = document.select("#currentgen a").eachText()

        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector,
            genres = category,
            key = "",
        )
    }

    // chapters
    override fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key.plus("chapter-list/"))
            headers { headers }
        }
    }

    override fun chaptersSelector() = "div.ch-list p a"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.select("a").attr("href")
        val name = element.select("a").text()

        return ChapterInfo(name = name, key = link)
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val request = client.get(chaptersRequest(manga)).asJsoup()
        return chaptersParse(request).reversed()
    }

    override fun pageContentParse(document: Document): List<String> {
        val title = document.select("h1.main-title").text()
        val content = document.select("div.par p").eachText()
        content.add(0, title)
        return content
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

    private fun customJsonSearchParse(mtlSearchItem: mtlSearchItem): MangasPageInfo {
        val books = mutableListOf<MangaInfo>()
        mtlSearchItem.items.first { item ->
            item.results.forEach { res ->
                books.add(
                    MangaInfo(
                        title = Jsoup.parse(res.title).text(),
                        key = res.permalink,
                        author = res.cn,
                        cover = res.thumbnail
                    )
                )
            }
            return@first true
        }

        return MangasPageInfo(books, false)
    }
}
