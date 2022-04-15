package ireader.ranobes

import io.ktor.client.request.*
import io.ktor.http.*
import okhttp3.OkHttpClient
import org.ireader.core.*
import org.ireader.core_api.http.okhttp
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

abstract class Ranobes(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "Ranobes"


    override val id: Long
        get() = 999999951
    override val baseUrl = "https://ranobes.net"

    override val lang = "en"


    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            Filter.Sort(
                "Sort By:", arrayOf(
                    "Latest",
                    "Popular"
                )
            ),
        )
    }

    val agent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36"

    private fun clientBuilder(): OkHttpClient = deps.httpClients.default.okhttp
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
            DetailParse(),
            ChaptersParse(),
            ChapterParse()
        )
    }


    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        var manga = MangasPageInfo(emptyList(), false)
        sort?.name?.let {
            val cmd = parseWebViewCommand(sort.name)
            if (cmd?.html?.contains(
                    "Our system has detected abnormal activity"
                ) == true
            ) {
                throw Exception("AntiBot System is Active, please open the app in WebView")
            }
            when (cmd?.mode) {
                "1" -> {
                    manga = booksRequestReceiver(
                        Jsoup.parse(cmd.html ?: ""),
                        popularSelector(),
                        popularLastPageSelector()
                    ) { popularFromElement(it) }

                }
                "0" -> {
                    manga = booksRequestReceiver(
                        Jsoup.parse(cmd.html ?: ""),
                        latestSelector(),
                        latestNextPageSelector()
                    ) { latestFromElement(it) }

                }
                "3" -> {
                    manga = booksRequestReceiver(
                        Jsoup.parse(cmd.html ?: ""),
                        searchSelector(),
                        searchNextPageSelector()
                    ) { searchFromElement(it) }

                }
                else -> {
                    manga = booksRequestSender(
                        urL = baseUrl + fetchLatestEndpoint(page),
                        "article:nth-child(1) > div.short-cont > h2 > a",
                        mode = "0"
                    )
                }
            }

        }
        return manga

    }


    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return booksRequestSender(
                urL = baseUrl + "/catlist%5B%5D=1&story=${
                    URLEncoder.encode(
                        query,
                        "utf-8"
                    )
                }&do=search&subaction=search",
                "article:nth-child(2) > div.short-cont > h2 > a",
                mode = "3"
            )

        }

        return when (sorts) {
            0 -> booksRequestSender(
                urL = baseUrl + fetchLatestEndpoint(page),
                "article:nth-child(1) > div.short-cont > h2 > a",
                mode = "0"
            )
            1 -> booksRequestSender(
                urL = baseUrl + fetchPopularEndpoint(page),
                "#ranking-content > article:nth-child(1) > h2 > a",
                mode = "1"
            )
            else -> booksRequestSender(
                urL = baseUrl + fetchLatestEndpoint(page),
                "article:nth-child(1) > div.short-cont > h2 > a",
                mode = "0"
            )
        }
    }

    class UnSupported : Exception("This Extension is fetch-only-type")


    fun fetchLatestEndpoint(page: Int): String? =
        "/novels/page/$page/"

    fun fetchPopularEndpoint(page: Int): String? =
        "cstart=$page&ajax=true"


    override fun HttpRequestBuilder.headersBuilder() {
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
        val title = element.select(".title a").text()
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

    fun latestSelector(): String = ".block"


    fun latestFromElement(element: Element): MangaInfo {
        val url = element.select("a").attr("href")
        val title = element.select("a:not(span):").text()
        val thumbnailUrl =
            element.select("figure").attr("style").replace("background-image:url(", "")
                .replace(")", "")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun latestNextPageSelector() = ".icon-right"

    fun searchSelector() = ".shortstory"

    fun searchFromElement(element: Element): MangaInfo {
        val url = element.select(".title a").attr("href")
        val title = element.select(".title a:not(span)").text()
        val thumbnailUrl = baseUrl + element.select(".cont-in .cover").attr("style")
            .replace("background-image:url(", "").replace(")", "")
        val desc = element.select(".cont-in div").text()
        val genre = element.select(".shortstory .ellipses").next().text().split(", ")
        return MangaInfo(
            key = url,
            title = title,
            cover = thumbnailUrl,
            description = desc,
            genres = genre
        )
    }

    fun searchNextPageSelector(): String? = popularLastPageSelector()


    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        return when {
            manga.key.contains(PARSE_DETAIL) -> detailParse(Jsoup.parse(manga.artist))
            else -> {
                val cmd = buildWebViewCommand(
                    manga.artist,
                    ajaxSelector = "div.r-fullstory-s1 > h1",
                    0,
                    mode = PARSE_DETAIL
                )
                return MangaInfo("", "", artist = cmd)
            }
        }
    }

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
            author = authorBookSelector ?: "",
            genres = category,
            key = "",
            status = status
        )
    }

    private fun String.handleStatus(): Int {
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
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").attr("title")

        return ChapterInfo(name = name, key = link)
    }


    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        if (manga.key == PARSE_CHAPTERS) {
            return chaptersParse(Jsoup.parse(manga.title))
        }
        val cmd = parseWebViewCommand(manga.artist)
        when (cmd?.mode) {
            "2" -> {
                val html = Jsoup.parse(manga.artist)
                chapters.addAll(chaptersRequestReceiver(
                    html,
                    "cat_line"
                ) { chapterFromElement(it) })
                val lastPage = html.select(".pages a[href]").last()?.text()

                chapters.addAll(chaptersRequestSender(
                    "null",
                    selector = ".ccline",
                    mode = "2",
                    maxPage = lastPage?:"1"
                ))
            }
            "null" -> {
                val url = Regex("[0-9]+").findAll(manga.key)
                    .map(MatchResult::value)
                    .toList()
                chapters.addAll(chaptersRequestSender(
                    "https://ranobes.net/chapters/${url.first()}/page/{page}/",
                    selector = ".ccline",
                    mode = "2"
                ))
            }
            else -> {}
        }
        return chapters
    }


    override fun pageContentParse(document: Document): List<String> {
        return document.select(".shortstory h1,p").eachText()
    }

    override suspend fun getPageList(chapter: ChapterInfo): List<Page> {
        return when {
            chapter.scanlator.contains(PARSE_CONTENT) -> pageContentParse(Jsoup.parse(chapter.scanlator)).map {
                Text(
                    it
                )
            }
            else -> {
                val cmd = buildWebViewCommand(
                    chapter.key,
                    ajaxSelector = "#arrticle > p:nth-child(1)",
                    0,
                    mode = PARSE_CONTENT
                )
                return listOf(Text(cmd))
            }
        }
    }

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