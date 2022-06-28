package ireader.mtlnation

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import okhttp3.OkHttpClient

import org.ireader.core_api.http.okhttp
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.*
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.util.concurrent.TimeUnit

@Extension
abstract class MtlNation(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://mtlnation.com"
    override val id: Long
        get() = 7
    override val name: String
        get() = "MtlNation"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:", arrayOf(
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
                selector = ".row .my-shadow ",
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
                endpoint = "/page/{page}/?s={query}&post_type=wp-manga",
                selector = ".c-tabs-item .row",
                nameSelector = "a",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts",
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
                when(status) {
                    "Completed"-> MangaInfo.COMPLETED
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
                title.replace("\\r","")
            }
        )


    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return client to HttpRequestBuilder().apply {
            url(url)
            headersBuilder()
        }
    }

    override suspend fun getContentRequest(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): Document {
        return deps.httpClients.browser.fetch(chapter.key,contentFetcher.pageTitleSelector,).responseBody.asJsoup()
    }


}