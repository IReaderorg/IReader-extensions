package ireader.readnovelfull

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import tachiyomix.annotations.Extension

/**
 * ReadNovelFull multisrc base class
 * Supports sites like ReadNovelFull, AllNovel, NovelFull, LibRead, NovelBin, etc.
 */
@Extension
abstract class ReadNovelFullScraper(
    private val deps: Dependencies,
    private val sourceId: Long,
    private val key: String,
    private val sourceName: String,
    private val language: String,
    private val options: ReadNovelFullOptions = ReadNovelFullOptions()
) : SourceFactory(deps = deps) {

    override val lang: String get() = language
    override val baseUrl: String get() = key
    override val id: Long get() = sourceId
    override val name: String get() = sourceName

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf("Latest", "Popular", "Rating", "New")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )


    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/${options.latestPage}?page={page}",
                selector = ".archive .row, .col-content .row, .list-novel .row",
                nameSelector = "h3.novel-title a, .novel-title a, h3 a",
                nameAtt = "title",
                coverSelector = "img",
                coverAtt = "data-src",
                linkSelector = "h3.novel-title a, .novel-title a, h3 a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/${options.searchPage}?keyword={query}",
                selector = ".archive .row, .col-content .row, .list-novel .row",
                nameSelector = "h3.novel-title a, .novel-title a, h3 a",
                nameAtt = "title",
                coverSelector = "img",
                coverAtt = "data-src",
                linkSelector = "h3.novel-title a, .novel-title a, h3 a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".books .title, h3.title, .book-info .title",
            coverSelector = ".books .book img, .book img, .book-info img",
            coverAtt = "data-src",
            addBaseurlToCoverLink = true,
            authorBookSelector = ".info span[title=Author], .info a[href*=author], span.author",
            categorySelector = ".info span[title=Genre] a, .info a[href*=genre], .genres a",
            descriptionSelector = ".desc-text, .inner, .summary .content",
            statusSelector = ".info span[title=Status], .info span.status",
            onStatus = { status ->
                when {
                    status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    status.contains("Hiatus", ignoreCase = true) -> MangaInfo.ON_HIATUS
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#list-chapter li a, .list-chapter li a, ul.chapter-list li a",
            nameSelector = "span, .chapter-text",
            linkSelector = "",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = false
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chr-title, .chapter-title, h2",
            pageContentSelector = "#chr-content p, #chapter-content p, .chapter-content p, .txt p"
        )


    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return if (options.postSearch) {
                getSearchPost(query, page)
            } else {
                super.getMangaList(filters, page)
            }
        }
        return super.getMangaList(filters, page)
    }

    private suspend fun getSearchPost(query: String, page: Int): MangasPageInfo {
        val searchKey = options.searchKey
        val doc = client.submitForm(
            url = "$baseUrl/${options.searchPage}",
            formParameters = Parameters.build {
                append(searchKey, query)
            }
        ) {
            headersBuilder()
        }.asJsoup()

        return parseNovelList(doc)
    }

    private fun parseNovelList(document: Document): MangasPageInfo {
        val novels = document.select(".archive .row, .col-content .row, .list-novel .row").mapNotNull { element ->
            parseNovelFromElement(element)
        }
        val hasNext = document.select(".pagination .next, .pagination li:last-child:not(.disabled)").isNotEmpty()
        return MangasPageInfo(novels, hasNext)
    }

    private fun parseNovelFromElement(element: Element): MangaInfo? {
        val linkElement = element.selectFirst("h3.novel-title a, .novel-title a, h3 a") ?: return null
        val title = linkElement.attr("title").ifBlank { linkElement.text() }
        val url = linkElement.attr("href")
        val imgElement = element.selectFirst("img")
        val cover = imgElement?.attr("data-src")?.ifBlank { imgElement.attr("src") } ?: ""

        return MangaInfo(
            key = if (url.startsWith("http")) url else "$baseUrl$url",
            title = title,
            cover = if (cover.startsWith("http")) cover else "$baseUrl$cover"
        )
    }


    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let {
            if (it.html.isNotBlank()) {
                return parseChaptersFromHtml(Ksoup.parse(it.html))
            }
        }

        val document = client.get(requestBuilder(manga.key)).asJsoup()

        // Try AJAX chapter loading if not noAjax
        if (!options.noAjax) {
            val novelId = document.selectFirst("#rating")?.attr("data-novel-id")
                ?: document.selectFirst("input#id_post")?.attr("value")
                ?: extractNovelIdFromUrl(manga.key)

            if (novelId != null) {
                val chapterListing = options.chapterListing
                val chapterParam = options.chapterParam
                val ajaxUrl = "$baseUrl/$chapterListing?$chapterParam=$novelId"

                try {
                    val ajaxDoc = client.get(requestBuilder(ajaxUrl)).asJsoup()
                    val chapters = parseChaptersFromAjax(ajaxDoc)
                    if (chapters.isNotEmpty()) {
                        return chapters
                    }
                } catch (e: Exception) {
                    // Fall back to page parsing
                }
            }
        }

        return parseChaptersFromHtml(document)
    }

    private fun extractNovelIdFromUrl(url: String): String? {
        val regex = Regex("\\d+")
        return regex.find(url)?.value
    }

    private fun parseChaptersFromHtml(document: Document): List<ChapterInfo> {
        return document.select("#list-chapter li a, .list-chapter li a, ul.chapter-list li a, #idData li a").mapNotNull { element ->
            val name = element.selectFirst("span, .chapter-text")?.text()?.trim()
                ?: element.attr("title").ifBlank { element.text().trim() }
            val url = element.attr("href")
            if (name.isBlank() || url.isBlank()) return@mapNotNull null

            ChapterInfo(
                name = name,
                key = if (url.startsWith("http")) url else "$baseUrl$url"
            )
        }
    }

    private fun parseChaptersFromAjax(document: Document): List<ChapterInfo> {
        return document.select("a, option").mapNotNull { element ->
            val url = element.attr("href").ifBlank { element.attr("value") }
            val name = element.attr("title").ifBlank { element.text().trim() }
            if (name.isBlank() || url.isBlank()) return@mapNotNull null

            ChapterInfo(
                name = name,
                key = if (url.startsWith("http")) url else "$baseUrl$url"
            )
        }
    }

    override fun pageContentParse(document: Document): List<Page> {
        val title = document.selectFirst(".chr-title, .chapter-title, h2")?.text()?.trim() ?: ""
        val content = document.select("#chr-content p, #chapter-content p, .chapter-content p, .txt p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                // Fallback: split by <br> tags
                document.selectFirst("#chr-content, #chapter-content, .chapter-content, .txt")
                    ?.html()?.split("<br>")?.map { Ksoup.parse(it).text().trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()
            }

        return (listOf(title) + content).filter { it.isNotBlank() }.map { it.toPage() }
    }
}

/**
 * Configuration options for ReadNovelFull-based sites
 */
data class ReadNovelFullOptions(
    val latestPage: String = "latest-release-novel",
    val searchPage: String = "search",
    val chapterListing: String = "ajax/chapter-archive",
    val chapterParam: String = "novelId",
    val searchKey: String = "keyword",
    val postSearch: Boolean = false,
    val noAjax: Boolean = false,
    val pageAsPath: Boolean = false
)
