package ireader.mylovenovel

import android.util.Log
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.merge
import merge
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.ireader.core.LatestListing
import org.ireader.core.ParsedHttpSource
import org.ireader.core.PopularListing
import org.ireader.core.SearchListing
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.source.Dependencies
import tachiyomi.source.model.*
import tachiyomix.annotations.Extension

@Extension
abstract class MyLoveNovel(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "FreeWebNovel"


    override val id: Long
        get() = 14204738996342153
    override val baseUrl = "https://m.mylovenovel.com"

    override val lang = "en"


    override fun getFilters(): FilterList {
        return listOf()
    }


    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
            PopularListing(),
            SearchListing()
        )
    }

    override fun fetchLatestEndpoint(page: Int): String? =
        "/lastupdate-${page}.html"

    override fun fetchPopularEndpoint(page: Int): String? =
        "/monthvisit-${page}.html"

    override fun fetchSearchEndpoint(page: Int, query: String): String? =
        "/index.php?s=so&module=book&keyword=${query}"


    fun headersBuilder() = Headers.Builder().apply {
        add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
        )
        add("cache-control", "max-age=0")
    }

    override val headers: Headers = headersBuilder().build()


    // popular
    override fun popularRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchPopularEndpoint(page = page))
        }
    }

    override fun popularSelector() =  "ul.list li a"

    override fun popularFromElement(element: Element): MangaInfo {
        val title = element.select("p.bookname").text()
        val url = baseUrl + element.attr("href")
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    override fun popularNextPageSelector() = "div.pagelist>a"


    // latest

    override fun latestRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchLatestEndpoint(page)!!)
            headers { headers }
        }
    }

    override fun latestSelector(): String = popularSelector()


    override fun latestFromElement(element: Element): MangaInfo = popularFromElement(element)

    override fun latestNextPageSelector() = popularNextPageSelector()

    override fun searchSelector() = "ul.list li a"

    override fun searchFromElement(element: Element): MangaInfo = popularFromElement(element)

    override fun searchNextPageSelector(): String? = popularNextPageSelector()


    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("div.detail img").attr("alt")
        val cover = document.select("div.detail img").attr("src")

        val authorBookSelector = document.select("#info > div.main > div.detail > p:nth-child(3)").text().replace("Author：","")
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
    private fun String.handleStatus() : Int {
        return when(this){
            "Active"-> MangaInfo.ONGOING
            "Complete"-> MangaInfo.COMPLETED
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
        return pageContentParse(client.get<String>(contentRequest(chapter)).parseHtml())
    }


    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val request = client.get<String>(chaptersRequest(manga)).parseHtml()
        return chaptersParse(request)
    }


    override fun searchRequest(
        page: Int,
        query: String,
        filters: List<Filter<*>>,
    ): HttpRequestBuilder {
        return requestBuilder(baseUrl + fetchSearchEndpoint(page = page, query = query))
    }

    override suspend fun getSearch(query: String, filters: FilterList, page: Int): MangasPageInfo {
        return searchParse(client.get<String>(searchRequest(page, query, filters)).parseHtml())
    }


}