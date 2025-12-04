package ireader.ranobes

import kotlinx.serialization.json.Json

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.log.Log
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
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import ireader.core.util.DefaultDispatcher
import kotlinx.coroutines.withContext
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class Ranobes(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "Ranobes"

    override val id: Long
        get() = 13
    override val baseUrl = "https://ranobes.top"

    override val lang = "en"

    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            Filter.Sort(
                "Sort By:",
                arrayOf(
                    "Latest",
                    "Popular"
                )
            ),
        )
    }

    val agent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36"

    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
        )
    }

    val maxTimeout = 15000L

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override fun getCommands(): CommandList {
        return listOf(
            Command.Chapter.Select(
                "Get Chapters",
                options = arrayOf(
                    "None",
                    "Last 25 Chapter"
                ),
                value = 0
            ),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
            Command.Detail.Fetch(),
        )
    }

    suspend fun getLatest(page: Int): MangasPageInfo {
        var response: Document

        val html = client.get(requestBuilder(baseUrl + fetchLatestEndpoint(page)))
        response = html.asJsoup()

        return bookListParse(
            response,
            latestSelector(),
            latestNextPageSelector()
        ) { latestFromElement(it) }
    }

    suspend fun getPopular(page: Int): MangasPageInfo {

        var response = ""

        val html = client.get(requestBuilder("$baseUrl/ranking/cstart=$page&ajax=true"))
        response = html.asJsoup().html()

        return bookListParse(
            response.asJsoup(),
            popularSelector(),
            null
        ) { popularFromElement(it) }
    }

    fun fetchSearchEndpoint(page: Int, query: String): String? =
        "/index.php?do=search&subaction=search&search_start=0&full_search=0&result_from=1&story=$query"

    suspend fun getSearch(page: Int, query: String): MangasPageInfo {

        var response = ""

        val html = client.get(requestBuilder(baseUrl + fetchSearchEndpoint(page, query)))
        response = html.asJsoup().html()

        return bookListParse(
            response.asJsoup(),
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

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
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
        val thumbnailUrl = element
            .select("figure")
            .attr("style")
            .substringAfter("background-image: url(")
            .substringBefore(");")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun latestNextPageSelector() = ".icon-right"

    fun searchSelector() = ".shortstory"

    fun searchFromElement(element: Element): MangaInfo {
        val url = element.select(".title a").attr("href")
        val title = element.select(".title a:not(span)").text()
        val thumbnailUrl =
            element.select(".cont-in .cover").attr("style").substringAfter("background-image: url(")
                .substringBefore(");")
        val desc = element.select(".cont-in div").text()
        return MangaInfo(
            key = url,
            title = title,
            cover = thumbnailUrl,
            description = desc,
        )
    }

    fun searchNextPageSelector(): String? = popularLastPageSelector()

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val fetcher = commands.findInstance<Command.Detail.Fetch>()
        if (fetcher != null) {
            return detailParse(fetcher.html.asJsoup())
        }

        var response: Document;
        val html = client.get(manga.key)
        response = html.asJsoup();
        return detailParse(response)
    }

    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("h1.title").first()?.ownText()
        val authorBookSelector = document.select(".tag_list a").text()
        val cover = document.select(".r-fullstory-poster a").attr("href")
        val description =
            document.select(".cont-in .showcont-h .moreless__full").text()

        val category = document.select("#mc-fs-genre .links a")
            .eachText()

        val status = document.select(".r-fullstory-spec .grey").first()
            ?.text()
            ?.replace("/[\t\n]/g", "")
            ?.handleStatus()!!

        return MangaInfo(
            title = title ?: "",
            cover = cover,
            description = description,
            author = authorBookSelector ?: "",
            genres = category,
            key = "",
            status = status
        )
    }

    private fun String.handleStatus(): Long {
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

    fun mtlChapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href")
        val name = element.select("h6").text()
        return ChapterInfo(name = name, key = link)
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null) {
            return chaptersParse(chapterFetch.html.asJsoup()).reversed()
        }

        val command = commands.findInstance<Command.Chapter.Select>()

        val bookId = Regex("[0-9]+").find(manga.key)?.value

        if (command != null) {
            var response = ""

            val html =
                client.get(requestBuilder("$baseUrl/chapters/${bookId}/page/1/"))
            response = html.asJsoup().html().substringAfter("<script>window.__DATA__ = ")
                .substringBefore("</script>")
            val chapters = Json { ignoreUnknownKeys = true }.decodeFromString<ChapterDTO>(response)
//            response = deps.httpClients.browser.fetch(
//                        "https://ranobes.net/chapters/${bookId.first()}/page/1/",
//                        selector = chaptersSelector(),
//                        timeout = maxTimeout
//                    ).responseBody

            return chaptersParse(chapters)
        }

        val chapters = mutableListOf<ChapterInfo>()
        var currentPage = 1
        var res = ""
        val html =
            client.get(requestBuilder("https://ranobes.net/chapters/${bookId}/page/1/"))
        res = html.asJsoup().html().substringAfter("<script>window.__DATA__ = ")
            .substringBefore("</script>")
        Log.error { res }
        val json1 = Json { ignoreUnknownKeys = true }.decodeFromString<ChapterDTO>(res)
//        res = deps.httpClients.browser.fetch(
//                    "https://ranobes.net/chapters/${bookId.first()}/page/1/",
//                    selector = chaptersSelector(),
//                    timeout = maxTimeout
//                ).responseBody

        chapters.addAll(chaptersParse(json1))
        val maxPage: Int = json1.pages_count
        val list = mutableListOf<ChapterInfo>()
        withContext(DefaultDispatcher) {
            for (i in 1..maxPage) {
                val response = client.get(
                    "$baseUrl/chapters/${json1.book_id}/page/$i/"
                ).asJsoup().html().substringAfter("<script>window.__DATA__ = ")
                    .substringBefore("</script>")

                val json2 = Json { ignoreUnknownKeys = true }.decodeFromString<ChapterDTO>(response)

                list.addAll(
                    chaptersParse(
                        json2
                    )
                )
            }
        }
        return list.reversed()
    }

    override fun chaptersParse(document: Document): List<ChapterInfo> {
        val mainChapters = document.select(chaptersSelector()).map { chapterFromElement(it) }
        if (mainChapters.isNotEmpty()) {
            return mainChapters.map { it.copy(key = it.key.substringAfter(baseUrl)) }
        }
        return document.select("#dle-content a").map { mtlChapterFromElement(it) }.reversed()
    }

    fun chaptersParse(chapterDTO: ChapterDTO): List<ChapterInfo> {
        return chapterDTO.chapters.map { chater ->
            ChapterInfo(
                key = "$baseUrl/read-${chater.id}.html",
                name = chater.title,
            )
        }
    }

    override fun pageContentParse(document: Document): List<String> {
        return document.select(".shortstory h1,h3,p").eachText()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val fetcher = commands.findInstance<Command.Content.Fetch>()
        if (fetcher != null) {
            return pageContentParse(fetcher.html.asJsoup()).map { Text(it) }
        }
        var response = ""

        val htmlPage = client.get(chapter.key)
        response = htmlPage.asJsoup().html()

//        response = deps.httpClients.browser.fetch(
//            chapter.key,
//            selector = ".shortstory h1,p",
//            userAgent = agent,
//            timeout = maxTimeout
//        ).responseBody
        return pageContentParse(Ksoup.parse(response)).map {
            Text(
                it
            )
        }
    }

    class LatestListing() : Listing("Latest")

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
