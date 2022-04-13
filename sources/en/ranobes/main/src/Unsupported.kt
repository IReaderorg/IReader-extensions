package ireader.ranobes

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.internal.wait
import org.ireader.core.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.source.Dependencies
import tachiyomi.source.model.*
import tachiyomix.annotations.Extension


abstract class UnsupportedRenobes(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "Ranobes"


    override val id: Long
        get() = 999999951
    override val baseUrl = "https://ranobes.net"

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

    fun popularRequest(url:String) : HttpRequestBuilder{
        return HttpRequestBuilder().apply {
            url(url)
            headers {
                append(HttpHeaders.UserAgent, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36")
                append(HttpHeaders.CacheControl, "max-age=0")
            }

        }
    }

    suspend fun getLatest(page: Int) : MangasPageInfo {

        val response = client.submitForm<HttpResponse>(
            url=baseUrl + fetchLatestEndpoint(page),
            formParameters = Parameters.build {
                append("ajax", "true")
                append("cstart", "$page")
            },
            encodeInQuery = true
        ) {
            headers {
                append(HttpHeaders.UserAgent, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36")
                append(HttpHeaders.CacheControl, "max-age=0")
            }
        }
        return bookListParse(response.asJsoup(),latestSelector(),latestNextPageSelector()) { latestFromElement(it) }
    }
    suspend fun getPopular(page: Int) : MangasPageInfo {
        val response = client.submitForm<HttpResponse>(
            url=baseUrl + fetchPopularEndpoint(page),
            formParameters = Parameters.build {
                append("ajax", "true")
                append("cstart", "$page")
            },
            encodeInQuery = true
        ) {
            headers {
                append(HttpHeaders.UserAgent, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36")
                append(HttpHeaders.CacheControl, "max-age=0")
            }
        }
        return bookListParse(response.asJsoup(),popularSelector(),popularLastPageSelector()) { popularFromElement(it) }
    }
    suspend fun getSearch(page: Int,query: String) : MangasPageInfo {
        val response = client.submitForm<HttpResponse>(
            url=baseUrl + fetchSearchEndpoint(page,query),
            formParameters = Parameters.build {
                append("do", "search")
                append("full_search", "0")
                append("result_from", "1")
                append("search_start", "0")
                append("story", query)
                append("subaction", "search")
            },
            encodeInQuery = true
        ) {
            headers {
                append(HttpHeaders.UserAgent, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36")
                append(HttpHeaders.CacheControl, "max-age=0")
            }
        }
        return bookListParse(response.asJsoup(),searchSelector(),searchNextPageSelector()) { searchFromElement(it) }
    }


    fun fetchLatestEndpoint(page: Int): String? =
        "/updates/page/$page/"

    fun fetchPopularEndpoint(page: Int): String? =
        "/ranking"

    fun fetchSearchEndpoint(page: Int, query: String): String? =
        "/index.php?do=search"



    override fun HttpRequestBuilder.headersBuilder() {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }



    fun popularSelector() = ".rank-story"

    fun popularFromElement(element: Element): MangaInfo {
        val url = element.select(".title a").attr("href")
        val title = element.select(".title a").text()
        val thumbnailUrl = baseUrl + element.select(".fit-cover img").attr("src")
        val desc = element.select(".moreless__full").text()
        val genre = element.select(".rank-story-genre .small a").eachText()
        return MangaInfo(key = url, title = title, cover = thumbnailUrl, description = desc, genres = genre)
    }

    fun popularLastPageSelector() = ".ranking__empty"


    // latest

    fun latestRequest(page: Int): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(baseUrl + fetchLatestEndpoint(page)!!)
            headers { headers }
        }
    }

    fun latestSelector(): String = ".block"


    fun latestFromElement(element: Element): MangaInfo  {
        val url = baseUrl + element.select("a").attr("href")
        val title = element.select("h3").text()
        val thumbnailUrl = baseUrl + element.select(".i .image").attr("style").replace("background-image:url(","").replace(")","")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun latestNextPageSelector() = ".icon-right"

    fun searchSelector() = ".shortstory"

    fun searchFromElement(element: Element): MangaInfo {
        val url = element.select(".title a").attr("href")
        val title = element.select(".title a:not(span)").text()
        val thumbnailUrl = baseUrl + element.select(".cont-in .cover").attr("style").replace("background-image:url(","").replace(")","")
        val desc = element.select(".cont-in div").text()
        val genre = element.select(".shortstory .ellipses").next().text().split(", ")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl, description = desc, genres = genre)
    }

    fun searchNextPageSelector(): String? = popularLastPageSelector()


    // manga details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("h1.title").text()
        val authorBookSelector = document.select(".tag_list a").text()
        val cover = baseUrl + document.select(".r-fullstory-poster a").attr("href")
        val description = document.select(".cont-in .showcont-h[itemprop=\"description\"]").text()

        val category = document.select(".links[itemprop=\"genre\"] a")
            .eachText()

        val status = document.select(".r-fullstory-spec .grey").first()
            ?.text()
            ?.replace("/[\t\n]/g", "")
            ?.handleStatus()!!




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
            "Completed"-> MangaInfo.COMPLETED
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
        val maxPage = page.select("li.last > a").attr("data-page")
        return maxPage.toInt()
    }


    override fun pageContentParse(document: Document): List<String> {
        return document.select(".shortstory h1,p").eachText()
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