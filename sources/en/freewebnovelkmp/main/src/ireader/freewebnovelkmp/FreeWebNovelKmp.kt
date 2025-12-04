package ireader.freewebnovelkmp

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.common.utils.DateParser
import tachiyomix.annotations.Extension
import tachiyomix.annotations.SourceMeta

/**
 * FreeWebNovel KMP Source
 * 
 * This source is fully KMP-compatible and works on:
 * - Android (JVM)
 * - Desktop (JVM)
 * - iOS (via Kotlin/JS or Kotlin/Native in future)
 * 
 * Uses only KMP-compatible libraries:
 * - Ksoup for HTML parsing
 * - Ktor for HTTP requests
 * - kotlinx-datetime for date handling
 */
@Extension
@SourceMeta(
    description = "Read free web novels - KMP compatible",
    nsfw = false,
    tags = ["english", "novels", "webnovel"]
)
abstract class FreeWebNovelKmp(private val deps: Dependencies) : SourceFactory(deps) {

    override val lang: String = "en"
    override val baseUrl: String = "https://freewebnovel.com"
    override val id: Long = 200001L
    override val name: String = "FreeWebNovel (KMP)"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf("Latest", "Popular", "New", "Completed")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch()
    )

    // Explore fetchers for browsing novels
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/latest-release-novel/{page}",
                selector = ".li-row",
                nameSelector = ".tit a",
                coverSelector = ".pic img",
                coverAtt = "src",
                linkSelector = ".tit a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Popular",
                endpoint = "/most-popular-novel/{page}",
                selector = ".li-row",
                nameSelector = ".tit a",
                coverSelector = ".pic img",
                coverAtt = "src",
                linkSelector = ".tit a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Completed",
                endpoint = "/completed-novel/{page}",
                selector = ".li-row",
                nameSelector = ".tit a",
                coverSelector = ".pic img",
                coverAtt = "src",
                linkSelector = ".tit a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search/?searchkey={query}",
                selector = ".li-row",
                nameSelector = ".tit a",
                coverSelector = ".pic img",
                coverAtt = "src",
                linkSelector = ".tit a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                type = SourceFactory.Type.Search
            )
        )

    // Detail page parsing
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.tit",
            coverSelector = ".pic img",
            coverAtt = "src",
            authorBookSelector = ".item span[itemprop=author]",
            categorySelector = ".item a[href*=genre]",
            descriptionSelector = ".inner p",
            statusSelector = ".item span:contains(Status)"
        )

    // Chapter list parsing
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#idData li a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = false
        )

    // Chapter content parsing
    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1.tit",
            pageContentSelector = ".txt p",
            onContent = { content ->
                // Clean up content - remove ads and unwanted text
                content.filter { paragraph ->
                    !paragraph.contains("freewebnovel", ignoreCase = true) &&
                    !paragraph.contains("freeweb novel", ignoreCase = true) &&
                    paragraph.isNotBlank()
                }
            }
        )

    // Custom chapter parsing with KMP-compatible date handling
    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.attr("href").let { 
            if (it.startsWith("http")) it else "$baseUrl$it" 
        }
        val name = element.text().trim()
        
        return ChapterInfo(
            key = link,
            name = name,
            dateUpload = DateParser.now() // Use KMP-compatible date
        )
    }

    // Custom status parsing
    override fun statusParser(text: String): Long {
        return when {
            text.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
            text.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }
    }
}
