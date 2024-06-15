package ireader.genesistranslator

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
abstract class GenesisTranslator(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://genesistls.com"
    override val id: Long
        get() = 91
    override val name: String
        get() = "GenesisTranslator"

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
                endpoint = "/series/?page={page}&status=&type=&order=update",
                selector = ".bs",
                nameSelector = "a",
                nameAtt = "title",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}",
                selector = ".bs",
                nameSelector = "a",
                nameAtt = "title",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                type = SourceFactory.Type.Search
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".entry-title",
            coverSelector = ".thumb img",
            coverAtt = "src",
            authorBookSelector = "div.spe > span:nth-child(2) > a",
            categorySelector = ".genxed a",
            descriptionSelector = ".entry-content p",
            statusSelector = "div.spe > span:nth-child(2) > a",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".eplisterfull ul li",
            nameSelector = ".epl-title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".entry-content p",
            onContent = {
                it.filter { it.isNotEmpty()  }
            }
        )



}
