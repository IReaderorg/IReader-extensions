package ireader.comrademao

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Url
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
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import tachiyomix.annotations.Extension
import ireader.common.utils.DateParser

@Extension
abstract class ComradeMao(private val deps: Dependencies) : HttpSource(deps) {

    override val name = "Comrademao"

    override val id: Long
        get() = 3
    override val baseUrl = "https://comrademao.com"

    override val lang = "en"
    val sorts = Filter.Sort(
        "Sort By:",
        arrayOf(
            "Chinese",
            "Japanese",
            "Korean",
        )
    )
    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            sorts,
        )
    }



    class Chinese : Listing("Chinese")
    class Japanese : Listing("Japanese")
    class Korean : Listing("Korean")

    override fun getListings(): List<Listing> {
        return listOf(
            Chinese(),
        )
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sort = filters.findInstance<Filter.Sort>()?.value

        if (query != null && query.isNotBlank()) {
            return getSearch(query, filters, page)
        } else {
            return getNovels(page, sort = sort)
        }
    }

    suspend fun getSearch(query: String, filters: FilterList, page: Int): MangasPageInfo {
        val request = client.get(searchRequest(page, query, filters)).asJsoup()
        return searchParse(request)
    }

    fun fromSearchElement(element: Element): MangaInfo {
        val name = element.select("a").attr("title")
        val img = element.select("img").attr("src")
        val url = element.select("a").attr("href")

        return MangaInfo(title = name, cover = img, key = url)
    }

    open fun searchParse(document: Document): MangasPageInfo {
        val books = document.select(".bs .bsx a").map { element ->
            fromSearchElement(element)
        }

        return MangasPageInfo(books, false)
    }

    fun selectors() = ".listupd .bsx"
    fun nextPageSelector() = "div.pagination a:nth-child(2)"

    fun fromElement(element: Element): MangaInfo {
        val name = element.select("a").text()
        val img = element.select("img").attr("src")
        val url = element.select("a").attr("href")

        return MangaInfo(title = name, cover = img, key = url)
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getNovels(page, sort = sorts.value)
    }

    private suspend fun getNovels(page: Int, sort: Filter.Sort.Selection?): MangasPageInfo {
        val req = when (sort?.index) {
            0 -> HttpRequestBuilder().apply {
                url("$baseUrl/mtype/chinese/page/$page/")
                headers { headers }
            }
            1 -> HttpRequestBuilder().apply {
                url("$baseUrl/mtype/japanese/page/$page/")
                headers { headers }
            }
            2 -> HttpRequestBuilder().apply {
                url("$baseUrl/mtype/korean/page/$page/")
                headers { headers }
            }
            else -> HttpRequestBuilder().apply {
                url("$baseUrl/mtype/chinese/page/$page/")
                headers { headers }
            }
        }
        val request = client.get(req).asJsoup()

        return novelsParse(request)
    }

    private fun novelsParse(document: Document): MangasPageInfo {
        val books = document.select(selectors()).map { element ->
            fromElement(element)
        }

        val hasNextPage = document.select(nextPageSelector()).text() == "Next »"

        return MangasPageInfo(books, hasNextPage)
    }

    fun detailRequest(manga: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(manga.key)
            headers { headers }
        }
    }

    fun searchRequest(
        page: Int,
        query: String,
        filters: List<Filter<*>>,
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url("$baseUrl/?s=$query&post_type=novel")
            headers { headers }
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return novelParsing(client.get(detailRequest(manga)).asJsoup())
    }

    private fun novelParsing(document: Document): MangaInfo {
        val name =
            document.select(".entry-title")
                .text().trim()
        val img = document.select(".thumb img").attr("src")
        val summary = document.select(".wd-full").last()?.text()?.trim()
        val genre = document.select(".wd-full").next().next().next().select("a").eachText()
            .filter { !it.contains("Genre") }
        val author = document.select(".wd-full").first()?.text()
        val status = document.select(".wd-full").next().text()
        val fStatus = when (status) {
            "OnGoing" -> MangaInfo.ONGOING
            "Complete" -> MangaInfo.COMPLETED
            else -> MangaInfo.ONGOING
        }

        return MangaInfo(
            title = name,
            cover = img,
            key = "",
            description = summary ?: "",
            genres = genre,
            author = author ?: "",
            status = fStatus
        )
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {

        val body: HttpResponse = client.get(Url(manga.key))
        return chaptersParse(body.asJsoup()).reversed()
    }

    private fun uniqueChaptersRequest(book: MangaInfo, page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(
                book.key + "page/$page/"
            )
            headers { headers }
        }
    }

    private fun chaptersParse(document: Document): List<ChapterInfo> {
        return document.select(chaptersSelector()).map { chapterFromElement(it) }
    }

    fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }

    fun chaptersSelector() = ".chbox a"

    fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.select("a").attr("href")
        val name = element.select("a .chapternum").text()
        val dateUploaded = element.select("a .chapterdate").text()

        return ChapterInfo(name = name, key = link, dateUpload = parseChapterDate(dateUploaded))
    }

    fun parseChapterDate(date: String): Long {
        return DateParser.parseRelativeOrAbsoluteDate(date)
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return getContents(chapter).map { Text(it) }
    }

    private suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get(contentRequest(chapter)).asJsoup())
    }

    fun pageContentParse(document: Document): List<String> {
        val header = document.select("h3").text()
        val body = document.select("#chaptercontent p").eachText()
        return listOf(header) + body
    }

    fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }
}
