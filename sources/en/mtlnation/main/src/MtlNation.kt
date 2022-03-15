package ireader.mtlnation

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import okhttp3.Headers
import org.ireader.core.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.source.Dependencies
import tachiyomi.source.model.*
import tachiyomix.annotations.Extension

//not working
@Extension
abstract class MtlNation(deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "MtlNation"


    override val id: Long
        get() = 488631435
    override val baseUrl = "https://mtlnation.com"

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
        val res = requestBuilder("$baseUrl/novel/page/${page}/?m_orderby=latest")
        return bookListParse(client.get<HttpResponse>(res).asJsoup(),"div.page-item-detail","a.last") { popularFromElement(it) }
    }
    suspend fun getPopular(page: Int) : MangasPageInfo {
        val res = requestBuilder("$baseUrl/novel/page/2/?m_orderby=views")
        return bookListParse(client.get<HttpResponse>(res).asJsoup(),"div.page-item-detail","a.last") { popularFromElement(it) }
    }
    suspend fun getSearch(page: Int,query: String) : MangasPageInfo {
        val res = requestBuilder("$baseUrl/search/?searchkey=$query")
        return bookListParse(client.get<HttpResponse>(res).asJsoup(),"div.ul-list1 div.li-row",null) { searchFromElement(it) }
    }


    override fun HttpRequestBuilder.headersBuilder() {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }


    fun popularFromElement(element: Element): MangaInfo {

        val url = baseUrl + element.select("h3.h5 a").attr("href")
        val title = element.select("h3.h5 a").text()
        val thumbnailUrl = element.select("img").attr("src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }



    fun searchFromElement(element: Element): MangaInfo {
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
        //not sure why its not working.
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

    private fun String.handleStatus(): Int {
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

    fun uniqueChaptersRequest(book: MangaInfo, page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(
                book.key.replace("/${page - 1}.html", "").replace(".html", "")
                    .plus("/$page.html")
            )
            headers { headers }
        }
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {
                val page = client.get<HttpResponse>(chaptersRequest(book = manga))
                val maxPage = parseMaxPage(manga)
                val list = mutableListOf<Deferred<List<ChapterInfo>>>()
                for (i in 1..maxPage) {
                    val pChapters = async {
                        chaptersParse(
                            client.get<HttpResponse>(
                                uniqueChaptersRequest(
                                    book = manga,
                                    page = i
                                )
                            ).asJsoup()
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
        val page = client.get<HttpResponse>(chaptersRequest(book = book)).asJsoup()
        val maxPage = page.select("#indexselect option").eachText().size
        return maxPage
    }


    override fun pageContentParse(document: Document): List<String> {
        return document.select("div.txt h4,p").eachText()
    }

    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get<HttpResponse>(contentRequest(chapter)).asJsoup())
    }


    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }


}