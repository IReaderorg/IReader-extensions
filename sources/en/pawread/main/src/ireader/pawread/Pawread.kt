package ireader.pawread

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
import ireader.core.source.findInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class Pawread(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://m.pawread.com"
    override val id: Long
        get() = 85
    override val name: String
        get() = "Pawread"

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
                endpoint = "/list_{page}/",
                selector = ".list-comic",
                nameSelector = "a.txtA",
                linkSelector = "a.txtA",
                linkAtt = "href",
                addBaseUrlToLink = true,
                coverSelector = "a img",
                coverAtt = "src",
                maxPage = 20
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search/?keywords={query}",
                selector = ".UpdateList .itemBox",
                nameSelector = "a.title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                maxPage = 0,
                type = SourceFactory.Type.Search
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "#comicName",
            coverSelector = "#Cover img",
            coverAtt = "src",
            descriptionSelector = "#full-des",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".filtr-item .item-box",
            nameSelector = "span:first-child",
            linkSelector = "span:nth-child(2)",
            linkAtt = "onclick",
            onLink = {
                    "https://m.pawread.com"+it.substringAfter("'").substringBefore("'")
            },
        )

    // if request is from client then reverse list ,if not then do not change the order
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { command ->
            return chaptersParse(Jsoup.parse(command.html)).let { if (chapterFetcher.reverseChapterList) it.reversed() else it }
        }
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {
                val chapters =
                    chaptersParse(
                        getChapterListRequest(manga, commands),
                    )
                return@withContext if (chapterFetcher.reverseChapterList) chapters else chapters.reversed()
            }
        }.getOrThrow().reversed()
    }

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter_name",
            pageContentSelector = ".content p",
        )
}
