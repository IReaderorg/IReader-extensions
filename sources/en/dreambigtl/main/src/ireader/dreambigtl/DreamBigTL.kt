package ireader.dreambigtl

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class DreamBigTL(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.dreambigtl.com"
    override val id: Long
        get() = 90 // Choose a unique ID
    override val name: String
        get() = "Dream Big Translations"

    override fun getFilters(): FilterList = listOf(
        Filter.Title()
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "New Novels",
                endpoint = "/search/label/New%20Novels",
                selector = ".blog-posts.index-post-wrap .blog-post.hentry.index-post",
                nameSelector = ".entry-title a",
                coverSelector = ".entry-image",
                coverAtt = "data-image",
                linkSelector = ".entry-title a",
                linkAtt = "href"
            ),
            BaseExploreFetcher(
                "Ongoing Novels",
                endpoint = "/search/label/Ongoing%20Novels",
                selector = ".blog-posts.index-post-wrap .blog-post.hentry.index-post",
                nameSelector = ".entry-title a",
                coverSelector = ".entry-image",
                coverAtt = "data-image",
                linkSelector = ".entry-title a",
                linkAtt = "href"
            ),
            BaseExploreFetcher(
                "Completed Novels",
                endpoint = "/search/label/Completed%20Novels",
                selector = ".blog-posts.index-post-wrap .blog-post.hentry.index-post",
                nameSelector = ".entry-title a",
                coverSelector = ".entry-image",
                coverAtt = "data-image",
                linkSelector = ".entry-title a",
                linkAtt = "href"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?q={query}",
                selector = ".blog-posts.index-post-wrap .blog-post.hentry.index-post",
                nameSelector = ".entry-title a",
                coverSelector = ".entry-image",
                coverAtt = "data-image",
                linkSelector = ".entry-title a",
                linkAtt = "href",
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = ".post-body img",
            descriptionSelector = ".post-body p:first-child",
            statusSelector = ".post-body",
            onStatus = { status ->
                // Determine status based on URL path
                when {
                    status.contains("completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    else -> MangaInfo.ONGOING
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-panel ul li, h2:contains(\"List of Chapters\") + ul li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".post-body"
        )
} 