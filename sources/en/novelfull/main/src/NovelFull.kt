package ireader.novelfull

import io.ktor.client.request.*
import kotlinx.coroutines.*
import okhttp3.Headers
import org.ireader.core.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.source.Dependencies
import tachiyomi.source.model.*
import tachiyomix.annotations.Extension

@Extension
abstract class NovelFull(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "NovelFull"


    override val id: Long
        get() = 9999999999
    override val baseUrl = "https://novelfull.com"

    override val lang = "en"


    override fun getFilters(): FilterList {
        return listOf(
                Filter.Title(),
                Filter.Sort(
                        "Sort By:",arrayOf(
                        "Latest",
                        "Popular"
                )),
        )
    }
    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
        )
    }
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return getSearch(page,query)
        }
        return when(sorts) {
            0 -> getLatest(page)
            1 -> getPopular(page)
            else -> getLatest(page)
        }
    }

    suspend fun getLatest(page: Int) : MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchLatestEndpoint(page))
        return bookListParse(client.get<Document>(res),latestSelector(),latestNextPageSelector()) { latestFromElement(it) }
    }
    suspend fun getPopular(page: Int) : MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchPopularEndpoint(page))
        return bookListParse(client.get<Document>(res),popularSelector(),popularNextPageSelector()) { popularFromElement(it) }
    }
    suspend fun getSearch(page: Int,query: String) : MangasPageInfo {
        val res = requestBuilder(baseUrl + fetchSearchEndpoint(page,query))
        return bookListParse(client.get<Document>(res),searchSelector(),searchNextPageSelector()) { searchFromElement(it) }
    }


     fun fetchLatestEndpoint(page: Int): String? =
        "/latest-release-novel?page=$page"

     fun fetchPopularEndpoint(page: Int): String? =
        "/most-popular?page=$page"

     fun fetchSearchEndpoint(page: Int, query: String): String? =
        "/search?keyword=$query&page=$page"


    fun headersBuilder() = Headers.Builder().apply {
        add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
        )
        add("cache-control", "max-age=0")
        add("Referer", baseUrl)
    }

    override val headers: Headers = headersBuilder().build()



     fun popularSelector() = "div.archive div.row"

     fun popularFromElement(element: Element): MangaInfo {
        val url = baseUrl + element.select("h3.truyen-title a").attr("href")
        val title = element.select("h3.truyen-title a").attr("title")
        val thumbnailUrl = baseUrl + element.select(".col-xs-3 img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

     fun popularNextPageSelector() = "ul > li.last > a"


    // latest

     fun latestRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchLatestEndpoint(page)!!)
            headers { headers }
        }
    }

     fun latestSelector(): String = popularSelector()


     fun latestFromElement(element: Element): MangaInfo = popularFromElement(element)

     fun latestNextPageSelector() = popularNextPageSelector()

     fun searchSelector() = popularSelector()

     fun searchFromElement(element: Element): MangaInfo = popularFromElement(element)

     fun searchNextPageSelector(): String? = popularNextPageSelector()


    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select(".info-holder h3.title").text()
        val authorBookSelector = document.select(".info a").first()?.text()
        val cover = baseUrl + document.select(".book img").attr("src")
        val description = document.select(".desc-text p").eachText().joinToString("\n")
        //not sure why its not working.
        val category = document.select("div.info > div:nth-child(3) a")
            .eachText()

        val status = document.select("div.info > div:nth-child(5) a")
            .next()
            .text()
            .replace("/[\t\n]/g", "")
            .handleStatus()




        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector?:"",
            genres = category,
            key = "",
            status = status
        )
    }
    private fun String.handleStatus() : Int {
        return when(this){
            "OnGoing"-> MangaInfo.ONGOING
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

    override fun chaptersSelector() = "ul.list-chapter li a"

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").attr("title")

        return ChapterInfo(name = name, key = link)
    }

    private fun uniqueChaptersRequest(book: MangaInfo, page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(
                book.key + "?page=$page"
            )
            headers { headers }
        }
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {
               // val page = client.get<String>(chaptersRequest(book = book))
                val maxPage = parseMaxPage(manga)
                val list = mutableListOf<Deferred<List<ChapterInfo>>>()
                for (i in 1..maxPage) {
                    val pChapters = async {
                        chaptersParse(
                            client.get<String>(
                                uniqueChaptersRequest(
                                    book = manga,
                                    page = i
                                )
                            ).parseHtml()
                        )
                    }
                    list.addAll(listOf(pChapters))
                }
                //  val request = client.get<String>(chaptersRequest(book = book))

                return@withContext list.awaitAll().flatten()
            }
        }.getOrThrow()
    }

    suspend fun parseMaxPage(book: MangaInfo): Int {
        val page = client.get<String>(chaptersRequest(book = book)).parseHtml()
        val maxPage = page.select("li.last > a").attr("data-page")
        return maxPage.toInt()
    }


    override fun pageContentParse(document: Document): List<String> {
        return document.select("div.txt h4,p").eachText()
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


}