package ireader.sunovels

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
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
// // import java.util.Locale - Not needed for KMP - Not needed for KMP

@Extension
abstract class Sunovels(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    
    override val baseUrl: String
        get() = "https://sunovels.com"
    
    override val id: Long
        get() = 42
    override val name: String
        get() = "Sunovels"

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
                endpoint = "/series/?page={page}&order=latest",
                selector = "div.series-item, div.manga-item",
                nameSelector = "a.title, h3 a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail",
                coverAtt = "data-src",
                nextPageSelector = "a.next.page-numbers, .pagination .next"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search/?q={query}&page={page}",
                selector = "div.series-item, div.manga-item",
                nameSelector = "a.title, h3 a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail",
                coverAtt = "data-src",
                nextPageSelector = "a.next.page-numbers, .pagination .next",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "Popular",
                endpoint = "/series/?page={page}&order=popular",
                selector = "div.series-item, div.manga-item",
                nameSelector = "a.title, h3 a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail",
                coverAtt = "data-src",
                nextPageSelector = "a.next.page-numbers, .pagination .next"
            ),
            BaseExploreFetcher(
                "New",
                endpoint = "/series/?page={page}&order=new",
                selector = "div.series-item, div.manga-item",
                nameSelector = "a.title, h3 a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail",
                coverAtt = "data-src",
                nextPageSelector = "a.next.page-numbers, .pagination .next"
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.title, .series-title",
            coverSelector = "img.cover, .series-cover img",
            coverAtt = "data-src",
            descriptionSelector = "div.description, .synopsis p",
            authorBookSelector = ".author a, span.author",
            categorySelector = ".genres a, .tags a",
            statusSelector = ".status span",
            onStatus = { status ->
                val lowerStatus = status.lowercase(Locale.ROOT)
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
            selector = "ul.chapters li, .chapter-list li",
            nameSelector = "a.chapter-title, a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title, h2",
            pageContentSelector = "div.content p, div.reader p, article p",
            onContent = { contents: List<String> ->
                contents.map { htmlText ->
                    val cleaned = htmlText.replace(
                        Regex("(?i)(?:(?:إقرأ|اقرأ)\\s*رواياتنا\\s*فقط\\s*على\\s*موقع\\s*(?:sunovels|الروايات|novel)\\.?\\s*(?:com|site)?(?:\\s*\\*?)+)"),
                        ""
                    ).trim()
                    
                    if (cleaned.contains("<img", ignoreCase = true)) {
                        val doc = Ksoup.parseBodyFragment(cleaned)
                        doc.setBaseUri(baseUrl)
                        val images = doc.select("img")
                        images.forEach { img ->
                            var src = img.attr("src").takeIf { it.isNotEmpty() } ?: img.attr("data-src").takeIf { it.isNotEmpty() }
                            if (src != null && !src.startsWith("http")) {
                                src = baseUrl.removeSuffix("/") + "/" + src.trimStart('/')
                            }
                            if (src != null) {
                                img.attr("src", src)
                            }
                            if (img.attr("alt").isEmpty()) {
                                img.attr("alt", "صورة من الرواية")
                            }
                        }
                        doc.body().html()
                    } else {
                        cleaned
                    }
                }.filter { it.isNotBlank() }
            }
        )
}
