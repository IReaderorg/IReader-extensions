package ireader.chireads

import io.ktor.client.request.post
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class Chireads(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "fr"
    override val baseUrl: String
        get() = "https://chireads.com"
    override val id: Long
        get() = 79
    override val name: String
        get() = "Chireads"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getListRequest(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String
    ): Document {
        val name =  super.getListRequest(baseExploreFetcher, page, query)

        Log.error { name.html() }

        return name
    }
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/category/translatedtales/page/{page}/",
                selector = "#content li",
                nameSelector = ".news-list-tit h5 a",
                linkSelector = ".news-list-tit h5 a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 50,
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?x=0&y=0&name={query}",
                selector = "#content li",
                nameSelector = ".news-list-tit h5 a",
                nameAtt = "title",
                linkSelector = ".news-list-tit h5 a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 0,
                type = SourceFactory.Type.Search
            ),


        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".inform-title",
            coverSelector = ".inform-product img",
            coverAtt = "src",
            descriptionSelector = ".inform-inform-txt",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapitre-table a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".article-title",
            pageContentSelector = "#content p",
        )

    override fun getUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    }
}
