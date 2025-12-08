package ireader.sunovels

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangaInfo.Companion.ON_HIATUS
import tachiyomix.annotations.Extension

/**
 * ☀️ Sunovels - Arabic Novel Source
 */
@Extension
abstract class Sunovels(deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://sunovels.com"
    override val id: Long get() = 42
    override val name: String get() = "Sunovels"

    // ═══════════════════════════════════════════════════════════════
    // 🔍 FILTERS & COMMANDS
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular", "New")),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // ═══════════════════════════════════════════════════════════════
    // 📚 EXPLORE FETCHERS
    // ═══════════════════════════════════════════════════════════════
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/series/?page={page}&order=latest",
                selector = "div.series-item, div.manga-item",
                nameSelector = "a.title, h3 a",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Popular",
                endpoint = "/series/?page={page}&order=popular",
                selector = "div.series-item, div.manga-item",
                nameSelector = "a.title, h3 a",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search/?q={query}&page={page}",
                selector = "div.series-item, div.manga-item",
                nameSelector = "a.title, h3 a",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            )
        )

    // ═══════════════════════════════════════════════════════════════
    // 📖 DETAIL FETCHER (with custom Arabic status parsing)
    // ═══════════════════════════════════════════════════════════════
    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = "h1.title, .series-title",
            coverSelector = "img.cover, .series-cover img",
            coverAtt = "data-src",
            descriptionSelector = "div.description, .synopsis p",
            authorBookSelector = ".author a, span.author",
            categorySelector = ".genres a, .tags a",
            statusSelector = ".status span",
            addBaseurlToCoverLink = true,
            onStatus = { status ->
                val lower = status.lowercase()
                when {
                    lower.contains("ongoing") || lower.contains("مستمرة") -> ONGOING
                    lower.contains("hiatus") || lower.contains("متوقفة") -> ON_HIATUS
                    lower.contains("completed") || lower.contains("مكتملة") -> COMPLETED
                    else -> ONGOING
                }
            }
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CHAPTER FETCHER
    // ═══════════════════════════════════════════════════════════════
    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = "ul.chapters li, .chapter-list li",
            nameSelector = "a.chapter-title, a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = true
        )

    // ═══════════════════════════════════════════════════════════════
    // 📄 CONTENT FETCHER (with watermark removal)
    // ═══════════════════════════════════════════════════════════════
    override val contentFetcher: Content
        get() = Content(
            pageTitleSelector = ".chapter-title, h2",
            pageContentSelector = "div.content p, div.reader p, article p",
            onContent = { contents ->
                contents.map { text ->
                    text.replace(Regex("(?i)(?:إقرأ|اقرأ)\\s*رواياتنا\\s*فقط\\s*على\\s*موقع.*"), "").trim()
                }.filter { it.isNotBlank() }
            }
        )

    // ═══════════════════════════════════════════════════════════════
    // 🌐 CUSTOM HEADERS
    // ═══════════════════════════════════════════════════════════════
    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            block()
        }
    }
}
