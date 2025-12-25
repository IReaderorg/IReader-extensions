package ireader.rewayatclub

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
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
class RewayatClub(deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "ar"

    override val baseUrl: String
        get() = "https://rewayat.club"

    override val id: Long
        get() = 6745632189101

    override val name: String
        get() = "RewayatClub"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "التصنيف",
            arrayOf(
                "الجميع",
                "خيال علمي",
                "رومانسي",
                "فانتازيا"
            )
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "latest",
                endpoint = "/page/{page}/",
                selector = "article",
                nameSelector = "h2.entry-title a",
                linkSelector = "h2.entry-title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
                nextPageSelector = "a.next.page-numbers"
            ),
            BaseExploreFetcher(
                "search",
                endpoint = "/?s={query}&page={page}",
                selector = "article",
                nameSelector = "h2.entry-title a",
                linkSelector = "h2.entry-title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
                nextPageSelector = "a.next.page-numbers",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "category",
                endpoint = "/category/{genre}/page/{page}/",
                selector = "article",
                nameSelector = "h2.entry-title a",
                linkSelector = "h2.entry-title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
                nextPageSelector = "a.next.page-numbers",
                type = SourceFactory.Type.Filter
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = "div.post-thumbnail img",
            coverAtt = "data-src",
            descriptionSelector = "div.entry-content p",
            authorBookSelector = "span.author a",
            categorySelector = "span.cat-links a",
            statusSelector = "div.post-tags",
            onStatus = { status ->
                val lowerStatus = status?.lowercase() ?: ""
                when {
                    lowerStatus.contains("مكتمل") -> COMPLETED
                    lowerStatus.contains("مستمر") -> ONGOING
                    else -> ONGOING
                }
            }
        )

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            append(HttpHeaders.AcceptLanguage, "ar,en;q=0.9")
            append(HttpHeaders.AcceptEncoding, "gzip, deflate, br")
            block()
        }
    }

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.entry-content h3, div.entry-content h4",
            nameSelector = ":self",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.entry-content p",
            onContent = { contents: List<String> ->
                contents.map { it.trim() }
                    .filter { it.isNotBlank() }
                    .filterNot { 
                        it.contains("اعلان") || 
                        it.contains("إعلان") || 
                        it.contains("ads") ||
                        it.startsWith("رواية") ||
                        it.startsWith("الفصل")
                    }
            }
        )
}
