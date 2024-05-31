package ireader.freewebnovel

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class FreeWebNovel(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "FreeWebNovel"

    override val id: Long
        get() = 4

    override val baseUrl = "https://freewebnovel.com"
    override val lang = "en"

    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            Filter.Sort(
                "Sort By:",
                arrayOf(
                    "Latest",
                    "Popular",
                    "New Novels"
                )
            ),
        )
    }

    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
        )
    }
    class LatestListing() : Listing("Latest")
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return getSearch(query)
        }
        return when (sorts) {
            0 -> getLatest(page)
            1 -> getPopular()
            2 -> getNewNovel(page)
            else -> getLatest(page)
        }
    }

    private suspend fun getLatest(page: Int): MangasPageInfo {
        val req = requestBuilder("$baseUrl/latest-release-novels/$page/")
        // resp can be 404
        val resp = client.get(req)
        return bookListParse(resp.asJsoup(), "div.ul-list1 div.li", "div.ul-list1") { latestFromElement(it) }
    }
    private suspend fun getPopular(): MangasPageInfo {
        val req = requestBuilder("$baseUrl/most-popular-novels/")
        // resp can be 404
        val resp = client.get(req)
        return bookListParse(resp.asJsoup(), "div.ul-list1 div.li-row", null) { popularFromElement(it) }
    }
    private suspend fun getSearch(query: String): MangasPageInfo {
        val req = requestBuilder("$baseUrl/search/?searchkey=$query")
        // resp can be 404
        val resp = client.get(req)
        return bookListParse(resp.asJsoup(), "div.ul-list1 div.li-row", null) { searchFromElement(it) }
    }

    private suspend fun getNewNovel(page: Int): MangasPageInfo {
        val res = requestBuilder("$baseUrl/latest-novels/$page/")
        return bookListParse(client.get(res).asJsoup(), "div.ul-list1 div.li", "div.ul-list1") { latestFromElement(it) }
    }

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }

    private fun popularFromElement(element: Element): MangaInfo {
        val url = baseUrl + element.select("a").attr("href")
        val title = element.select("a").attr("title")
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    private fun latestFromElement(element: Element): MangaInfo {
        val title = element.select("div.txt a").attr("title")
        val url = baseUrl + element.select("div.txt a").attr("href")
        val thumbnailUrl = element.select("div.pic img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    private fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("div.txt a").attr("title")
        val url = baseUrl + element.select("div.txt a").attr("href")
        val thumbnailUrl = element.select("div.pic img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("div.m-desc h1.tit").text()
        val cover = document.select("div.m-book1 div.pic img").text()
        val link = baseUrl + document.select("div.cur div.wp a:nth-child(5)").attr("href")
        val authorBookSelector = document.select("div.right a.a1").attr("title")
        val description = document.select("div.inner p").eachText().joinToString("\n")
        // not sure why its not working.
        val category = document.select("[title=Genre]")
            .next()
            .text()
            .split(",")

        val status = document.select("[title=Status]")
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
            key = link,
            status = status
        )
    }
    private fun String.handleStatus(): Long {
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

    override fun chaptersSelector() = "div.m-newest2 ul.ul-list5 li"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").attr("title")

        return ChapterInfo(name = name, key = link)
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val resp = client.get(chaptersRequest(manga)).asJsoup()
        return chaptersParse(resp)
    }

    override fun pageContentParse(document: Document): List<String> {
        return document.select("div.txt h4,p").eachText()
    }

    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }
}
