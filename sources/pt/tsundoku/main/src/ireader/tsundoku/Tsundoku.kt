package ireader.tsundoku

import io.ktor.client.request.get
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangasPageInfo
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class Tsundoku(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "pt"
    override val baseUrl: String get() = "https://tsundoku.com.br"
    override val id: Long get() = 79
    override val name: String get() = "Tsundoku Traduções"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Order by",
            arrayOf("Default", "A-Z", "Z-A", "Update", "Latest", "Popular")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    private val orderValues = arrayOf("", "title", "titlereverse", "update", "latest", "popular")

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.filterIsInstance<Filter.Title>().firstOrNull()?.value
        val sortIndex = filters.filterIsInstance<Filter.Sort>().firstOrNull()?.value?.index ?: 0
        
        val url = buildString {
            append("$baseUrl/manga/?")
            if (page > 1) append("page=$page&")
            append("type=novel")
            if (!query.isNullOrBlank()) {
                append("&title=$query")
            } else {
                val order = orderValues.getOrElse(sortIndex) { "" }
                if (order.isNotEmpty()) append("&order=$order")
            }
        }
        
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: Document): MangasPageInfo {
        val novels = document.select(".listupd .bsx").mapNotNull { element ->
            val name = element.select(".tt").text().trim()
            val link = element.select("a").attr("href")
            val cover = element.select("img").attr("src")
            
            if (name.isNotBlank() && link.isNotBlank()) {
                MangaInfo(
                    key = link.removePrefix(baseUrl),
                    title = name,
                    cover = cover
                )
            } else null
        }
        
        val hasNext = document.select(".hpage .r").isNotEmpty()
        return MangasPageInfo(novels, hasNext)
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/manga/?type=novel&order=latest&page={page}",
                selector = ".listupd .bsx",
                nameSelector = ".tt",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = false,
                addBaseurlToCoverLink = false
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/manga/?type=novel&title={query}&page={page}",
                selector = ".listupd .bsx",
                nameSelector = ".tt",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = false,
                addBaseurlToCoverLink = false,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = ".main-info .thumb img",
            coverAtt = "src",
            descriptionSelector = ".entry-content.entry-content-single div",
            authorBookSelector = ".tsinfo .imptdt:contains(Autor)",
            categorySelector = ".mgen a",
            statusSelector = ".tsinfo .imptdt:contains(Status)",
            addBaseurlToCoverLink = false,
            onStatus = { status ->
                when {
                    status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#chapterlist ul > li",
            nameSelector = ".chapternum",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = false,
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".headpost .entry-title",
            pageContentSelector = "#readerarea"
        )
}
