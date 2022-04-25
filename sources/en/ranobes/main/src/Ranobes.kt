package ireader.ranobes

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.ireader.core.*
import org.ireader.core_api.http.okhttp
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.util.concurrent.TimeUnit

@Extension
abstract class Ranobes(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "Ranobes"


    override val id: Long
        get() = 999999951
    override val baseUrl = "https://ranobes.net"

    override val lang = "en"

    override val client = HttpClient(OkHttp) {
        engine {
            preconfigured = clientBuilder()
        }
        install(HttpCookies) {
            storage = ConstantCookiesStorage()
        }
    }

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

    val agent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36"

    private fun clientBuilder(): OkHttpClient = deps.httpClients.default.okhttp
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
            DetailParse(),
            ChaptersParse(),
            ChapterParse()
        )
    }


    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    suspend fun getLatest(page: Int): MangasPageInfo {
        var response = ""
        withContext(Dispatchers.Main) {
            response = deps.httpClients.browser.fetch(
                url = baseUrl + fetchLatestEndpoint(page),
                    selector = latestSelector(),
                    userAgent = agent
            ).responseBody
        }

        return bookListParse(
            Jsoup.parse(response),
            latestSelector(),
            latestNextPageSelector()
        ) { latestFromElement(it) }
    }

    suspend fun getPopular(page: Int): MangasPageInfo {
        val response = deps.httpClients.browser.fetch(
            url = "https://ranobes.net/ranking/cstart=$page&ajax=true",
                selector = ".rank-story a",
                userAgent = agent
        )
        return bookListParse(
            response.responseBody.asJsoup(),
            popularSelector(),
            null
        ) { popularFromElement(it) }
    }



    fun fetchSearchEndpoint(page: Int, query: String): String? =
        "/index.php?do=search&subaction=search&search_start=0&full_search=0&result_from=1&story=$query"

    suspend fun getSearch(page: Int, query: String): MangasPageInfo {
        val response = deps.httpClients.browser.fetch(
            url = baseUrl + fetchSearchEndpoint(page,query),
                selector = searchSelector(),
                userAgent = agent
        )
        return bookListParse(
            response.responseBody.asJsoup(),
            searchSelector(),
            searchNextPageSelector()
        ) { searchFromElement(it) }
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


    fun fetchLatestEndpoint(page: Int): String? =
        "/novels/page/$page/"

    fun fetchPopularEndpoint(page: Int): String? =
        "/cstart=$page&ajax=true"


    override fun HttpRequestBuilder.headersBuilder() {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
            )
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }


    fun popularSelector() = ".rank-story"

    fun popularFromElement(element: Element): MangaInfo {
        val url = element.select(".title a").attr("href")
        val title = element.select(".title").text()
        val thumbnailUrl = baseUrl + element.select(".fit-cover img").attr("src")
        val desc = element.select(".moreless__full").text()
        val genre = element.select(".rank-story-genre .small a").eachText()
        return MangaInfo(
            key = url,
            title = title,
            cover = thumbnailUrl,
            description = desc,
            genres = genre
        )
    }

    fun popularLastPageSelector() = ".ranking__empty"

    fun latestSelector(): String = ".short-cont"


    fun latestFromElement(element: Element): MangaInfo {
        val url = element.select("a").attr("href")
        val title = element.select(".title").text()
        val thumbnailUrl =
            element.select("figure").attr("style").substringAfter("background-image: url(").substringBefore(");")

        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun latestNextPageSelector() = ".icon-right"

    fun searchSelector() = ".shortstory"

    fun searchFromElement(element: Element): MangaInfo {
        val url = element.select(".title a").attr("href")
        val title = element.select(".title a:not(span)").text()
        val thumbnailUrl =element.select(".cont-in .cover").attr("style").substringAfter("background-image: url(").substringBefore(");")
        val desc = element.select(".cont-in div").text()
        return MangaInfo(
            key = url,
            title = title,
            cover = thumbnailUrl,
            description = desc,
        )
    }

    fun searchNextPageSelector(): String? = popularLastPageSelector()


    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        val res = deps.httpClients.browser.fetch(
                manga.key,
                selector = "h1.title",
                userAgent = agent
        )
        return detailParse(Jsoup.parse(res.responseBody))
    }

    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("h1.title").first()?.ownText()
        val authorBookSelector = document.select(".tag_list a").text()
        val cover = document.select(".r-fullstory-poster a").attr("href")
        val description = document.select(".cont-in .showcont-h[itemprop=\"description\"] .moreless__full").text()

        val category = document.select(".links[itemprop=\"genre\"] a")
            .eachText()

        val status = document.select(".r-fullstory-spec .grey").first()
            ?.text()
            ?.replace("/[\t\n]/g", "")
            ?.handleStatus()!!




        return MangaInfo(
            title = title?:"",
            cover = cover,
            description = description,
            author = authorBookSelector ?: "",
            genres = category,
            key = "",
            status = status
        )
    }

    private fun String.handleStatus(): Int {
        return when (this) {
            "OnGoing" -> MangaInfo.ONGOING
            "Completed" -> MangaInfo.COMPLETED
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


    override fun chaptersSelector() = ".cat_line"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href")
        val name = element.select("a").attr("title")

        return ChapterInfo(name = name, key = link)
    }


    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val bookId = Regex("[0-9]+").findAll(manga.key)
                .map(MatchResult::value)
                .toList()
        val chapters = mutableListOf<ChapterInfo>()
        var currentPage = 1
        val res =
                Jsoup.parse(deps.httpClients.browser.fetch("https://ranobes.net/chapters/${bookId.first()}/page/1/", selector = chaptersSelector()).responseBody)
        chapters.addAll(chaptersParse(res))
        val maxPage = res.select(".pages a").last()?.text()?.toInt() ?: 1
        while (currentPage <= maxPage) {
            val response = Jsoup.parse(deps.httpClients.browser.fetch("https://ranobes.net/chapters/${bookId.first()}/page/$currentPage/", selector = chaptersSelector()).responseBody)
            chapters.addAll(chaptersParse(response))
            currentPage++
        }



        return chapters.reversed()
    }

    override fun chaptersParse(document: Document): List<ChapterInfo> {
        return document.select(chaptersSelector()).map { chapterFromElement(it) }
    }

    override fun pageContentParse(document: Document): List<String> {
        return document.select(".shortstory h1,p").eachText()
    }

    override suspend fun getPageList(chapter: ChapterInfo): List<Page> {
        val res = deps.httpClients.browser.fetch(
                chapter.key,
                selector = ".shortstory h1,p",
                userAgent = agent
        )
        return pageContentParse(Jsoup.parse(res.responseBody)).map {
            Text(
                it
            )
        }
    }

    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        return emptyList()
    }


    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }


}