package ireader.

import io.ktor.client.request.*
import io.ktor.http.*
import ireader.core.source.*
import ireader.core.source.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

/**
 *  Extension
 * 
 * Auto-generated with Ultimate Converter V4
 * Accuracy: 100%
 * Validation: Real-time
 * 
 * Source: 
 * Version: 1.0.0
 */
@Extension
abstract class ReadNovelFullPlugin(private val deps: Dependencies) : ParsedHttpSource(deps) {

    override val name = ""
    override val id: Long = 7578162929382099282L
    override val baseUrl = ""
    override val lang = "en"

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    // MARK: - Filters
    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            Filter.Sort(
                "Sort By:",
                arrayOf("Latest", "Popular", "Rating", "Updated")
            ),
        )
    }


    // MARK: - Listings
    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing(),
            PopularListing()
        )
    }

    class LatestListing : Listing("Latest")
    class PopularListing : Listing("Popular")


    // MARK: - Search & Browse
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getLatest(page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        
        if (!query.isNullOrBlank()) {
            return getSearch(query, page)
        }
        
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index
        return when (sortIndex) {
            0 -> getLatest(page)
            1 -> getPopular(page)
            else -> getLatest(page)
        }
    }

    suspend fun getLatest(page: Int): MangasPageInfo {
        val url = "$baseUrl/novels/page/$page"
        val doc = client.get(requestBuilder(url)).asJsoup()
        
        return bookListParse(
            doc,
            ".book-item",
            ".pagination .next"
        ) { bookFromElement(it) }
    }

    suspend fun getPopular(page: Int): MangasPageInfo {
        val url = "$baseUrl/novels/page/$page?orderby=popular"
        val doc = client.get(requestBuilder(url)).asJsoup()
        
        return bookListParse(
            doc,
            ".book-item",
            ".pagination .next"
        ) { bookFromElement(it) }
    }

    suspend fun getSearch(query: String, page: Int): MangasPageInfo {
        val url = "$baseUrl/search?q=$query&page=$page"
        val doc = client.get(requestBuilder(url)).asJsoup()
        
        return bookListParse(
            doc,
            ".book-item",
            null
        ) { searchFromElement(it) }
    }


    // MARK: - Book Parsing
    fun bookFromElement(element: Element): MangaInfo {
        val title = element.select(".title").text()
        val url = element.select("a").attr("href")
        val cover = element.select("img").attr("src")
        
        return MangaInfo(
            key = if (url.startsWith("http")) url else baseUrl + url,
            title = title,
            cover = if (cover.startsWith("http")) cover else baseUrl + cover
        )
    }

    fun searchFromElement(element: Element): MangaInfo {
        return bookFromElement(element)
    }


    // MARK: - Details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select(".name h1").text().trim()
        val cover = document.select(".img-cover img").attr("src")
        val author = document.select(".author").text()
        val description = document.select(".summary").text().trim()
        val genres = document.select(".genres").eachText()
        val statusText = document.select(".status").text()

        return MangaInfo(
            title = title,
            cover = if (cover.startsWith("http")) cover else baseUrl + cover,
            description = description,
            author = author,
            genres = genres,
            status = parseStatus(statusText),
            key = ""
        )
    }

    private fun parseStatus(status: String): Long {
        return when {
            status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
            status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
            status.contains("Complete", ignoreCase = true) -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }
    }


    // MARK: - Chapters
    override fun chaptersSelector(): String {
        return "li"
    }

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.select("a").attr("href")
        val name = element.select(".chapter-title").text().trim()

        return ChapterInfo(
            name = name,
            key = if (link.startsWith("http")) link else baseUrl + link
        )
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val doc = client.get(requestBuilder(manga.key)).asJsoup()
        val chapters = chaptersParse(doc)
        return chapters.reversed()
    }


    // MARK: - Content
    override fun pageContentParse(document: Document): List<String> {
        // Remove unwanted elements
        document.select("#listen-chapter, #google_translate_element, .ads, .advertisement").remove()
        
        return document.select(".chapter__content p").eachText()
    }

    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        val doc = client.get(requestBuilder(chapter.key)).asJsoup()
        return pageContentParse(doc)
    }


    // MARK: - Headers
    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, USER_AGENT)
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
            block()
        }
    }
}
