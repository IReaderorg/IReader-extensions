package ireader.mtlarchive

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class MtlNation(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://mtlarchive.com"
    override val id: Long
        get() = 7
    override val name: String
        get() = "Mtlarchive"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf(
                "Latest",
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
                endpoint = "/library?query=&sort=novel_new&page={page}",
                selector = ".row .my-shadow",
                nameSelector = ".content h3",
                linkSelector = ".content a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                coverSelector = ".content a img",
                coverAtt = "src",
                nextPageSelector = ".block",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/library?query={query}&sort=chapter_new&page={page}",
                selector = ".row .my-shadow",
                nameSelector = ".content h3",
                linkSelector = ".content a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                coverSelector = ".content a img",
                coverAtt = "src",
                nextPageSelector = ".block",
                type = Type.Search
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.q-mt-xs",
            coverSelector = ".text-center img",
            coverAtt = "src",
            authorBookSelector = "div:nth-child(1) > div.text-subtitle1.text-bold.text-grey-8",
            categorySelector = ".q-my-xs a",
            descriptionSelector = ".text-synopsis p",
            statusSelector = ".row.q-mx-auto.q-my-md.justify-center.text-left > div:nth-child(3) > div.text-subtitle1.text-bold.text-grey-8",
            onStatus = { status ->
                when (status) {
                    "Completed" -> MangaInfo.COMPLETED
                    else -> MangaInfo.ONGOING
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapters a",
            nameSelector = ".text-bold",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = false
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "#item-0 > div:nth-child(2)",
            pageContentSelector = ".font-poppins p",
            onTitle = { title ->
                title.replace("\\r", "")
            }
        )

    override suspend fun getMangaDetailsRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return deps.httpClients.browser.fetch(
            manga.key,
            "section.content-novel-mobile h1",
            timeout = 50000
        ).responseBody.asJsoup()
    }

    override suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return deps.httpClients.browser.fetch(
            manga.key,
            chapterFetcher.nameSelector,
        ).responseBody.asJsoup()
    }

    override suspend fun getListRequest(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String
    ): Document {
        when (baseExploreFetcher.key) {
            "Search" -> {
                return deps.httpClients.browser.fetch(
                    baseUrl + baseExploreFetcher.endpoint?.replace(
                        "{page}",
                        page.toString()
                    )?.replace("{query}", query.replace(" ", "+")),
                    "div:nth-child(1) > div.row > div.col-9 > div > a",
                    timeout = 50000
                ).responseBody.asJsoup()
            }
            else -> {
                return deps.httpClients.browser.fetch(
                    baseUrl + baseExploreFetcher.endpoint?.replace(
                        "{page}",
                        page.toString()
                    )?.replace("{query}", query.replace(" ", "+")),
                    "div:nth-child(1) > div.content > h3 > a",
                    timeout = 50000
                ).responseBody.asJsoup()
            }
        }
    }

    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return deps.httpClients.cloudflareClient to HttpRequestBuilder().apply {
            url(url)
            headersBuilder()
        }
    }

    override suspend fun getContentRequest(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): Document {
        return deps.httpClients.browser.fetch(
            chapter.key,
            contentFetcher.pageTitleSelector,
            timeout = 50000
        ).responseBody.asJsoup()
    }
}
