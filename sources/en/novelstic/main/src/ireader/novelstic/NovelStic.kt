package ireader.novelstic

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import tachiyomix.annotations.Extension

@Extension
abstract class NovelStic(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://novelstic.com"
    override val id: Long
        get() = 31
    override val name: String
        get() = "NovelStic"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf(
                "Latest",
                "Popular",
                "New",
                "Most Views",
                "Rating",
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/manga/page/{page}/?m_orderby=latest",
                selector = ".page-item-detail",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}&post_type=wp-manga",
                selector = ".c-tabs-item__content",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts",
                onQuery = {
                    it.replace(" ", "+")
                },
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "Trending",
                endpoint = "/manga/page/{page}/?m_orderby=trending",
                selector = ".page-item-detail .item-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),
            BaseExploreFetcher(
                "New",
                endpoint = "/manga/page/{page}/?m_orderby=new-manga",
                selector = ".page-item-detail .item-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),
            BaseExploreFetcher(
                "Most Views",
                endpoint = "/manga/page/{page}/?m_orderby=views",
                selector = ".page-item-detail .item-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),
            BaseExploreFetcher(
                "Rating",
                endpoint = "/manga/page/{page}/?m_orderby=rating",
                selector = ".page-item-detail .item-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".post-title",
            coverSelector = ".summary_image a img",
            coverAtt = "data-src",
            authorBookSelector = ".author-content a",
            categorySelector = ".genres-content a",
            descriptionSelector = ".summary__content p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".version-chap li.wp-manga-chapter",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".cha-tit",
            pageContentSelector = ".text-left h3,p ,.cha-content .pr .dib p",
        )
}
