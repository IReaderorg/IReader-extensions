package ireader.sunovels

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangaInfo.Companion.ON_HIATUS
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

/**
 * ☀️ Sunovels - Arabic Novel Source
 *
 * This is a Next.js SPA site that requires WebView-based fetching for chapters.
 * The site structure:
 * - Library: /library (with client-side pagination)
 * - Search: /search?title={query}
 * - Novel detail: /novel/{slug}
 * - Chapter: /novel/{slug}/{chapter_number}
 * - Chapters are loaded via tab navigation (client-side)
 *
 * ⚠️ IMPORTANT: The library page uses lazy-loaded images (placeholder.gif).
 * Cover images are only available on the novel detail page via preload links.
 * The app will show covers after opening novel details.
 */
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    "reverend",
    1
)
@TestFixture(
    "https://sunovels.com/novel/reverend-insanity",
    chapterUrl = "https://sunovels.com/novel/reverend-insanity/1",
    expectedAuthor = "",
    expectedTitle = "القس المجنون",
)
@TestExpectations()
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
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // ═══════════════════════════════════════════════════════════════
    // 📚 EXPLORE FETCHERS
    // Site uses client-side rendering with lazy-loaded images.
    // Server-side HTML has placeholder.gif, JavaScript loads real images.
    // Cover images will only be available after opening novel details.
    // ═══════════════════════════════════════════════════════════════
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/library",
                selector = "article ul li",
                nameSelector = "h4",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                maxPage = 1,  // Client-side pagination
                onCover = { cover, _ ->
                    // Filter out placeholder images
                    if (cover.contains("placeholder")) "" else cover
                }
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?title={query}",
                selector = "article ul li",
                nameSelector = "h4",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search,
                onCover = { cover, _ ->
                    // Filter out placeholder images
                    if (cover.contains("placeholder")) "" else cover
                }
            )
        )

    // ═══════════════════════════════════════════════════════════════
    // 📖 DETAIL FETCHER
    // Novel detail page structure:
    // - Title: article h3 (Arabic title)
    // - Cover: Extracted from <link rel="preload" as="image" href="/uploads/...">
    // - Description: article > div > div p (first paragraph in info tab)
    // - Categories: article ul li a
    // - Status: article strong (مستمر/مكتمل)
    // ═══════════════════════════════════════════════════════════════
    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = "article h3",
            coverSelector = "link[rel='preload'][as='image'][href*='/uploads/']",
            coverAtt = "href",
            descriptionSelector = "article > div > div p",
            authorBookSelector = ".author",
            categorySelector = "article ul li a[href*='category']",
            statusSelector = "article strong",
            addBaseurlToCoverLink = true,
            onStatus = { status ->
                val lower = status.lowercase()
                when {
                    lower.contains("مستمر") || lower.contains("ongoing") -> ONGOING
                    lower.contains("متوقف") || lower.contains("hiatus") -> ON_HIATUS
                    lower.contains("مكتمل") || lower.contains("completed") -> COMPLETED
                    else -> ONGOING
                }
            }
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CHAPTER FETCHER
    // Chapters are loaded via JavaScript tabs, so WebView is required
    // Chapter list structure: ul li with links like /novel/{slug}/{number}
    // ═══════════════════════════════════════════════════════════════
    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = "ul li a[href*='/novel/']",
            nameSelector = "strong",
            linkSelector = "",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = false  // Chapters are already in order
        )

    /**
     * Custom chapter list parsing since chapters are loaded via JavaScript
     * Users need to use WebView (Command.Chapter.Fetch) to get chapters
     */
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        // Check for WebView HTML first (required for this site)
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup())
        }

        // Try to fetch chapters directly (may not work due to JS rendering)
        val chaptersUrl = "${manga.key}?activeTab=chapters"
        return try {
            val document = client.get(requestBuilder(chaptersUrl)).asJsoup()
            parseChaptersFromHtml(document)
        } catch (e: Exception) {
            // Return empty list - user needs to use WebView
            emptyList()
        }
    }

    private fun parseChaptersFromHtml(document: Document): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()

        // Select chapter links - they follow pattern /novel/{slug}/{number}
        val chapterLinks = document.select("a[href*='/novel/'][href~=/\\d+$]")

        for (element in chapterLinks) {
            val href = element.attr("href")
            // Skip non-chapter links
            if (!href.matches(Regex(".*/novel/[^/]+/\\d+.*"))) continue

            val name = element.select("strong").text().ifBlank {
                element.text().trim()
            }

            if (name.isNotBlank()) {
                val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                chapters.add(
                    ChapterInfo(
                        name = name,
                        key = fullUrl
                    )
                )
            }
        }

        return chapters.distinctBy { it.key }
    }

    // ═══════════════════════════════════════════════════════════════
    // 📄 CONTENT FETCHER
    // Chapter content is in paragraph elements in the main content div
    // Structure: main > div with multiple p elements
    // ═══════════════════════════════════════════════════════════════
    override val contentFetcher: Content
        get() = Content(
            pageTitleSelector = "banner h2",
            pageContentSelector = ".chapter-content p:not(.d-none)",
            onContent = { contents ->
                contents
                    .filter { it.isNotBlank() }
                    .filter { !it.contains("Tahtoh", ignoreCase = true) }  // Remove translator watermark
                    .map { it.trim() }
            }
        )

    /**
     * Custom content parsing for better extraction
     */
    override fun pageContentParse(document: Document): List<Page> {
        val content = mutableListOf<String>()

        // Get chapter title from banner h2
        val title = document.select("banner h2, header h2").firstOrNull()?.text()?.trim()
        if (!title.isNullOrBlank()) {
            content.add(title)
        }

        // The main content is in a div with multiple p elements
        // Try multiple selectors to find the content
        val paragraphs = document.select("main > div > p")
            .ifEmpty { document.select("body > div > div > p") }
            .ifEmpty { document.select("div > p") }
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .filter { !it.contains("Tahtoh", ignoreCase = true) }  // Remove watermark
            .filter { !it.startsWith("©") }  // Remove copyright
            .filter { it.length > 10 }  // Filter out very short strings

        content.addAll(paragraphs)

        // If still no content, try getting all text from main
        if (content.isEmpty()) {
            val mainText = document.select("main").text()
            if (mainText.isNotBlank() && mainText.length > 50) {
                content.add(mainText)
            }
        }

        return content.toPage()
    }

    // ═══════════════════════════════════════════════════════════════
    // 🌐 CUSTOM HEADERS
    // ═══════════════════════════════════════════════════════════════
    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            append(HttpHeaders.AcceptLanguage, "ar,en;q=0.9")
            block()
        }
    }
}
