package ireader.novelparadise

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.findInstance
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import tachiyomix.annotations.Extension


@Extension
abstract class NovelParadise(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://www.novelsparadise.site"
    override val id: Long
        get() = 50
    override val name: String
        get() = "NovelParadise"

    // The site (جنة الروايات) runs the `lightnovel` WordPress theme behind a Cloudflare
    // managed challenge. Everything (listings, chapters, content) is fetched through the
    // CF-aware channel: browser.fetch (WebView on device, solves the challenge) first,
    // then cloudflareClient (OkHttp that reuses the cf_clearance the WebView obtained).
    // This mirrors GalaxyNovels and is the only way the protected pages render on-device.
    override val client: HttpClient
        get() = deps.httpClients.cloudflareClient

    // Real root of the theme: /np-light/ (not /series/).
    val root: String get() = "$baseUrl/np-light"

    // ── listings ────────────────────────────────────────────────────────────────
    // Home at /np-light/ renders cards as .listupd .lexa (cover .thumb img, title
    // .dtl h2 a). Some pages still use the classic .maindet .inmain .mdinfo layout;
    // we accept both.
    override fun getFilters(): FilterList = listOf(
        Filter.Title()
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    class TopNovelsListing : Listing("الرئيسية")
    class SeriesListListing : Listing("قائمة السلاسل")
    class SearchListing : Listing("بحث")

    override fun getListings(): List<Listing> = listOf(
        TopNovelsListing(),
        SeriesListListing(),
        SearchListing(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return when (sort) {
            is TopNovelsListing -> fetchGrid("$root/", page, home = true)
            is SeriesListListing -> fetchGrid("$root/series-list/", page)
            else -> fetchGrid("$root/", page, home = true)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return search(query, page)
        }
        return super.getMangaList(filters, page)
    }

    // ── grid / search ──────────────────────────────────────────────────────────
    // Fetch the rendered page through the browser (solves CF), parse the cards.
    private suspend fun fetchGrid(url: String, page: Int, home: Boolean = false): MangasPageInfo {
        // lightnovel uses /np-light/page/{n}/ for pagination except the first one.
        val target = when {
            !home && page <= 1 -> url
            home && page <= 1 -> url
            else -> "$url${if (url.endsWith("/")) "" else "/"}page/$page/"
        }
        return try {
            val html = fetchPage(target, GRID_SELECTOR)
            val doc = Ksoup.parse(html)
            val novels = doc.select(".listupd .lexa, .maindet").mapNotNull { card ->
                parseCard(card)
            }.distinctBy { it.key }
            // lightnovel shows next-page arrows (.hpage a.next / .page-numbers.next)
            val hasNext = doc.selectFirst("a.next, a.next.page-numbers, a.nextpostslink") != null
            MangasPageInfo(novels, hasNext)
        } catch (e: Exception) {
            Log.error { "NovelParadise grid error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private fun parseCard(card: Element): MangaInfo? {
        // lexa card: <div class="lexa"><a class="thumb"><img src=..></a><div class="dtl"><h2><a href=..>title</a></h2></div></div>
        val titleEl = card.selectFirst(".dtl h2 a, .mdinfo h2 a, h3 a") ?: return null
        val title = titleEl.text().trim()
        val href = titleEl.attr("href").ifBlank { titleEl.absUrl("href") }
        if (title.isBlank() || href.isBlank()) return null

        val cover = card.selectFirst("a.thumb img, .mdthumb img, a.wor-novel-card__cover img")
            ?.attr("src")?.ifBlank { card.selectFirst("a.thumb img, .mdthumb img")?.attr("data-src") }
            ?: ""
        return MangaInfo(key = href, title = title, cover = cover)
    }

    private suspend fun search(query: String, page: Int): MangasPageInfo {
        val target = buildString {
            append(root)
            append("/?s=")
            append(query.replace(" ", "+"))
        }
        return try {
            val html = fetchPage(target, GRID_SELECTOR)
            val doc = Ksoup.parse(html)
            val novels = doc.select(".listupd .lexa, .maindet").mapNotNull { parseCard(it) }
                .distinctBy { it.key }
            MangasPageInfo(novels, false)
        } catch (e: Exception) {
            Log.error { "NovelParadise search error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    // ── details / chapters / content ────────────────────────────────────────────
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title, h1",
            coverSelector = ".sertothumb img, .infseries .sertothumb img, .single-thumb img",
            coverAtt = "src",
            authorBookSelector = ".serl .serval, .infseries .serl .serval, [class*=author] a",
            categorySelector = ".sertogenre a, .infseries .sertogenre a, a[rel=category]",
            descriptionSelector = ".sersys, .sersysfull, .entry-content, .dddes",
            onDescription = { list -> list.flatMap { it.split("\n") }.map { it.trim() }.filter { it.isNotBlank() } },
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".eplisterfull ul li, .eplister ul li, ul.list-chapters li",
            nameSelector = ".epl-num, a.chapternum, a span",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1.entry-title, h1.chapter-heading, h1",
            pageContentSelector = ".entry-content, .reading-content, .chapter-content, #chapter-content, .text-left",
        )

    // ── content via browser (CF) ───────────────────────────────────────────────
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.filterIsInstance<Command.Content.Fetch>().firstOrNull()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                return pageContentParse(Ksoup.parse(cmd.html))
            }
        }

        return try {
            val html = fetchPage(chapter.key, CONTENT_SELECTOR)
            pageContentParse(Ksoup.parse(html))
        } catch (e: Exception) {
            Log.error { "NovelParadise: Error fetching content: ${e.message}" }
            listOf(Text("المحتوى غير متوفر. جرب فتح الفصل مرة أخرى."))
        }
    }

    override fun pageContentParse(document: Document): List<Page> {
        document.select("script, style, noscript, iframe, nav, footer, header, .sidebar, .comments, .ads, [class*=ad-], .announ").remove()

        val content = mutableListOf<String>()

        val title = document.selectFirst("h1.entry-title, h1.chapter-heading")?.text()?.trim()
        if (!title.isNullOrBlank()) content.add(title)

        val selectors = listOf(
            ".entry-content p, .chapter-content p, .reading-content p, #chapter-content p, .text-left p",
            ".entry-content div, .chapter-content div, .reading-content div",
        )
        for (selector in selectors) {
            val paragraphs = document.select(selector)
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 3 }
                .distinct()
            if (paragraphs.size >= 2) {
                content.addAll(paragraphs)
                return content.map { Text(it) }
            }
        }

        val containers = listOf(".entry-content", ".chapter-content", ".reading-content", ".text-left")
        for (selector in containers) {
            val container = document.selectFirst(selector) ?: continue
            container.select("script, style, noscript, .ads").remove()
            val text = container.text().trim()
            if (text.isNotBlank() && text.length > 50) {
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.length > 3 }.distinct()
                if (lines.isNotEmpty()) {
                    content.addAll(lines)
                    return content.map { Text(it) }
                }
            }
        }

        if (content.isNotEmpty()) return content.map { Text(it) }
        return listOf(Text("جاري تحميل المحتوى... حاول مرة أخرى"))
    }

    // ── CF-aware page fetch ────────────────────────────────────────────────────
    // browser.fetch renders the page in a WebView which solves the Cloudflare
    // challenge, then returns the fully-rendered HTML. If the browser isn't available
    // (desktop test harness) we fall back to cloudflareClient.
    private suspend fun fetchPage(url: String, selector: String): String {
        try {
            val browserResult = deps.httpClients.browser.fetch(
                url = url,
                selector = selector,
                timeout = 45000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                return browserResult.responseBody
            }
            Log.error { "NovelParadise: browser fetch not success: ${browserResult.error}" }
        } catch (e: Exception) {
            Log.error { "NovelParadise: browser fetch failed: ${e.message}" }
        }
        val response = client.get(requestBuilder(url))
        return response.bodyAsText()
    }

    companion object {
        const val GRID_SELECTOR = ".listupd .lexa, .maindet"
        const val CONTENT_SELECTOR = ".entry-content, .chapter-content, .reading-content, #chapter-content, .text-left"
    }
}