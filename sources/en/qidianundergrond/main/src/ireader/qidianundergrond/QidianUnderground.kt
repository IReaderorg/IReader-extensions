package ireader.qidianundergrond

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

import org.ireader.core_api.http.Result
import org.ireader.core_api.http.okhttp
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension
import java.util.concurrent.TimeUnit

@Extension
abstract class QidianUnderground(private val deps: Dependencies) : HttpSource(deps) {

    override val name = "Qidian Underground"


    override val id: Long
        get() = 14204738993432811
    override val baseUrl = "https://toc.qidianunderground.org"

    override val client = HttpClient(OkHttp) {
        engine {
            preconfigured = clientBuilder()
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })

        }
    }
    class LatestListing() : Listing("Latest")
    private fun clientBuilder(): OkHttpClient = deps.httpClients.default.okhttp
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val lang = "en"

    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
        )
    }

    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
        )
    }


    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        return if (!query.isNullOrBlank()) {
            getSearch(query)
        } else {
            getLatest(page)
        }

    }

    suspend fun getLatest(page: Int): MangasPageInfo {
        val res = requestBuilder("$baseUrl/api/v1/pages/public")

        return parseBooks(
            Gson().fromJson<QUGroup>(
                client.get(res).bodyAsText(),
                QUGroup::class.java
            )
        )

    }

    fun requestBuilder(
        url: String,
        block: HeadersBuilder.() -> Unit = {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
            )
            append(HttpHeaders.CacheControl, "max-age=0")
        }
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(url)
            HeadersBuilder().apply(block)
        }
    }

    suspend fun getSearch(query: String): MangasPageInfo {
        val res = requestBuilder("$baseUrl/api/v1/pages/public")
        return parseBooks(
            Gson().fromJson<QUGroup>(
                client.get(res).bodyAsText(),
                QUGroup::class.java
            ),
            query
        )
    }

    private fun parseBooks(quGroup: QUGroup, query: String = ""): MangasPageInfo {
        val books = mutableListOf<MangaInfo>()
        quGroup.forEach {
            kotlin.runCatching {
                books.add(
                    MangaInfo(
                        key = "https://toc.qidianunderground.org/api/v1/pages/public/${it.ID}/chapters",
                        title = it.Name,
                        status = when (it.Status) {
                            "(Completed)" -> MangaInfo.COMPLETED
                            else -> MangaInfo.ONGOING
                        }
                    )
                )
            }.getOrNull()
        }
        return MangasPageInfo(books.filter { it.title.contains(query, true) }, false)
    }


    fun HttpRequestBuilder.headersBuilder() {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
            )
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return detailParse()
    }

    fun detailParse(): MangaInfo {
        return MangaInfo(
            title = "",
            key = "",
        )
    }


    // chapters
    fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }


    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val request = client.get(chaptersRequest(manga)).bodyAsText()
        val chapters = Gson().fromJson(request, ChapterGroup::class.java)
        return chapters.map { ChapterInfo(key = it.Href, name = it.Text) }
    }


    fun pageContentParse(document: Document): List<String> {
        return document.select("div > p,h2").eachText().map { Jsoup.parse(it).text() }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        var html : Result ?= null
        withContext(Dispatchers.Main) {
            html = deps.httpClients.browser.fetch(chapter.key, selector = ".well")
        }
        return pageContentParse(Jsoup.parse(html?.responseBody?:"")).map { Text(it) }
    }

    suspend fun getContents(chapter: ChapterInfo): List<String> {
        val html = client.get(contentRequest(chapter)).asJsoup()
        return pageContentParse(html)
    }


    fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }


}

