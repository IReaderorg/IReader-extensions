package ireader.rewayatfans

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
abstract class RewayatFans(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://rewayatfans.com"
    override val id: Long
        get() = 42
    override val name: String
        get() = "روايات فانز"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "الترتيب حسب:",
            arrayOf(
                "الأحدث",
                "الشائعة",
                "المكتملة",
                "الجديدة"
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
                "الأحدث",
                endpoint = "/?page={page}",
                selector = "ul li, .recent-list li, div.post-list li",
                nameSelector = "a, li",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next, .pagination a:contains(2), Pages a"
            ),
            BaseExploreFetcher(
                "البحث",
                endpoint = "/?s={query}&page={page}",
                selector = "ul li:has(a), .search-results li",
                nameSelector = "a",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "الشائعة",
                endpoint = "/novels/?page={page}&order=popular",
                selector = "ul li, .novel-list li",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next"
            ),
            BaseExploreFetcher(
                "المكتملة",
                endpoint = "/completed/?page={page}",
                selector = "ul li:contains(النهاية), .completed li",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next"
            ),
            BaseExploreFetcher(
                "الجديدة",
                endpoint = "/?page={page}&order=new",
                selector = "ul li:contains(new), .new-list li",
                nameSelector = "a",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next"
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title, .novel-title, h1",
            coverSelector = "div.thumb img, .cover img, img[src*='cover']",
            coverAtt = "src",
            descriptionSelector = "div.entry-content p, .description p, .summary",
            authorBookSelector = ".author span a, div:contains(الكاتب) a",
            categorySelector = ".genres a, .tags a, div.sertogenre a",
            statusSelector = ".status span, div.sertostat span, .novel-status",
            onStatus = { status ->
                if (status.contains("مكتمل", ignoreCase = true) || status.contains("النهاية") || status.contains("Completed") || status.contains("The End")) {
                    MangaInfo.COMPLETED
                } else if (status.contains("مستمر", ignoreCase = true) || status.contains("Ongoing")) {
                    MangaInfo.ONGOING
                } else if (status.contains("توقف", ignoreCase = true) || status.contains("Hiatus")) {
                    MangaInfo.ON_HIATUS
                } else {
                    MangaInfo.UNKNOWN
                }
            },
        )

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.AcceptLanguage, "ar-SA,ar;q=0.9,en-US;q=0.8,en;q=0.7")
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
    }

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ul.chapter-list li, li[data-id], .chapters li",
            nameSelector = "a, div.epl-title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".epheader, h1.chapter-title, .post-title",
            pageContentSelector = "div.entry-content p, .chapter-content p, ol li",
            onContent = { contents: List<String> ->
                contents.map { text ->
                    text.replace(
                        Regex("(?i).*new.*|.*النهاية.*|.*The End.*|.*اقرأ.*فقط.*على.*rewayatfans.*com.*|.*إعلان.*"),
                        ""
                    )
                        .replace(Regex("\\s+"), " ")
                        .trim { it.isWhitespace() || it == '*' }
                        .takeIf { it.length > 20 && !it.contains("Discord", ignoreCase = true) } ?: ""
                }.filter { it.isNotEmpty() }
            }
        )
}
