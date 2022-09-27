package ireader.lightnovelsme

import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.ConstantCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.invoke
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.serialization.gson.gson
import ireader.core.http.okhttp
import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import ireader.lightnovels.books_dto.BookListDTO
import ireader.lightnovels.books_dto.Result
import ireader.lightnovels.chapter_dto.ChapterDTO
import ireader.lightnovels.content_dto.ContentDTO
import ireader.lightnovels.detail_dto.NovelDetail
import ireader.lightnovels.detail_dto.PageProps
import ireader.lightnovels.search_dto.ResultX
import ireader.lightnovels.search_dto.SearchDTO
import ireader.lightnovels.search_dto.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Extension
abstract class LightNovel(private val deps: Dependencies) : HttpSource(deps) {

    override val name = "LightNovel.me"

    override val id: Long
        get() = 6
    override val baseUrl = "https://lightnovels.me"

    override val lang = "en"

    override val client = HttpClient(OkHttp) {
        engine {
            preconfigured = clientBuilder()
        }
        BrowserUserAgent()
        install(ContentNegotiation) {
            gson()
        }
        install(HttpCookies) {
            storage = ConstantCookiesStorage()
        }
    }

    private fun clientBuilder(): OkHttpClient = deps.httpClients.default.okhttp
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            sorts,
        )
    }

    val sorts = Filter.Sort(
        "Sort By:",
        arrayOf(
            "Latest Release",
            "Hot Novel",
            "Complete Novel",
        )
    )

    class Latest() : Listing("Latest")

    override fun getListings(): List<Listing> {
        return listOf(
            Latest(),
        )
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sort = filters.findInstance<Filter.Sort>()?.value

        return if (query != null && query.isNotBlank()) {
            getSearch(query, filters, page)
        } else {
            getNovels(page, sort = sort)
        }
    }

    private suspend fun getSearch(query: String, filters: FilterList, page: Int): MangasPageInfo {
        val request = client.get(searchRequest(page, query, filters)).bodyAsText()
        val json = try {
            Gson().fromJson(request, SearchResult::class.java)
        } catch (e: Exception) {
            Gson().fromJson(request, SearchDTO::class.java)
        }
        return searchParse(json)
    }

    private fun fromSearchElement(e: ireader.lightnovels.search_dto.Result): MangaInfo {
        return MangaInfo(
            key = baseUrl + "/novel" + e.novel_slug,
            title = e.novel_name,
            artist = "",
            author = "",
            status = handleStatue(e.status),
            cover = baseUrl + e.novel_image
        )
    }

    private fun fromSearchElement(e: ResultX): MangaInfo {
        return MangaInfo(
            key = baseUrl + "/novel" + e.novel_slug,
            title = e.novel_name,
            artist = "",
            author = "",
            status = handleStatue(e.status),
            cover = baseUrl + e.novel_image
        )
    }

    private fun handleStatue(s: String): Long {
        return when (s) {
            "Ongoing" -> MangaInfo.ONGOING
            "Completed" -> MangaInfo.COMPLETED
            else -> MangaInfo.ONGOING
        }
    }

    private fun searchParse(novels: Any): MangasPageInfo {
        val books = when (novels) {
            is SearchDTO -> {
                novels.results.map { element ->
                    fromSearchElement(element)
                }
            }
            is SearchResult -> {
                novels.results.map { element ->
                    fromSearchElement(element)
                }
            }
            else -> {
                throw IllegalArgumentException()
            }
        }

        return MangasPageInfo(books, false)
    }

    fun selectors() = ".flex-wrap.flex.border-b"
    private fun nextPageSelector() = ".MuiSvgIcon-root.MuiPaginationItem-icon path"

    private fun fromElement(e: Result): MangaInfo {
        val name = e.novel_name
        val img = baseUrl + e.novel_image
        val url = baseUrl + "/novel" + e.novel_slug
        return MangaInfo(title = name, cover = img, key = url)
    }

    fun HeadersBuilder.applyHeader() {
        append(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
        )
        append("cache-control", " max-age=0")
        append("referer", baseUrl)
    }

    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return client to HttpRequestBuilder(url).apply {
            url(url)
            headers {
                append(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
                )
                append("cache-control", "max-age=0")
                append("referer", baseUrl)
            }
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getNovels(page, sort = sorts.value)
    }

    private suspend fun getNovels(page: Int, sort: Filter.Sort.Selection?): MangasPageInfo {
        val req = when (sort?.index) {
            1 -> HttpRequestBuilder().apply {
                url("https://lightnovels.me/api/novel/hot-novel?index=${(page - 1) * 20}&limit=20")
                headers { applyHeader() }
            }
            2 -> HttpRequestBuilder().apply {
                url("https://lightnovels.me/api/novel/completed-novels?index=${(page - 1) * 20}&limit=20")
                headers { applyHeader() }
            }
            else -> HttpRequestBuilder().apply {
                url("https://lightnovels.me/api/novel/latest-release-novel?index=${(page - 1) * 20}&limit=20")
                headers { applyHeader() }
            }
        }
        val request = client.get(req).asJsoup()

        return novelsParse(request)
    }

    private fun novelsParse(document: Document): MangasPageInfo {
        val jsonBook = Gson().fromJson<BookListDTO>(document.text(), BookListDTO::class.java)

        val books = jsonBook.results.map { element ->
            fromElement(element)
        }

        val hasNextPage = jsonBook.index < jsonBook.total

        return MangasPageInfo(books, hasNextPage)
    }

    fun detailRequest(manga: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(manga.key)
            headers { applyHeader() }
        }
    }

    fun searchRequest(
        page: Int,
        query: String,
        filters: List<Filter<*>>,
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url("$baseUrl/api/search?keyword=$query&index=0&limit=200")
            headers { applyHeader() }
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val mainDoc = client.get(detailRequest(manga = manga)).body<HttpResponse>().asJsoup()
        val json = mainDoc.select("#__NEXT_DATA__").html()
        val detail = Gson().fromJson(json, NovelDetail::class.java)
        return novelParsing(detail.props.pageProps)
    }

    private fun novelParsing(novel: PageProps): MangaInfo {
        val name = novel.novelInfo.novel_name
        val img = novel.novelInfo.novel_image
        val summary = novel.novelInfo.novel_description
        val genre = novel.genres.map { it.name }
        val author = novel.authors.firstOrNull()?.name ?: ""
        val status = novel.novelInfo.novel_status

        return MangaInfo(
            title = name,
            cover = baseUrl + img,
            key = "",
            description = summary,
            genres = genre,
            author = author,
            status = handleStatue(status)
        )
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return withContext(Dispatchers.IO) {
            val mainDoc = client.get(chaptersRequest(book = manga)).body<HttpResponse>().asJsoup()
            val json = mainDoc.select("#__NEXT_DATA__").html()
            val detail = Gson().fromJson(json, NovelDetail::class.java)
            val novelId = detail.props.pageProps.novelInfo.novel_id
            // val maxPage = parseMaxPage(manga)
            // val list = mutableListOf<Deferred<List<ChapterInfo>>>()
            val chapters = chaptersParse(
                Gson().fromJson(
                    client.get(
                        uniqueChaptersRequest(
                            novelId = novelId,
                            index = 0
                        )
                    ).asJsoup().body().text(),
                    ChapterDTO::class.java
                )
            )
//            for (i in 0..maxPage) {
//                val pChapters = async {
//                    val docs = client.get(
//                        uniqueChaptersRequest(
//                            novelId = novelId,
//                            index = i * 50
//                        )
//                    ).asJsoup().body().text()
//                    chaptersParse(
//                        Gson().fromJson(docs, ChapterDTO::class.java)
//                    )
//                }
//
//                list.addAll(listOf(pChapters))
//            }
            //  val request = client.get(chaptersRequest(book = book))

            // return@withContext list.awaitAll().flatten()
            return@withContext chapters
        }
    }

    private fun uniqueChaptersRequest(novelId: Int, index: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(
                "https://lightnovels.me/api/chapters?id=$novelId&index=0&limit=5000"
            )
            headers { headers }
        }
    }

    private fun chaptersParse(response: ChapterDTO): List<ChapterInfo> {
        return response.results.map { chapterFromElement(it) }
    }

    suspend fun parseMaxPage(book: MangaInfo): Int {
        val page = client.get(chaptersRequest(book = book)).asJsoup()
        val last = page.select(".MuiButtonBase-root.MuiPaginationItem-root")
            .indexOf(page.select(".MuiButtonBase-root.MuiPaginationItem-root").last())
        val maxPage =
            page.select(".MuiButtonBase-root.MuiPaginationItem-root")[last - 1].text() ?: "1"
        return maxPage.toInt()
    }

    fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
            headers {
                applyHeader()
            }
        }
    }

    fun chaptersSelector() = ".w-full .flex-col ul.flex-wrap.flex li.flex"

    fun chapterFromElement(result: ireader.lightnovels.chapter_dto.Result): ChapterInfo {
        return ChapterInfo(
            name = result.chapter_name,
            key = baseUrl + result.slug,
            dateUpload = parseChapterDate(result.updated_at)
        )
    }

    fun parseChapterDate(date: String): Long {
        return dateFormat.parse(date)?.time ?: 0L
    }

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return getContents(chapter).map { Text(it) }
    }

    private suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get(contentRequest(chapter)).asJsoup())
    }

    fun pageContentParse(document: Document): List<String> {
        val json = document.select("#__NEXT_DATA__").html()
        val parsedJson = Gson().fromJson(json, ContentDTO::class.java)
        val head = parsedJson.props.pageProps.cachedChapterInfo.chapter_name
        val par = parsedJson.props.pageProps.cachedChapterInfo.content.split(
            "\\u003c/p\\u003e\\u003cp\\u003e\\u003c/p\\u003e\\u003cp\\u003e",
            "<p>",
            "<br>"
        ).map { Jsoup.parse(it).text() }
        return listOf(head) + par
    }

    fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            header(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
            )
            headers {
                append("cache-control", " max-age=0")
                append(HttpHeaders.Referrer, baseUrl)
            }
        }
    }
}
