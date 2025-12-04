package ireader.sunovels

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangaInfo.Companion.ON_HIATUS
import tachiyomix.annotations.*
import java.util.Locale

/**
 * ☀️ Sunovels - Arabic Novel Source
 *
 * Simplified using KSP annotations for declarative configuration.
 * Custom logic (status parsing, content cleaning) uses lambdas in fetchers.
 */
@AutoSourceId(seed = "Sunovels")
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular", "New"])
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
@ExploreFetcher(name = "Latest", endpoint = "/series/?page={page}&order=latest", selector = "div.series-item, div.manga-item", nameSelector = "a.title, h3 a", linkSelector = "a", coverSelector = "img.cover, img.thumbnail")
@ExploreFetcher(name = "Popular", endpoint = "/series/?page={page}&order=popular", selector = "div.series-item, div.manga-item", nameSelector = "a.title, h3 a", linkSelector = "a", coverSelector = "img.cover, img.thumbnail")
@ExploreFetcher(name = "Search", endpoint = "/search/?q={query}&page={page}", selector = "div.series-item, div.manga-item", nameSelector = "a.title, h3 a", linkSelector = "a", coverSelector = "img.cover, img.thumbnail", isSearch = true)
@DetailSelectors(title = "h1.title, .series-title", cover = "img.cover, .series-cover img", author = ".author a, span.author", description = "div.description, .synopsis p", genres = ".genres a, .tags a", status = ".status span")
@ChapterSelectors(list = "ul.chapters li, .chapter-list li", name = "a.chapter-title, a", link = "a", reversed = true)
@ContentSelectors(content = "div.content p, div.reader p, article p", title = ".chapter-title, h2")
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
    // 🔍 USE GENERATED HELPERS (from KSP)
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters() = SunovelsGenerated.getFilters()
    override fun getCommands() = SunovelsGenerated.getCommands()
    override val exploreFetchers get() = SunovelsGenerated.exploreFetchers

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
            onStatus = { status ->
                val lower = status.lowercase(Locale.ROOT)
                when {
                    lower.contains("ongoing") || lower.contains("مستمرة") -> ONGOING
                    lower.contains("hiatus") || lower.contains("متوقفة") -> ON_HIATUS
                    lower.contains("completed") || lower.contains("مكتملة") -> COMPLETED
                    else -> ONGOING
                }
            }
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CHAPTER FETCHER (from generated)
    // ═══════════════════════════════════════════════════════════════
    override val chapterFetcher get() = SunovelsGenerated.chapterFetcher

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
