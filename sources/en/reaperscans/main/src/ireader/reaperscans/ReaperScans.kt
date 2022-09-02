package ireader.reaperscans

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.ParsedHttpSource
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.Command
import org.ireader.core_api.source.model.Filter
import org.ireader.core_api.source.model.FilterList
import org.ireader.core_api.source.model.Listing
import org.ireader.core_api.source.model.MangaInfo
import org.ireader.core_api.source.model.MangasPageInfo
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class ReaperScans(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = "ReaperScans"
    override val id: Long = 15

    override val baseUrl = "https://reaperscans.com"

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

    class LatestListing() : Listing("Latest")

    override fun getListings(): List<Listing> {
        return listOf(LatestListing())
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return getSearch(page, query)
        }
        return getLatest(page)
    }

    suspend fun getLatest(page: Int): MangasPageInfo {
        val response = client.submitForm(
            "https://reaperscans.com/wp-admin/admin-ajax.php",
            formParameters = Parameters.build {
                append("action", "madara_load_more")
                append("page", (page - 1).toString())
                append("template", "madara-core/content/content-archive")
                append("vars[meta_key]", "_latest_update")
                append("vars[meta_query][0][0][key]", "_wp_manga_chapter_type")
                append("vars[meta_query][0][0][value]", "text")
                append("vars[meta_query][0][relation]", "AND")
                append("vars[meta_query][relation]", "OR")
                append("vars[order]", "desc")
                append("vars[orderby]", "meta_value_num")
                append("vars[paged]", "1")
                append("vars[post_status]", "publish")
                append("vars[post_type]", "wp-manga")
                append("vars[sidebar]", "full")
                append("vars[template]", "archive")
            }
        )
        val books = bookListParse(
            response.asJsoup(), latestSelector(),
            null
        ) { this.latestFromElement(it) }
        return MangasPageInfo(books.mangas, books.mangas.isNotEmpty())
    }

    suspend fun getSearch(page: Int, query: String): MangasPageInfo {
        val response = client.get(requestBuilder("$baseUrl/?s=$query&post_type=wp-manga"))
        return bookListParse(response.asJsoup(), searchSelector(), null) { searchFromElement(it) }
    }

    fun fetchPopularEndpoint(page: Int): String? =
        "/all-series/novels/"

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

    fun latestSelector() = ".page-item-detail"

    fun latestFromElement(element: Element): MangaInfo {
        val title = element.select("h3 a").text()
        val url = element.select("a").attr("href")
        val thumbnailUrl = element.select("img").attr("data-src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    fun searchSelector() = "div.c-tabs-item__content"

    fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("h3.h4 a").text()
        val url = element.select("div.tab-thumb a").attr("href")
        val thumbnailUrl = element.select("div.tab-thumb a img").attr("data-src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }

    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("div.post-title h1").text()
        val cover = document.select(".summary_image img").attr("data-src")
        val authorBookSelector = document.select("div.author-content a").text()
        val description =
            document.select("div.description-summary div.summary__content p").eachText()
                .joinToString("\n\n")
        val category = document.select("div.genres-content a").eachText()
        val rating = document.select("div.post-rating span.score").text()
        val status = document.select("div.post-status div.summary-content").last()?.text()

        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector,
            genres = category,
            key = "",
            status = parseStatus(status ?: "")
        )
    }

    private fun parseStatus(string: String): Int {
        return when {
            "OnGoing" in string -> MangaInfo.ONGOING
            "Completed" in string -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }
    }

    // chapters
    override fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }

    override fun chaptersSelector() = ".main .wp-manga-chapter .chapter-link"

    override fun chapterFromElement(element: Element): ChapterInfo {

        val link = element.select("a").attr("href")
        val name = element.select(".chapter-manhwa-title").text()

        return ChapterInfo(name = name, key = link)
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val response = client.get(requestBuilder(manga.key))

        return chaptersParse(response.asJsoup()).reversed()
    }

    override fun pageContentParse(document: Document): List<String> {
        return document.select(".reading-content p").eachText()
    }

    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get(requestBuilder(chapter.key)).asJsoup())
    }

    override fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }
}
