package ireader.fanmtl

import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class Fanmtl(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://fanmtl.com"
    override val id: Long
        get() = 40
    override val name: String
        get() = "FanMtl"

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
                "Trending",
                endpoint = "/list/all/all-newstime-{page}.html",
                selector = ".novel-item",
                nameSelector = "a",
                nameAtt = "title",
                coverSelector = ".cover-wrap img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                maxPage = 39,
                addBaseUrlToLink = true,
               addBaseurlToCoverLink = true
            ),BaseExploreFetcher(
                "Trending",
                endpoint = "/list/all/all-onclick-{page}.html",
                selector = ".novel-item",
                nameSelector = "a",
                nameAtt = "title",
                coverSelector = ".cover-wrap img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                maxPage = 39,
                addBaseUrlToLink = true,
               addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "https://www.fanmtl.com/e/search/index.php",
                selector = ".col-novel-main > .list-novel .row",
                nameSelector = ".novel-title",
                coverSelector = ".cover",
                coverAtt = "src",
                linkSelector = ".novel-title > a",
                linkAtt = "href",
                addBaseUrlToLink = false,
                type = SourceFactory.Type.Search
            ),
        )
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value

        if (query != null) {
            val response =
                client.submitForm(
                    "https://www.fanmtl.com/e/search/index.php",
                    formParameters = Parameters.build {
                        append("show", "title")
                        append("tempid", "1")
                        append("tbname", "news")
                        append("keyboard", query)
                    },
                    encodeInQuery = true
                ) { headersBuilder() }.bodyAsText()
            Log.error { response }
            val mangas = response.asJsoup().select(".novel-item").map { html ->
                val name = html.select(".a").text().trim()
                val cover = html.select(".cover-wrap img").attr("src")
                val url = baseUrl + html.select("a").attr("href")
                MangaInfo(
                    key = url,
                    title = name,
                    cover = cover
                )
            }

            return MangasPageInfo(
                mangas = mangas,
                hasNextPage = false
            )
        }
        return super.getMangaList(filters, page)
    }
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.novel-title",
            coverSelector = ".cover img",
            coverAtt = "src",
            addBaseurlToCoverLink = true,
            // We are using nth-last-child(x) because there are some
            // novels which can have alternative names that is added
            // on top of this .info list
            authorBookSelector = ".author",
            categorySelector = ".categories li a.property-item",
            descriptionSelector = ".summary .content",
            statusSelector = "#novel > header > div > div.novel-info > div.header-stats > span:nth-child(2) > strong",
            onStatus = { str ->
                when (str) {
                    "Completed" -> MangaInfo.COMPLETED
                    "Ongoing" -> MangaInfo.ONGOING
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#chapters .chapter-list li",
            reverseChapterList = false,
            nameSelector = ".chapter-title",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            numberSelector = ".chapter-no",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".titles h2",
            pageContentSelector = ".chapter-content p",
        )

}
