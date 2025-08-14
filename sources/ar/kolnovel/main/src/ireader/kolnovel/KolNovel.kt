package ireader.kolnovel

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class KolNovel(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://kolnovel.site"
    override val id: Long
        get() = 41
    override val name: String
        get() = "KolNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf(
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
                endpoint = "/series/?page={page}&status=&order=latest",
                selector = "div.inmain div.mdthumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = "a.r"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}",
                selector = "div.inmain div.mdthumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = "a.next",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "Trending",
                endpoint = "/series/?page={page}&status=&order=popular",
                selector = "div.inmain div.mdthumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = "a.r"
            ),
            BaseExploreFetcher(
                "New",
                endpoint = "/series/?page={page}&order=update",
                selector = "div.inmain div.mdthumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = "a.rs"
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = "div.sertothumb img",
            coverAtt = "data-src",
            descriptionSelector = "div.entry-content[itemprop=description] p",
            authorBookSelector = "div.serl:contains(الكاتب) span a",
            categorySelector = "div.sertogenre a",
            statusSelector = "div.sertostat span",
            onStatus = { status ->
                if (status.contains("Ongoing")) {
                    MangaInfo.ONGOING
                } else if (status.contains("Hiatus")) {
                    MangaInfo.ON_HIATUS
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
            // reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".epheader",
            pageContentSelector = "div.entry-content p:not([style~=opacity]), div.entry-content ol li",
            onContent = { contents: List<String> ->
        contents.map { text ->
            text.replace("*إقرأ* رواياتنا* فقط* على* مو*قع م*لوك الرو*ايات ko*lno*vel ko*lno*vel. com", "", ignoreCase = true)
                .trim()
        }
    }
        )
}
