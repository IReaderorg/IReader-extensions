package ireader.royalroad

import io.ktor.client.HttpClient
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class RoyalRoad(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.royalroad.com"
    override val id: Long
        get() = 49
    override val name: String
        get() = "RoyalRoad"

    override val client: HttpClient
        get() = deps.httpClients.cloudflareClient
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
            endpoint = "/fictions/$endpoint?page={page}",
            selector = ".fiction-list-item",
            nameSelector = ".fiction-title a",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = ".fiction-title a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            maxPage = 2000,
        )
    }
    fun search() :BaseExploreFetcher{
        return BaseExploreFetcher(
            "Search",
            endpoint = "/fictions/search?page={page}}&title={query}",
            selector = ".fiction-list-item",
            nameSelector = ".fiction-title a",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = ".fiction-title a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            maxPage = 2000,
            type = SourceFactory.Type.Search
        )
    }
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            fetcherCreator("Last Update","latest-updates"),
            fetcherCreator("Trending","trending"),
            fetcherCreator("Best Rated","best-rated"),
            fetcherCreator("Popular","active-popular"),
            fetcherCreator("Complete","complete"),
            fetcherCreator("Complete","complete"),
            fetcherCreator("Weekly Popular","weekly-popular"),
            fetcherCreator("New Releases","new-releases"),
            fetcherCreator("New Releases","new-releases"),
            fetcherCreator("Rising Stars","rising-stars"),
            fetcherCreator("Writathon","writathon"),
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
            nameSelector = "h1.font-white",
            coverSelector = ".thumbnail",
            coverAtt = "src",
            authorBookSelector = "h4.font-white",
            categorySelector = ".margin-bottom-10 span a.label",
            descriptionSelector = ".description p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#chapters .chapter-row",
            reverseChapterList = true,
            nameSelector = "td:first-child a",
            linkSelector = "td:first-child a",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".chapter-content p",
        )
}
