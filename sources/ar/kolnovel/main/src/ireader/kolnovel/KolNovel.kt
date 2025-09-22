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
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangaInfo.Companion.ON_HIATUS
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
                nextPageSelector = "a.next.page-numbers"
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
                nextPageSelector = "a.next.page-numbers",
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
                nextPageSelector = "a.next.page-numbers"
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
                nextPageSelector = "a.next.page-numbers"
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = "div.sertothumb img",
            coverAtt = "data-src",
            descriptionSelector = "div.entry-content p",
            authorBookSelector = "div.serl:contains(الكاتب) span a",
            categorySelector = "div.sertogenre a",
            statusSelector = "div.sertostat span",
            onStatus = { status ->
                val lowerStatus = status.lowercase()
                when {
                    lowerStatus.contains("ongoing") || lowerStatus.contains("مستمرة") -> ONGOING
                    lowerStatus.contains("hiatus") || lowerStatus.contains("متوقفة") -> ON_HIATUS
                    lowerStatus.contains("completed") || lowerStatus.contains("مكتملة") -> COMPLETED
                    else -> ONGOING
                }
            },
        )

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            append("Sec-Fetch-Mode", "navigate")
            append("Sec-Fetch-Site", "same-origin")
            append("Sec-Fetch-User", "?1")
            block()
        }
    }

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li[data-id]",
            nameSelector = "a div.epl-num, a div.epl-title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".epheader",
            pageContentSelector = "div.entry-content p:not([style~=opacity]), div.entry-content ol li",
            onContent = { contents: List<String> ->
                contents.map { text ->
                    text.replace(
                        Regex("(?i)\\*?إقرأ\\s*رواياتنا\\s*فقط\\s*على\\s*موقع\\s*ملك\\s*الروايات\\s*koll?novel\\.?\\s*koll?novel\\.com?"),
                        ""
                    ).trim()
                }.filter { it.isNotBlank() }
            }
        )
}
