package ireader.kolnovel

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class KolNovel(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://kolnovel.com"
    override val id: Long
        get() = 41
    override val name: String
        get() = "KolNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:", arrayOf(
                "Latest",
                "Popular",
                "New",
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
                endpoint = "/series/?page={page}&status=&type=&order=update",
                selector = "article.bs div.bsx",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a div.limit img",
                coverAtt = "src",
                nextPageSelector = "a.r"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}",
                selector = "article.bs div.bsx",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a div.limit img",
                coverAtt = "src",
                nextPageSelector = "a.next",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "Trending",
                endpoint = "/series/?page={page}&status=&order=popular",
                selector = "article.bs div.bsx",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a div.limit img",
                coverAtt = "src",
                nextPageSelector = "a.rs"
            ),
            BaseExploreFetcher(
                "New",
                endpoint = "/series/?page={page}&order=update",
                selector = "article.bs div.bsx",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a div.limit img",
                coverAtt = "src",
                nextPageSelector = "a.rs"
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".infox h1",
            coverSelector = ".thumb img",
            coverAtt = "src",
            descriptionSelector = "div.entry-content[itemprop=description] p",
            authorBookSelector = "span:contains(المؤلف) a",
            categorySelector = "div.genxed a",
            statusSelector = "span:contains(الحالة)",
            onStatus = { status ->
                if (status.contains("Ongoing")) {
                    MangaInfo.ONGOING
                } else {
                    MangaInfo.COMPLETED
                }
            },
        )
    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
            )
            append(HttpHeaders.Referrer, baseUrl)
        }
    }


    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li[data-id]",
            nameSelector = "a div.epl-num ,a div.epl-title",
            linkSelector = "a",
            linkAtt = "href",
            //reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".epheader",
            pageContentSelector = "div.entry-content p:not([style~=opacity]), div.entry-content ol li",
        )



}
