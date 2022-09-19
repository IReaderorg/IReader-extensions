package ireader.novelscafes

import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class NovelsCafe(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://novelscafe.com"
    override val id: Long
        get() = 36
    override val name: String
        get() = "NovelsCafe"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
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
                "Trending",
                endpoint = "/completed-novel/page/{page}/",
                selector = ".posts.row .post-column",
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                nameSelector = ".post-title",
                coverSelector = "img",
                coverAtt = "src",
                addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/?s={query}",
                selector = ".posts.row .post-column",
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                nameSelector = ".post-title",
                coverSelector = "img",
                coverAtt = "src",
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "#primary > div.row.mt-5 > div.info-wrapper.d-flex.col-12.col-md-9.pt-5.mb-5 > div.col-12.col-md-4.pb-5 > img",
            coverAtt = "src",
            authorBookSelector = "#primary > div.row.mt-5 > div.info-wrapper.d-flex.col-12.col-md-9.pt-5.mb-5 > div.col-12.col-md-8.mt-1.pb-5 > div:nth-child(3) > div > h2 > a",
            categorySelector = "#primary > div.row.mt-5 > div.info-wrapper.d-flex.col-12.col-md-9.pt-5.mb-5 > div.col-12.col-md-8.mt-1.pb-5 > div:nth-child(6) h2",
            descriptionSelector = "#description p",
            statusSelector = "#primary > div.row.mt-5 > div.info-wrapper.d-flex.col-12.col-md-9.pt-5.mb-5 > div.col-12.col-md-8.mt-1.pb-5 > div.counting-header > span:nth-child(4) > strong",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".row .text-truncate",
            nameSelector = ".chapter-title",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true

        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1",
            pageContentSelector = "#chapter-content",
        )
}
