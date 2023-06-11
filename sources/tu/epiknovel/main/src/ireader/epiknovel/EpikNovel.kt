package ireader.epiknovel

import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class EpikNovel(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "tu"
    override val baseUrl: String
        get() = "https://www.epiknovel.com"
    override val id: Long
        get() = 84
    override val name: String
        get() = "EpikNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
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
                endpoint = "/seri-listesi?Sayfa={page}",
                selector = "div.col-lg-12.col-md-12",
                nameSelector = "h3",
                linkSelector = "'h3 > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
                maxPage = 30
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/seri-listesi?q={query}",
                selector = "div.col-lg-12.col-md-12",
                nameSelector = "h3",
                linkSelector = "'h3 > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
                maxPage = 30,
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1#tables",
            coverSelector = "img.manga-cover",
            coverAtt = "src",
            descriptionSelector = "#wrapper > div.row > div.col-md-9 > div:nth-child(6) > p:nth-child(3)",
            statusSelector = "#wrapper > div.row > div.col-md-9 > div.row > div.col-md-9 > h4:nth-child(3) > a",
            authorBookSelector = "#NovelInfo > p:nth-child(4)",
            onStatus = { status ->
                if (status.contains("Completed")) {
                    MangaInfo.COMPLETED
                } else {
                    MangaInfo.ONGOING
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "table tr",
            nameSelector = "td:nth-child(1) > a",
            onName = {chapterName ->
                chapterName
            },
            linkSelector = "td:nth-child(1) > a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "#icerik > center > h4 > b",
            pageContentSelector = "div#icerik p",
        )

    override fun getUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36"
    }
}
