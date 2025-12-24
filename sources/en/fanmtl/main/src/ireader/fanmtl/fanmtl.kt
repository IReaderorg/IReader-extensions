package ireader.fanmtl

import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
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
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations

@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "douluo",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://www.fanmtl.com/novel/6954065.html",
    chapterUrl = "https://www.fanmtl.com/novel/6954065_1.html",
    expectedTitle = "Douluo Continent: Me! Qiu'er's fiancé, may we have many children and much happiness.",
    expectedAuthor = "琼梁"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 50,
    supportsPagination = true,
    requiresLogin = false
)
abstract class Fanmtl(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.fanmtl.com"
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
                "Latest",
                endpoint = "/list/all/all-newstime-{page}.html",
                selector = "ul > li",
                nameSelector = "h4",
                nameAtt = "",
                coverSelector = "figure img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                maxPage = 100,
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                onPage = { page -> (page - 1).toString() }
            ),
            BaseExploreFetcher(
                "Popular",
                endpoint = "/list/all/all-onclick-{page}.html",
                selector = "ul > li",
                nameSelector = "h4",
                nameAtt = "",
                coverSelector = "figure img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                maxPage = 100,
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                onPage = { page -> (page - 1).toString() }
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/e/search/index.php",
                selector = "li.novel-item",
                nameSelector = "a",
                nameAtt = "title",
                coverSelector = ".cover-wrap img",
                coverAtt = "data-src",
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            ),
        )

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value

        if (!query.isNullOrBlank()) {
            val response = client.submitForm(
                "$baseUrl/e/search/index.php",
                formParameters = Parameters.build {
                    append("show", "title")
                    append("tempid", "1")
                    append("tbname", "news")
                    append("keyboard", query)
                },
                encodeInQuery = true
            ) { headersBuilder() }.bodyAsText()

            val mangas = response.asJsoup().select("li.novel-item").map { html ->
                val name = html.select("a").attr("title").trim()
                val cover = html.select(".cover-wrap img").attr("data-src")
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
            nameSelector = "h1",
            coverSelector = "figure img",
            coverAtt = "src",
            addBaseurlToCoverLink = true,
            authorBookSelector = "div:contains(Author)",
            categorySelector = "ul li a[href*='/list/']",
            descriptionSelector = ".summary .content, .description",
            statusSelector = "strong:contains(Ongoing), strong:contains(Completed)",
            onStatus = { str ->
                when {
                    str.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    str.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ul > li > a[href*='_']",
            reverseChapterList = false,
            nameSelector = "strong",
            linkSelector = "",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h2",
            pageContentSelector = "article > div > div > p",
        )
}
