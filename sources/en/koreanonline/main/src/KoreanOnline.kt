package ireader.koreanonline

import android.util.Log
import io.ktor.client.request.*
import okhttp3.Headers
import org.ireader.core.LatestListing
import org.ireader.core.ParsedHttpSource
import org.ireader.core.SearchListing
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.source.Dependencies
import tachiyomi.source.model.*
import tachiyomix.annotations.Extension

@Extension
abstract class KoreanOnline(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "KoreanMtl.Online"


    override val id: Long
        get() = 14204738993432853
    override val baseUrl = "https://www.koreanmtl.online"

    override val lang = "en"

    override fun getListings(): List<Listing> {
        return listOf(
               LatestListing()
        )
    }

    override fun getFilters(): FilterList {
        return listOf(
                Filter.Title(),
                Filter.Sort(
                        "Sort By:",arrayOf(
                        "Latest",
                )),
        )
    }


    override fun fetchLatestEndpoint(page: Int): String? =
        "/p/novels-listing.html"

    override fun fetchPopularEndpoint(page: Int): String? = null
    override fun fetchSearchEndpoint(page: Int, query: String): String? =
        "/p/novels-listing.html"


    fun headersBuilder() = Headers.Builder().apply {
        add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
        )
        add("cache-control", "max-age=0")
    }

    override val headers: Headers = headersBuilder().build()

    override fun popularRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchLatestEndpoint(page))
            headers { headers }
        }
    }

    override fun popularSelector() = ""

    override fun popularFromElement(element: Element): MangaInfo {
        return MangaInfo(key = "", title = "")
    }

    override fun popularNextPageSelector() = null


    override suspend fun getLatest(page: Int): MangasPageInfo {
        val request = client.get<String>(latestRequest(page)).parseHtml()
        return latestParse(request)
    }

    override fun latestRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchLatestEndpoint(page)!!)
            headers { headers }
        }
    }

    override fun latestSelector(): String ="ul.a li.b"


    override fun latestFromElement(element: Element): MangaInfo {
        val title = element.select("a").text()
        val url = element.select("a").attr("href")
        Log.d("TAG", "latestFromElement: $title")

        return MangaInfo(key = url, title = title)
    }

    override fun latestNextPageSelector() = null

    override fun searchSelector() = latestSelector()

    override fun searchFromElement(element: Element): MangaInfo = latestFromElement(element)

    override fun searchNextPageSelector(): String? = null


    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val description = document.select("div.post-body p").eachText().joinToString("\n")

        return MangaInfo(
            title = "",
            key = "",
            description = description,
        )
    }


    // chapters
    override fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }

    override fun chaptersSelector() = "div.post-body ul.a li.a"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").text()

        return ChapterInfo(name = name, key = link)
    }


    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val request = client.get<String>(chaptersRequest(manga)).parseHtml()
        return chaptersParse(request)
    }


    override fun pageContentParse(document: Document): List<String> {
        return document.select("h1,p").eachText()
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


    override fun searchRequest(
        page: Int,
        query: String,
        filters: List<Filter<*>>,
    ): HttpRequestBuilder {
        return requestBuilder(baseUrl + fetchSearchEndpoint(page = page, query = query))
    }

    override suspend fun getSearch(query: String, filters: FilterList, page: Int): MangasPageInfo {
        val response = searchParse(client.get<String>(searchRequest(page, query, filters)).parseHtml())
        val books = response.mangas.filter { it.title.contains(query) }

        return MangasPageInfo(books , response.hasNextPage)
    }


}