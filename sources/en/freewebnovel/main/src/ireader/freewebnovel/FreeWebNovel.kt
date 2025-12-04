package ireader.freewebnovel

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
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
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import tachiyomix.annotations.*

/**
 * 📚 FreeWebNovel Source
 * 
 * Uses ParsedHttpSource with KSP annotations for filters/commands.
 * Custom fetch logic required due to different selectors per listing type.
 */
@Extension
@AutoSourceId(seed = "FreeWebNovel")
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular", "New Novels"])
@GenerateCommands(detailFetch = true, chapterFetch = true, contentFetch = true)
abstract class FreeWebNovel(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "FreeWebNovel"
    override val id: Long get() = 4
    override val baseUrl = "https://freewebnovel.com"
    override val lang = "en"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular", "New Novels")),
    )
    
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override fun getListings(): List<Listing> = listOf(LatestListing())
    class LatestListing : Listing("Latest")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo = getLatest(page)

    override suspend fun getMangaList(filters: ireader.core.source.model.FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) return getSearch(query)
        return when (sorts) {
            0 -> getLatest(page)
            1 -> getPopular()
            2 -> getNewNovel(page)
            else -> getLatest(page)
        }
    }

    private suspend fun getLatest(page: Int): MangasPageInfo {
        val resp = client.get(requestBuilder("$baseUrl/latest-release-novels/$page/"))
        return bookListParse(resp.asJsoup(), "div.ul-list1 div.li", "div.ul-list1") { latestFromElement(it) }
    }

    private suspend fun getPopular(): MangasPageInfo {
        val resp = client.get(requestBuilder("$baseUrl/most-popular-novels/"))
        return bookListParse(resp.asJsoup(), "div.ul-list1 div.li-row", null) { popularFromElement(it) }
    }

    private suspend fun getSearch(query: String): MangasPageInfo {
        val resp = client.get(requestBuilder("$baseUrl/search/?searchkey=$query"))
        return bookListParse(resp.asJsoup(), "div.ul-list1 div.li-row", null) { searchFromElement(it) }
    }

    private suspend fun getNewNovel(page: Int): MangasPageInfo {
        val resp = client.get(requestBuilder("$baseUrl/latest-novels/$page/"))
        return bookListParse(resp.asJsoup(), "div.ul-list1 div.li", "div.ul-list1") { latestFromElement(it) }
    }

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }

    private fun popularFromElement(element: Element) = MangaInfo(
        key = baseUrl + element.select("a").attr("href"),
        title = element.select("a").attr("title"),
        cover = baseUrl + element.select("img").attr("src")
    )

    private fun latestFromElement(element: Element) = MangaInfo(
        key = baseUrl + element.select("div.txt a").attr("href"),
        title = element.select("div.txt a").attr("title"),
        cover = baseUrl + element.select("div.pic img").attr("src")
    )

    private fun searchFromElement(element: Element) = MangaInfo(
        key = baseUrl + element.select("div.txt a").attr("href"),
        title = element.select("div.txt a").attr("title"),
        cover = baseUrl + element.select("div.pic img").attr("src")
    )

    override fun detailParse(document: Document) = MangaInfo(
        title = document.select("div.m-desc h1.tit").text(),
        cover = baseUrl + document.select("div.m-book1 div.pic img").attr("src"),
        description = document.select("div.inner p").eachText().joinToString("\n"),
        author = document.select("div.right a.a1").attr("title"),
        genres = document.select("[title=Genre]").next().text().split(","),
        key = baseUrl + document.select("div.cur div.wp a:nth-child(5)").attr("href"),
        status = document.select("[title=Status]").next().text().handleStatus()
    )

    private fun String.handleStatus(): Long = when (this) {
        "OnGoing" -> MangaInfo.ONGOING
        "Complete" -> MangaInfo.COMPLETED
        else -> MangaInfo.ONGOING
    }

    override fun chaptersRequest(book: MangaInfo) = HttpRequestBuilder().apply {
        url(book.key)
        headers { headers }
    }

    override fun chaptersSelector() = "div.m-newest2 ul.ul-list5 li"

    override fun chapterFromElement(element: Element) = ChapterInfo(
        name = element.select("a").attr("title"),
        key = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
    )

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> =
        chaptersParse(client.get(chaptersRequest(manga)).asJsoup())

    override fun pageContentParse(document: Document): List<String> =
        document.select("div.txt h4,p").eachText()

    override fun contentRequest(chapter: ChapterInfo) = HttpRequestBuilder().apply {
        url(chapter.key)
        headers { headers }
    }
}
