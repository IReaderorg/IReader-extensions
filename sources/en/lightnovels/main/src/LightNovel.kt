package ireader.lightnovels

import books_dto.BookListDTO
import chapter_dto.ChapterDTO
import chapter_dto.Result
import com.google.gson.Gson
import detail_dto.NovelDetail
import detail_dto.PageProps
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.*
import org.ireader.core.asJsoup
import org.ireader.core.findInstance
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource
import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import search_dto.SearchResult
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.*

@Extension
abstract class LightNovel(private val deps: Dependencies) : HttpSource(deps) {


    override val name = "LightNovel.me"


    override val id: Long
        get() = 9999999997
    override val baseUrl = "https://lightnovels.me"

    override val lang = "en"

    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson()
        }
        install(HttpCookies) {
            storage = ConstantCookiesStorage()
        }
    }


    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            sorts,
        )
    }

    val sorts = Filter.Sort(
        "Sort By:", arrayOf(
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
        val json = Gson().fromJson(request, SearchResult::class.java)
        return searchParse(json)
    }

    private fun fromSearchElement(e: search_dto.Result): MangaInfo {
        return MangaInfo(
            key = baseUrl + "/novel" + e.novel_slug,
            title = e.novel_name,
            artist = "",
            author = "",
            status = handleStatue(e.status),
            cover = baseUrl + e.novel_image
        )
    }


    private fun handleStatue(s: String): Int {
        return when (s) {
            "Ongoing" -> MangaInfo.ONGOING
            "Completed" -> MangaInfo.COMPLETED
            else -> MangaInfo.ONGOING
        }
    }

    private fun searchParse(novels: SearchResult): MangasPageInfo {
        val books = novels.results.map { element ->
            fromSearchElement(element)
        }

        return MangasPageInfo(books, false)
    }

    fun selectors() = ".flex-wrap.flex.border-b"
    private fun nextPageSelector() = ".MuiSvgIcon-root.MuiPaginationItem-icon path"

    private fun fromElement(e: books_dto.Result): MangaInfo {
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

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
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

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return withContext(Dispatchers.IO) {
            val mainDoc = client.get(chaptersRequest(book = manga)).body<HttpResponse>().asJsoup()
            val json = mainDoc.select("#__NEXT_DATA__").html()
            val detail = Gson().fromJson(json, NovelDetail::class.java)
            val novelId = detail.props.pageProps.novelInfo.novel_id
            //val maxPage = parseMaxPage(manga)
            //val list = mutableListOf<Deferred<List<ChapterInfo>>>()
            val chapters = chaptersParse(
                Gson().fromJson(
                    client.get(
                        uniqueChaptersRequest(
                            novelId = novelId,
                            index = 0
                        )
                    ).asJsoup().body().text(), ChapterDTO::class.java
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

            //return@withContext list.awaitAll().flatten()
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

    fun chapterFromElement(result: Result): ChapterInfo {
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

    override suspend fun getPageList(chapter: ChapterInfo): List<Page> {
        return getContents(chapter).map { Text(it) }
    }

    private suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get(contentRequest(chapter)).asJsoup())
    }

    fun pageContentParse(document: Document): List<String> {
        val head = document.select("h1.text-lg").text()
        val par = document.select(".text-xl p").eachText()
        return listOf(head) + par
    }

    fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers {
                applyHeader()
            }
        }
    }

}