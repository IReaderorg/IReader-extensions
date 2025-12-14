package ireader.novelparadise

import io.ktor.client.HttpClient
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class NovelParadise(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://novelsparadise.site"
    override val id: Long
        get() = 50
    override val name: String
        get() = "NovelParadise"

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

    fun fetcherCreator(name:String, endpoint:String) :BaseExploreFetcher{
        return BaseExploreFetcher(
            name,
            endpoint = "/series/?page={page}&status=&type=&order=$name",
            selector = ".maindet",
            nameSelector = ".mdinfo h2 a",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = ".mdinfo h2 a",
            linkAtt = "href",
            maxPage = 50,
        )
    }
    fun search() :BaseExploreFetcher{
        return BaseExploreFetcher(
            "Search",
            endpoint = "/page/{page}}/?s={query}",
            selector = ".maindet",
            nameSelector = ".mdinfo h2 a",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = ".mdinfo h2 a",
            linkAtt = "href",
            maxPage = 50,
            type = SourceFactory.Type.Search
        )
    }
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            fetcherCreator("Last Update","update"),
            search()

        )

    override suspend fun getListRequest(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String
    ): Document {
        val re=  super.getListRequest(baseExploreFetcher, page, query)
        Log.error { re.html() }
        return re
    }

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = ".sertothumb img",
            coverAtt = "src",
            authorBookSelector = ".serl:nth-child(3) .serval",
            categorySelector = ".sertogenre a",
            descriptionSelector = ".sersysn p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".eplisterfull li a",
            nameSelector = ".epl-num",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "entry-title",
            pageContentSelector = ".entry-content p",
        )
}
