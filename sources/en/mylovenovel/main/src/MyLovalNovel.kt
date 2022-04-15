package ireader.mylovenovel

import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.merge
import merge
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.ireader.core.*
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class MyLoveNovel(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "MyLoveNovel"


    override val id: Long
        get() = 14204738996342153
    override val baseUrl = "https://m.mylovenovel.com"

    override val lang = "en"


    override fun getFilters(): FilterList {
        return listOf(
                Filter.Title(),
                Filter.Sort(
                        "Sort By:", arrayOf(
                        "Latest",
                        "Popular",
                )),
        )
    }

    override fun getListings(): List<Listing> {
        return listOf(
                LatestListing()
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
        val res = requestBuilder("$baseUrl/lastupdate-${page}.html")
        return bookListParse(client.get(res).asJsoup(), latestSelector(), latestNextPageSelector()) { latestFromElement(it) }
    }

    suspend fun getPopular(page: Int): MangasPageInfo {
        val res = requestBuilder("$baseUrl/monthvisit-${page}.html")
        return bookListParse(client.get(res).asJsoup(), popularSelector(), popularNextPageSelector()) { popularFromElement(it) }
    }

    suspend fun getSearch(page: Int, query: String): MangasPageInfo {
        val res = requestBuilder("$baseUrl/index.php?s=so&module=book&keyword=${query}")
        return bookListParse(client.get(res).asJsoup(), searchSelector(), searchNextPageSelector()) { searchFromElement(it) }
    }


    override fun HttpRequestBuilder.headersBuilder() {

        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }

    fun popularSelector() = "ul.list li a"

    fun popularFromElement(element: Element): MangaInfo {
        val title = element.select("p.bookname").text()
        val url = baseUrl + element.attr("href")
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun popularNextPageSelector() = "div.pagelist>a"


    fun latestSelector(): String = popularSelector()


    fun latestFromElement(element: Element): MangaInfo = popularFromElement(element)

    fun latestNextPageSelector() = popularNextPageSelector()

    fun searchSelector() = "ul.list li a"

    fun searchFromElement(element: Element): MangaInfo = popularFromElement(element)

    fun searchNextPageSelector(): String? = popularNextPageSelector()


    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("div.detail img").attr("alt")
        val cover = document.select("div.detail img").attr("src")

        val authorBookSelector = document.select("#info > div.main > div.detail > p:nth-child(3)").text().replace("Author：", "")
        val description = document.select("div.intro").eachText().joinToString("\n")
        //not sure why its not working.
        val category = document.select("div.detail p.line a")
                .next()
                .text()
                .split(",")

        val status = document.select("div.detail > p:nth-child(6)")
                .next()
                .text()
                .replace("/[\t\n]/g", "")
                .handleStatus()




        return MangaInfo(
                title = title,
                cover = cover,
                description = description,
                author = authorBookSelector,
                genres = category,
                key = "",
                status = status
        )
    }

    private fun String.handleStatus(): Int {
        return when (this) {
            "Active" -> MangaInfo.ONGOING
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

    override fun chaptersSelector() = "#morelist ul.vlist li"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").text()

        return ChapterInfo(name = name, key = link)
    }

    override fun pageContentParse(document: Document): List<String> {
        val head = document.select("h1.headline").text()
        val content = document.select("div.content").html().split("<br>")
        return listOf(head) + content
    }

    fun <T> merge(first: List<T>, second: List<T>): List<T> {
        return object : ArrayList<T>() {
            init {
                addAll(first)
                addAll(second)
            }
        }
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

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val request = client.get(chaptersRequest(manga)).asJsoup()
        return chaptersParse(request)
    }


}