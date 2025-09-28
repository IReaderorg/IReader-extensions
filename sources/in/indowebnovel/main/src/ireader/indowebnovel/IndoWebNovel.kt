package ireader.indowebnovel

import io.ktor.client.request.post
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class IndoWebNovel(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://indowebnovel.id"
    override val id: Long
        get() = 71
    override val name: String
        get() = "IndoWebNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "SortBy:",
            arrayOf(
                "A-Z",
                "Z-A",
                "Latest Update",
                "Latest Added",
                "Popular",
                "Rating"
            ),
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                name = "A-Z",
                endpoint = "/advanced-search/?page={page}&order=title",
                selector = ".listupd .bs",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            ),
            BaseExploreFetcher(
                name = "Z-A",
                endpoint = "/advanced-search/?page={page}&order=titlereverse",
                selector = ".listupd .bs",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            ),
            BaseExploreFetcher(
                name = "Latest Update",
                endpoint = "/advanced-search/?page={page}&order=update",
                selector = ".listupd .bs",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            ),
            BaseExploreFetcher(
                name = "Latest Added",
                endpoint = "/advanced-search/?page={page}&order=latest",
                selector = ".listupd .bs",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            ),
            BaseExploreFetcher(
                name = "Popular",
                endpoint = "/advanced-search/?page={page}&order=popular",
                selector = ".listupd .bs",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            ),
            BaseExploreFetcher(
                name = "Rating",
                endpoint = "/advanced-search/?page={page}&order=rating",
                selector = ".listupd .bs",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            ),
            BaseExploreFetcher(
                name = "Search",
                endpoint = "/page/{page}/?s={query}",
                selector = ".flexbox2 .flexbox2-item",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".series-title h2",
            coverSelector = ".series-thumb img",
            coverAtt = "src",
            authorBookSelector = "li:contains(Author) span",
            categorySelector = "li:contains(Tags) span a",
            statusSelector = ".series-infoz .status",
            onStatus = { status ->
                val lowerStatus = status.lowercase()
                when {
                    lowerStatus.contains("ongoing") -> ONGOING
                    lowerStatus.contains("completed") -> COMPLETED
                    lowerStatus.contains("hiatus") -> ON_HIATUS
                    else -> ONGOING
                }
            },
            descriptionSelector = ".series-synops p",
        )
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".series-chapter li .flexch-infoz a",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".title-chapter",
            pageContentSelector = ".text-left p",
        )
}
