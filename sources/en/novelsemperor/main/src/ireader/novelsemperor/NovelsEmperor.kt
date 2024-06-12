package ireader.novelsemperor

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class NovelsEmperor(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://novelsemperor.com"
    override val id: Long
        get() = 90
    override val name: String
        get() = "NovelsEmperor"

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
                "Latest",
                endpoint = "/series?page={page}",
                selector = ".rounded-lg a",
                nameSelector = "h2",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/series?title={query}&type=&status=",
                selector = ".rounded-lg a",
                nameSelector = "h2",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                type = SourceFactory.Type.Search
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div:nth-child(2) > div:nth-child(3) > h2",
            coverSelector = ".rounded-lg .relative img.object-cover",
            coverAtt = "src",
            authorBookSelector = "p:nth-child(4) > span.capitalize",
            categorySelector = "p:nth-child(2) > span.capitalize",
            descriptionSelector = "#description",
            statusSelector = "p:nth-child(1) > span.capitalize",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#chapters-list a",
            nameSelector = "div.flex.gap-2 > span",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".chap-content",
        )

    override fun pageContentParse(document: Document): List<Page> {
        val content = document.select(".chap-content").html().substringAfter("</div>")
            .substringAfter("</div>").substringBefore("<div class=\"").split("<br>")
            .filter { it.isNotBlank() }.map { Text(it.trim()) }
        return content
    }


}
