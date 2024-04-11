package ireader.coolnovel

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
abstract class CoolNovel(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.novelcool.com"
    override val id: Long
        get() = 86
    override val name: String
        get() = "Cool Novel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf(
                "Latest",
                "Popular",
                "New",
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
                endpoint = "/latest.html",
                selector = ".book-item",
                nameSelector = ".book-pic",
                nameAtt = "title",
                linkSelector = ".book-pic a",
                linkAtt = "href",
                coverSelector = ".book-pic a img",
                coverAtt = "src",
                maxPage = 1
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search/?wd={query}",
                selector = ".book-item",
                nameSelector = ".book-pic",
                nameAtt = "title",
                linkSelector = ".book-pic a",
                linkAtt = "href",
                coverSelector = ".book-pic a img",
                coverAtt = "src",
                maxPage = 1,
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "Trending",
                endpoint = "/popular.html",
                selector = ".book-item",
                nameSelector = ".book-pic",
                nameAtt = "title",
                linkSelector = ".book-pic a",
                linkAtt = "href",
                coverSelector = ".book-pic a img",
                coverAtt = "src",
                maxPage = 1
            ),
            BaseExploreFetcher(
                "New",
                endpoint = "/new_list.html",
                selector = ".book-item",
                nameSelector = ".book-pic",
                nameAtt = "title",
                linkSelector = ".book-pic a",
                linkAtt = "href",
                coverSelector = ".book-pic a img",
                coverAtt = "src",
                maxPage = 1
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "bookinfo-title",
            coverSelector = ".bookinfo-pic img",
            coverAtt = "src",
            descriptionSelector = ".bk-summary-txt",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chp-item",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(

            pageTitleSelector = ".chapter-title",
            pageContentSelector = "p",
            onContent = {
                it.map { it.trim() }
            }
        )

}
