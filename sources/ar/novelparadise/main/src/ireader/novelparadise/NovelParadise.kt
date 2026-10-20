package ireader.novelparadise

import android.util.Log
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.findInstance
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


/**
 * NovelParadise (جنة الروايات) — WordPress `lightnovel` theme behind Cloudflare.
 *
 * The whole site answers HTTP 403 + `Server: cloudflare` to OkHttp without a cf_clearance
 * cookie, so the app's [CloudflareInterceptor] is the real fix-path: on Android it spins a
 * WebView to run the Turnstile challenge, captures a fresh cf_clearance, retries, and caches
 * it in the shared jar. We therefore make `cloudflareClient` (the interceptor-wrapped OkHttp)
 * our ONLY primary channel. `deps.httpClients.browser.fetch` is a secondary rescue when the
 * interceptor can't trigger (e.g. desktop where there's no WebView) — and we always pass it a
 * non-null *content* selector so contentReady() waits for the real page instead of returning
 * the challenge stub.
 *
 * All diagnostics here go through [diag] which logs at `android.util.Log` (vis. logcat with
 * `adb logcat -s NovelParadise:*`) — the Kermit-backed ireader Log drops Info/Debug by default,
 * so those levels never show up on the phone.
 */
@Extension
abstract class NovelParadise(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://www.novelsparadise.site"
    override val id: Long get() = 7_351_326_936_191_662_005L
    override val name: String get() = "NovelParadise"

    // The interceptor-wrapped OkHttp client (solves CF via WebView on device).
    override val client: HttpClient get() = deps.httpClients.cloudflareClient

    // Real site root of the lightnovel theme:
    val root: String get() = "$baseUrl/np-light"

    // Last successfully fetched series-detail body, keyed by series URL. Both the details
    // screen and the chapter list hit the same page; whichever coroutine survives the app's
    // navigation teardown fills this, so the other one still has a real document to parse.
    private val lastDetailHtml = HashMap<String, String>(4)

    // ── diagnostics ─────────────────────────────────────────────────────────────
    private fun diag(msg: String) = Log.w(TAG, msg)

    /**
     * Resolve any relative key/href to an absolute URL. The app hands us the key as
     * stored in the list, which can be a bare path ("/np-light/series/slug/…") when the
     * theme emits relative hrefs; using that directly makes OkHttp hit localhost:80 and
     * the WebView hang forever — the #1 cause of "chapters never load".
     */
    private fun absolute(url: String): String {
        val u = url.trim()
        if (u.isEmpty() || u.startsWith("http", ignoreCase = true)) return u
        return if (u.startsWith("/")) baseUrl + u else "$baseUrl/$u"
    }

    // ── listings ────────────────────────────────────────────────────────────────
    override fun getFilters(): FilterList = listOf(Filter.Title())
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // Real home sections (verified on-device, v2.18): the lightnovel home page has 4 content
    // sections — "اخر التحديثات" (latest updated, 54 series), "اجدد الروايات" (newly added,
    // 12), "الرائجة اليوم" (trending today, 12) and "الروايات المكتملة" (completed, 12) — each
    // a <section> whose heading (h2) names it. We expose exactly those + the full archive
    // (قائمة السلاسل) and بحث.
    class LatestUpdatesListing : Listing("أحدث الروايات")
    class NewSeriesListing : Listing("اجدد الروايات")
    class TrendingListing : Listing("الرائجة اليوم")
    class CompletedListing : Listing("الروايات المكتملة")
    class SeriesListListing : Listing("كل الروايات")

    override fun getListings(): List<Listing> = listOf(
        LatestUpdatesListing(),
        NewSeriesListing(),
        TrendingListing(),
        CompletedListing(),
        SeriesListListing(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return when (sort) {
            // Each listing reads its OWN full, paginated archive page — NOT the limited home
            // carousel — so "عرض المزيد" keeps loading real content.
            is LatestUpdatesListing -> fetchGrid("$root/series-list/", page, filter = null)
            is NewSeriesListing -> fetchGrid("$root/series-list/?orderby=date", page, filter = null)
            is TrendingListing -> fetchGrid("$root/trending/", page, filter = null)
            is CompletedListing -> fetchGrid("$root/series-list/", page, filter = { it.isCompleted() })
            is SeriesListListing -> fetchGrid("$root/series-list/", page, filter = null)
            else -> fetchGrid("$root/series-list/", page, filter = null)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) return search(query, page)
        return MANGAS_EMPTY
    }

    private suspend fun fetchGrid(
        url: String,
        page: Int,
        home: Boolean = false,
        filter: ((MangaInfo) -> Boolean)? = null,
    ): MangasPageInfo {
        // Keep any query string (?orderby=…) when building the paged URL.
        val base = url.substringBefore("?")
        val query = url.substringAfter("?", missingDelimiterValue = "")
        val target = when {
            page <= 1 -> url
            query.isNotEmpty() -> "$base?${query}&page=$page"
            else -> "$base/page/$page/"
        }
        return try {
            val html = fetchPage(target, SERIES_ANCHOR_SELECTOR)
            val doc = Ksoup.parse(html)

            // On-device STRUCTURE dump (v2.9) proved the theme does NOT use the classic
            // .listupd/.lexa/.maindet grids — it uses <article>-based cards. So we extract
            // series links directly, dedupe by detail URL, and pull cover + title from the
            // card region.
            val all = doc.select(SERIES_ANCHOR_SELECTOR).mapNotNull { parseSeriesAnchor(it) }
                .distinctBy { it.key }
            // Secondary dedup by title: the same novel can appear with Arabic & English slugs
            // giving different keys — dedup by normalised title as a safety net.
            val byTitle = linkedMapOf<String, MangaInfo>()
            all.forEach { m -> byTitle.putIfAbsent(normalizedTitle(m.title), m) }
            val novels = if (filter == null) byTitle.values.toList() else byTitle.values.filter(filter)

            if (page <= 1) {
                diagPagination(doc, target)
                diagSortControls(doc, target)
                val preview = byTitle.values.take(6).map { it.title.take(28) }.joinToString(" | ")
                diag("PG1 $target titles: $preview")
            }
            if (novels.isEmpty() && html.length > 1_000) {
                diagnoseHtml(html)
            }
            if (home) diagHome(html)

            val hasNext = hasNextPage(doc)

            diag("grid ${if (home) "home" else url}: ${all.size} raw → ${novels.size} novels, hasNext=$hasNext (html=${html.length}B)")
            MangasPageInfo(novels, hasNext)
        } catch (e: Exception) {
            diag("grid error on $target: ${e.message}")
            MANGAS_EMPTY
        }
    }

    /**
     * Scrape one series from the theme's series card.
     *
     * The captured grid markup is one `<article class="series-card">` per novel:
     *
     *     <article class="series-card">
     *       <a class="series-card-main" href="/np-light/series/…/">
     *         <img src="/np-light/public/uploads/series/….jpg" alt="الرواية" loading="lazy">
     *         <span class="series-status-badge is-ongoing">مستمرة</span>
     *         <h2>الرواية</h2>
     *         <span class="card-rating" …>0.0<i>★★★★★</i></span>
     *       </a>
     *       <span class="card-meta"> …genres…, <a class="card-meta-chapter"
     *            href="/np-light/series/…/chapter-403/">403 فصل</a></span>
     *     </article>
     *
     * The `card-meta-chapter` anchor is a SECOND series link per card, so only the
     * `series-card-main` one is a listing entry. Anchors that live outside a card are page
     * furniture (nav, related links, sidebar) and are dropped too — accepting them is what
     * mixed junk into the results on pages that use a different template.
     */
    private fun parseSeriesAnchor(a: Element): MangaInfo? {
        if (!a.hasClass("series-card-main")) return null
        val href = a.absUrl("href").ifBlank { a.attr("href") }
        if (href.isBlank() || !href.contains(SERIES_KEY)) return null
        if (href.contains(CHAPTER_KEY) || href.endsWith("series-list/")) return null

        val container = a.closest("article") ?: return null
        // The card's title lives in its own <h2> — cleaner than scraping anchor text,
        // which also carries the status badge and the star rating.
        val title = cleanTitle(
            container.selectFirst("h2, h3")?.text()?.trim().orEmpty().ifBlank {
                bestTitleForHref(container, href)
            }
        )
        if (title.isBlank()) {
            diag("parseSeriesAnchor REJECT href=$href anchorText='${a.text().trim().take(24)}'")
            return null
        }

        // Normalize key: resolve to an absolute URL (relative hrefs are Common in the
        // theme's HTML), drop trailing slash and decode %XX so raw-Arabic and
        // percent-encoded variants of the same URL dedupe to one novel.
        val key = absolute(href).trimEnd('/')
            .let { try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } }

        // Covers are ROOT-RELATIVE ("/np-light/public/uploads/series/x.jpg") — logcat v2.25
        // proved every one of them is, with no protocol and no host. Passing that straight to
        // MangaInfo.cover makes the loader fail (it has nothing to resolve against), so no
        // cover ever renders. Resolve to an absolute URL, and skip lazy-loader placeholders.
        val imgEl = container.selectFirst("img")
        val cover = imgEl?.let { img ->
            IMG_ATTRS
                .asSequence()
                .map { img.attr(it) }
                .map { firstSrcsetUrl(it) }   // "a.jpg 1x, b.jpg 2x" -> "b.jpg"
                .firstOrNull { it.isNotBlank() && !isPlaceholder(it) }
                ?.let { absolute(it.trim()) }
                ?.takeIf { it.isNotBlank() }
        } ?: ""

        val status = readStatus(container)
        return MangaInfo(key = key, title = title, cover = cover, status = status)
    }

    /**
     * Read the series status from the card's `.series-status-badge` — either from its
     * `is-{class}` (e.g. `is-completed`) or its Arabic label ("مكتملة" / "مستمرة").
     * Returns [MangaInfo.UNKNOWN] when the card carries no badge.
     */
    private fun readStatus(container: Element): Long {
        val badge = container.selectFirst(".series-status-badge") ?: return MangaInfo.UNKNOWN
        val cls = badge.classNames().firstOrNull { it.startsWith("is-") }?.removePrefix("is-")
        val text = badge.text().trim().lowercase()
        return when {
            cls?.contains("complet", ignoreCase = true) == true || text.contains("مكتمل") ||
                text == "completed" || cls == "finish" -> MangaInfo.COMPLETED
            cls?.contains("ongoing", ignoreCase = true) == true || text.contains("مستمر") ||
                text == "ongoing" -> MangaInfo.ONGOING
            else -> MangaInfo.UNKNOWN
        }
    }

    /** Longest "real name" among anchors pointing at [href] within [container]. */
    private fun bestTitleForHref(container: Element, href: String): String {
        val candidates = mutableListOf<Pair<String, Int>>() // text, score
        for (el in container.select("a[href]")) {
            val elHref = el.absUrl("href").ifBlank { el.attr("href") }
            if (elHref != href) continue
            val text = el.text().trim()
            if (text.isBlank()) continue
            // Prefer heading anchors / serie-s, and long descriptive text.
            val inHeading = el.closest("h1, h2, h3") != null || el.hasClass("serie-s")
            val score = if (inHeading) 100 else 0
            candidates.add(text to score)
        }
        return candidates
            .filter { (text, _) -> text.length >= 4 && !isStatusWord(text) }
            .maxByOrNull { (text, score) -> score * 1000 + text.length }
            ?.first
            ?: ""
    }

    private fun isStatusWord(text: String): Boolean {
        val t = text.trim().lowercase()
        return t == "مستمرة" || t == "متابعة" || t == "مكتملة" ||
            t.contains("فصل") || t.startsWith("0 ") ||
            t.matches(Regex("^[0-9]+\\s*فصل$"))
    }

    private suspend fun search(query: String, page: Int): MangasPageInfo {
        val q = query.trim()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        // The THEME's own search box posts to /np-light/search/?q= (confirmed in the captured
        // markup: <form class="search-form" action="/np-light/search/" method="get"> with
        // <input type="search" name="q">). We used to use WordPress core's root /?s= instead:
        // that is a whole-site post/page search whose *header* is a different template, so the
        // grid parser latched onto that header's navigation cards — which is where the garbled
        // "فتح رواية …" titles and the repeated placeholder cover came from. The theme endpoint
        // returns the SAME <article class="series-card"> grid as the listings, so titles, covers
        // and counts all come out clean.
        val target = "$root/search/?q=$encoded&page=$page"
        return try {
            val html = fetchPage(target, SERIES_ANCHOR_SELECTOR)
            val doc = Ksoup.parse(html)
            val all = doc.select(SERIES_ANCHOR_SELECTOR).mapNotNull { parseSeriesAnchor(it) }
            val byTitle = linkedMapOf<String, MangaInfo>()
            all.forEach { m -> byTitle.putIfAbsent(normalizedTitle(m.title), m) }
            val novels = byTitle.values.toList()
            val hasNext = hasNextPage(doc)
            diag("search '$q' p$page: ${all.size} raw -> ${novels.size} results, hasNext=$hasNext (html=${html.length}B)")
            if (novels.isEmpty() && html.length > 1_000) diagnoseHtml(html)
            MangasPageInfo(novels, hasNext)
        } catch (e: Exception) {
            diag("search error: ${e.message}")
            MANGAS_EMPTY
        }
    }

    // ── details / chapters / content (theme selectors) ──────────────────────────
    override val detailFetcher: Detail = SourceFactory.Detail(
        // The detail page carries a breadcrumb heading as well as the title heading, and
        // `select("h1, h1.entry-title").text()` CONCATENATES every match — that is where the
        // title "الرئيسية / كل الروايات / ديون لا نهاية لها" came from. detailParse() below
        // takes the LAST h1, which is the real novel title; keep the selector as a fallback.
        nameSelector = "h1.entry-title, h1",
        coverSelector = ".sertothumb img, .infseries .sertothumb img, .seriesthumb img",
        coverAtt = "src",
        // The theme emits ROOT-RELATIVE covers; without this the detail page's image is a
        // bare path with no host and never loads (same root cause as the grid covers).
        addBaseurlToCoverLink = true,
        authorBookSelector = ".serl .serval, .infseries .serl .serval, [class*=author] a",
        categorySelector = ".sertogenre a, .infseries .sertogenre a, a[rel=category]",
        // The detail page carries the same badge as the grid card. Without this the status
        // column always read "unknown" even though the grid already knew it.
        statusSelector = ".series-status-badge, .seriestatus .is-ongoing, .seriestatus .is-completed",
        statusAtt = "class",
        onStatus = { text ->
            when {
                text.contains("complet", ignoreCase = true) || text.contains("مكتمل") -> MangaInfo.COMPLETED
                text.contains("ongoing", ignoreCase = true) || text.contains("مستمر") -> MangaInfo.ONGOING
                else -> MangaInfo.UNKNOWN
            }
        },
        descriptionSelector = ".sersys, .sersysfull, .entry-content",
        onDescription = { list -> list.flatMap { it.split("\n") }.map { it.trim() }.filter { it.isNotBlank() } },
    )

    /**
     * The detail page renders the breadcrumb AND the title as sibling `<h1>`s, so the stock
     * [detailParse] would glue them into one string. Everything else it does is right, so
     * take its result and only replace the title with the LAST h1 (the novel's own heading).
     */
    override fun detailParse(document: Document): MangaInfo {
        val parsed = super.detailParse(document)
        val headings = document.select("h1")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.contains("/") }
        val title = headings.lastOrNull()?.let { cleanTitle(it) }?.takeIf { it.isNotBlank() }
        return if (title == null) parsed else parsed.copy(title = title)
    }

    override suspend fun getMangaDetailsRequest(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): Document {
        // The CF-warmed fetch of this very page often succeeds a few ms before the coroutine
        // backing getMangaDetails is cancelled (the app tears it down on navigation). Keeping
        // the last good body per key lets the details parse from a warm page instead of
        // rendering an empty screen after the cancel.
        lastDetailHtml[manga.key]?.let { cached ->
            diag("getMangaDetailsRequest: using cached body for ${manga.key} (${cached.length}B)")
            return Ksoup.parse(cached)
        }
        return try {
            // NonCancellable: the app tears this coroutine down on navigation, but the fetch is
            // already in flight and completes fine — cancelling it wasted a warm CF session and
            // left the details screen blank (logcat: "StandaloneCoroutine was cancelled").
            val html = withContext(NonCancellable) {
                fetchPage(manga.key, CONTENT_MARKER_SELECTOR)
            }
            if (html.isBlank()) {
                diag("getMangaDetailsRequest: blank for ${manga.key}")
                Ksoup.parse("")
            } else {
                if (html.length > 5_000) lastDetailHtml[manga.key] = html
                Ksoup.parse(html)
            }
        } catch (e: Exception) {
            diag("getMangaDetailsRequest error: ${e.message}")
            Ksoup.parse("")
        }
    }

    override val chapterFetcher: Chapters = SourceFactory.Chapters(
        selector = ".eplisterfull ul li, .eplister ul li, ul.list-chapters li",
        nameSelector = ".epl-num, a.chapternum, a span",
        linkSelector = "a",
        linkAtt = "href",
    )

    /**
     * Fetch the chapter list from the detail page. The lightnovel theme puts chapters in an
     * `.eplister` list, but like the grid, the live DOM may differ — so if the named list
     * yields nothing we extract chapter links directly, then diagnose the structure on-device.
     */
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): List<ChapterInfo> {
        diag("CHL START key=${manga.key}")

        // 1) Pre-fetched HTML (WebView) command, if the app actually hands one over.
        //    Log EVERYTHING so we can see what the WebView saw, even on the empty path.
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            diag("  CHL cmd Chapter.Fetch: url=${cmd.url.take(140).ifBlank { "<none>" }} html=${cmd.html.length}B")
            if (cmd.html.isNotBlank()) {
                diagChaptersDoc("  [cmd] ", cmd.html)
                val chapters = cleanChapters(chaptersParse(Ksoup.parse(cmd.html)))
                if (chapters.isNotEmpty()) {
                    diag("  CHL returns ${chapters.size} via [cmd] fetcher")
                    return applyChapterSorting(chapters)
                }
                val fallback = chapterLinksFromDoc(Ksoup.parse(cmd.html))
                if (fallback.isNotEmpty()) {
                    diag("  CHL returns ${fallback.size} via [cmd] link-fallback")
                    return applyChapterSorting(fallback)
                }
                diagnoseHtml(cmd.html)
                diag("  CHL [cmd] html parsed 0 chapters — continuing to direct fetch")
            }
        }

        // 2) Direct fetch of the detail page (the normal path when no command is set).
        return try {
            // The CF challenge door may need a moment — retry a few times when the page comes
            // back as a bare challenge stub (no real <a> links) so we don't show 0 chapters.
            var html = ""
            for (attempt in 1..3) {
                diag("  CHL direct fetch ${manga.key} (try $attempt) …")
                html = fetchPage(manga.key, CONTENT_MARKER_SELECTOR)
                diagChaptersDoc("  [drt] ", html)
                if (html.isNotBlank() && challengeStubOnly(html)) {
                    diag("  [drt] challenge stub; waiting for CF bypass before retry…")
                    kotlinx.coroutines.delay(1_500L)
                    continue
                }
                break
            }
            // Share the warm body with getMangaDetailsRequest, which is frequently cancelled
            // a few ms later by the app's navigation teardown and would otherwise show nothing.
            if (html.length > 5_000) lastDetailHtml[manga.key] = html

            val doc = Ksoup.parse(html)

            val chapters = cleanChapters(chaptersParse(doc))
            if (chapters.isNotEmpty()) {
                diag("  CHL returns ${chapters.size} via [drt] fetcher")
                return applyChapterSorting(chapters)
            }

            // Fallback: every real chapter is an <a> whose href contains /chapter-N/.
            val fallback = chapterLinksFromDoc(doc)
            if (fallback.isEmpty()) {
                if (html.isNotBlank() && challengeStubOnly(html)) {
                    diag("  CHL: page is still a CF stub — returning empty (cached?)")
                } else {
                    diagnoseHtml(html)
                    diag("  CHL EMPTY on ${manga.key}")
                }
            } else {
                diag("  CHL returns ${fallback.size} via [drt] link-fallback")
            }
            applyChapterSorting(fallback)
        } catch (e: Exception) {
            diag("  CHL error on ${manga.key}: ${e.message}")
            emptyList()
        }
    }

    /** True when [html] is a Cloudflare/Turnstile stub rather than a real page: it has no <a> anchors. */
    private fun challengeStubOnly(html: String): Boolean {
        if (html.isBlank()) return false
        return try {
            Ksoup.parse(html).selectFirst("a[href]") == null
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Extract every real chapter link (`a[href*='/chapter-N/']`) from [doc] as [ChapterInfo].
     */
    private fun chapterLinksFromDoc(doc: Document): List<ChapterInfo> =
        doc.select("a[href*='$CHAPTER_KEY']").mapNotNull { a ->
            val raw = a.attr("href").trim().let { if (it.isBlank()) a.absUrl("href") else it }
            if (raw.isBlank() || !raw.contains(CHAPTER_KEY)) return@mapNotNull null
            val href = absolute(raw)
            val name = a.text().trim()
            val title = if (name.isBlank() || name.length < 2) {
                href.substringAfterLast("/").replace('-', ' ').trim()
            } else cleanChapterName(name)
            ChapterInfo(key = href, name = title)
        }.distinctBy { it.key }

    /** Apply [cleanChapterName] to every chapter, whichever parser produced it. */
    private fun cleanChapters(chapters: List<ChapterInfo>): List<ChapterInfo> =
        chapters.map { it.copy(name = cleanChapterName(it.name)) }

    /**
     * Strip the publish date that the theme renders inside the chapter <a> next to the title
     * ("الفصل 12  2024-05-20" / "الفصل 1 · 20 يوليو 2024"). Normalises Arabic-Indic digits
     * (٢٠٢٤-٠٥-٢٠) to ASCII first, then removes trailing (and leading) date+optional time from
     * both the title and the whole list, whichever parser produced the chapters.
     */
    private fun cleanChapterName(name: String): String {
        var t = normalizedDigits(name).trim()
        // Trailing numeric date (+ optional time). The theme GLUES the date to the title with
        // NO separator (logcat-proven: "الظهور الإلهي2025-12-12 04:29"), but it can also be
        // separated by a space / '+'; allow ZERO or more separator chars. Requiring a 4-digit
        // year keeps this safe — no real Arabic chapter title ends in YYYY-MM-DD itself.
        // Also covers "… 2024-05-20", "… 20/05/2024 14:30", "…+2026-09-14 10:50".
        t = t.replace(
            Regex("""(?:[\s +·:،,/\-]*)(?:20\d{2}[\-/.]\d{1,2}[\-/.]\d{1,2})(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?\s*$"""),
            ""
        )
        // Tidy leftovers from the removal ("الفصل 12 -", "الفصل 12 ·").
        t = t.replace(Regex("""[\s·\-:,/—]+\s*$"""), "").trim()
        // Trailing named-month date (Arabic/English): "… 20 يوليو 2024" / "… July 2, 2024"
        t = t.replace(
            Regex("""[\s +]+(?:\d{1,2}[\s .,]+\p{L}+[\s .]+\d{2,4}|\p{L}+[\s ]+\d{1,2}[\s .,]*\d{2,4})(?:\s+\d{1,2}:\d{2})?\s*$"""),
            ""
        ).trim()
        // Leading date (nicknames like "2024-05-20 · الفصل 1" / "20 يوليو 2024 · …")
        t = t.replace(
            Regex("""^(?:\d{1,2}[\-/.]\d{1,2}[\-/.]\d{2,4}|\d{2,4}[\-/.]\d{1,2}[\-/.]\d{1,2}|\d{1,2}[\s +.,]+\p{L}+[\s .]+\d{2,4}|\p{L}+[\s +]+\d{1,2}[\s +.,]*\d{2,4})(?:\s+\d{1,2}:\d{2})?[\s ·]+\s*"""),
            ""
        ).trim()
        return t.ifBlank { name }
    }

    /**
     * Strip the rating the theme renders next to a series name inside the card anchor
     * ("ملحمة النجمة 4.0★★★★★★★★★★"). The anchor text is "name + rating + a run of ★/☆
     * stars" — keep only the real name. Arabic-Indic digits are normalised first so the
     * decimal rating ("٤٫٥") is recognised too.
     */
    private fun cleanTitle(title: String): String {
        var t = normalizedDigits(title).trim()
        // 1) Solid run of star glyphs at the very end (with optional separators before it).
        t = t.replace(Regex("""[\s·:,/()\-]*\s*[★☆✦✧★☆]{1,12}\s*$"""), "").trim()
        // 2) A standalone decimal rating left over after the stars were removed ("… 4.0").
        t = t.replace(Regex("""\s+\d{1,2}[.,]\d{1,2}\s*$"""), "").trim()
        // 3) A "4.0/5" style rating.
        t = t.replace(Regex("""\s+\d{1,2}[.,]\d{1,2}\s*/\s*[0-5]\s*$"""), "").trim()
        // 4) The status badge is GLUED to the front of the name with no separator — logcat v2.24
        //    showed every title as "مستمرة الوحيد في العالم", "مكتملة …". The badge is a
        //    *separate element* in the card, so a title that merely STARTS with a status word is
        //    really "<status> <title>". Strip it, and if nothing is left keep the original so we
        //    never return an empty title.
        t = stripLeadingStatus(t)
        return t
    }

    /**
     * Remove a leading status word ("مستمرة" / "مكتملة" / "متابعة" / English equivalents) that
     * the theme renders at the head of the card title. Only strips when real text remains, so a
     * title that IS the status word is left alone.
     */
    private fun stripLeadingStatus(t: String): String {
        val lead = Regex(
            "^(?:مستمرة|مكتملة|متابعة|منتهية|جديدة|جديد|" +
                "ongoing|completed|new|updating)\\s*[·:\\-–—]?\\s+"
        )
        val stripped = t.replace(lead, "").trim()
        return stripped.ifBlank { t }
    }

    /** Normalise a title for dedup: lowercase, collapse whitespace, strip punctuation. */
    private fun normalizedTitle(title: String): String = title.lowercase().trim()
        .replace(Regex("[\\s\\p{Punct}]+"), " ")
        .trim()

    /** Map Arabic-Indic (٠-٩) and Persian (۰-۹) digits to ASCII so date regexes match. */
    private fun normalizedDigits(s: String): String = buildString(s.length) {
        s.forEach { c ->
            when (c) {
                in '٠'..'٩' -> append('0' + (c - '٠'))
                in '۰'..'۹' -> append('0' + (c - '۰'))
                else -> append(c)
            }
        }
    }

    /**
     * Fingerprint a chapter-list body so we can tell what the WebView actually delivered:
     * chapter-link count, lis, name-anchor count, first few chapter links, and whether the
     * page looks like a challenge stub rather than a real chapter list.
     */
    private fun diagChaptersDoc(label: String, html: String) {
        if (html.isBlank()) {
            diag("$label body BLANK")
            return
        }
        val doc = Ksoup.parse(html)
        val chapterLinks = doc.select("a[href*='$CHAPTER_KEY']")
        val ulLis = doc.select("ul li").size
        val eplisters = doc.select(".eplisterfull, .eplister, ul.list-chapters").size
        val heads = doc.select("h1:not(:empty)").map { it.text().trim().take(50) }
        val pageTitle = titleOf(html)
        diag("$label html=${html.length}B chapterLinks=${chapterLinks.size} ul>li=$ulLis eplisters=$eplisters " +
            "title=$pageTitle challenge=${isChallenge(pageTitle)}")
        chapterLinks.take(6).forEach { a ->
            diag("$label  ${a.text().trim().take(40)} -> ${a.absUrl("href").ifBlank { a.attr("href") }}")
        }
        if (heads.isNotEmpty()) diag("$label h1=${heads.joinToString(" | ")}")
    }

    override val contentFetcher: Content = SourceFactory.Content(
        pageTitleSelector = "h1, h1.chapter-heading",
        pageContentSelector = "$CPAGE_CLASS, .entry-content, .chapter-content",
    )

    // ── page list (content paragraphs) ──────────────────────────────────────────
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        diag("PGL START key=${chapter.key.take(120)} name=${chapter.name.take(60)}")
        commands.filterIsInstance<Command.Content.Fetch>().firstOrNull()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                diag("  PGL via Content.Fetch (WebView) html=${cmd.html.length}B")
                diagChaptersDoc("  [pg-cmd] ", cmd.html)
                return pageContentParse(Ksoup.parse(cmd.html))
            }
        }

        return try {
            val html = fetchPage(chapter.key, CONTENT_MARKER_SELECTOR)
            diagChaptersDoc("  [pg-drt] ", html)
            if (html.isBlank()) {
                diag("  PGL blank body for ${chapter.key.take(120)}")
                return listOf(Text("المحتوى غير متوفر. جرّب فتح الفصل مرة أخرى."))
            }
            val pages = pageContentParse(Ksoup.parse(html))
            if (pages.isEmpty()) {
                diag("  PGL parsed 0 pages; dumping structure for ${chapter.key.take(120)}")
                diagnoseHtml(html)
            } else {
                diag("  PGL returns ${pages.size} pages for ${chapter.key.take(120)}")
            }
            pages
        } catch (e: Exception) {
            diag("  PGL error: ${e.message}")
            listOf(Text("المحتوى غير متوفر. جرّب فتح الفصل مرة أخرى."))
        }
    }

    /**
     * Extract the chapter body from a detail/chapter document:
     * 1. Find the .epcontent / .entry-content / .chapter-content container
     * 2. Pull its <p> paragraphs as pages
     * 3. If none, use the whole container text split into lines
     */
    override fun pageContentParse(document: Document): List<Page> {
        // Remove everything that isn't content.
        document.select(
            "script, style, noscript, iframe, nav, footer, header, " +
                ".sidebar, .comments, .ads, [class*=ad-], .announ",
        ).remove()

        // Strip advert placeholders ("Advertise here" etc.) the theme injects mid-chapter.
        document.select("div[class*='ad'], div[id*='ad'], p[class*='ad']").forEach { el ->
            val txt = el.text().trim()
            if (txt.length <= 40 && AD_TEXT_MARKERS.any { it in txt.lowercase() }) el.remove()
        }

        val content = mutableListOf<String>()
        val title = document.selectFirst("h1, h1.chapter-heading")?.text()?.trim()
        if (!title.isNullOrBlank()) content.add(title)

        // Paragraph granularity, then line granularity, then a last-chance fallback.
        val paragraphSelectors = listOf(
            "$CPAGE_CLASS p, .entry-content p, .chapter-content p, .reading-content p, #chapter-content p, .text-left p",
            "$CPAGE_CLASS div, .entry-content div, .chapter-content div, .reading-content div",
        )
        for (selector in paragraphSelectors) {
            val paragraphs = document.select(selector)
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 3 }
                .distinct()
            if (paragraphs.size >= 2) {
                content.addAll(paragraphs)
                return content.map { Text(it) }
            }
        }

        for (selector in listOf(CPAGE_CLASS, ".entry-content", ".chapter-content", ".reading-content", ".text-left")) {
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

    // ── CF-aware fetch ──────────────────────────────────────────────────────────
    /**
     * Fetch [url] and require the returned HTML to contain a real *content* marker:
     * [SERIES_ANCHOR_SELECTOR] (which matches only on pages that actually have a series link,
     * never on the Cloudflare challenge stub). Returns "" if no real content.
     */
    private suspend fun fetchPage(url: String, selector: String): String {
        // Defensive: always resolve to an absolute URL — stale/cached keys from a previous
        // version may still be bare paths (a relative URL hits localhost:80 / hangs the WebView).
        val abs = absolute(url)

        // 1. Primary: cloudflareClient — the OkHttp CloudflareInterceptor solves the challenge
        //    inside a HIDDEN WebView (running the Turnstile JS, which plants cf_clearance in the
        //    shared Chrome cookie store) and then re-fetches. On a warm cookie this returns real
        //    content instantly, invisibly, with NO visible challenge. When the cookie is cold the
        //    interceptor reports "Failed to bypass" — but its hidden WebView just warmed the
        //    session, which makes the browser (step 2) pass almost immediately.
        try {
            val body = client.get(requestBuilder(abs)).bodyAsText()
            if (isRealContent(body)) {
                diag("OK client ($abs) ${body.length}B")
                return body
            }
            diag("client returned ${body.length}B non-real; falling back to browser")
        } catch (e: Exception) {
            diag("client GET failed: ${e.message}")
        }

        // 2. BrowserEngine WebView — solves the challenge visibly and, crucially, waits for the
        //    *content* selector. A non-null selector means contentReady() only fires once real
        //    chapter/series links exist, so it usually returns the true page. Turnstile can take
        //    a few seconds, so retry up to 3× with a short pause when we get a challenge stub.
        for (attempt in 1..3) {
            try {
                diag("browser.fetch($abs, selector=…, try $attempt)…")
                val result = deps.httpClients.browser.fetch(
                    url = abs,
                    selector = selector,
                    timeout = 60_000L,
                )
                val body = result.responseBody
                if (result.isSuccess && isRealContent(body)) {
                    diag("OK browser ($abs) ${body.length}B")
                    return body
                }
                diag("browser gave no content (${result.statusCode} ${result.error ?: ""})")
            } catch (e: Exception) {
                diag("browser.fetch threw: ${e.message}")
            }
            if (attempt < 3) {
                kotlinx.coroutines.delay(2_000L)
                diag("  retrying browser (Turnstile may still be solving)…")
            }
        }
        return ""
    }

    private fun isRealContent(body: String): Boolean {
        if (body.isBlank() || body.length < 4_000) return false
        // A Turnstile challenge stub ("Just a moment…") has ZERO real <a> links — but it DOES
        // echo the requested URL (including /np-light/series/…) inside its scripts, so a
        // text-contains check gets fooled into accepting the stub. Require actual <a> structure.
        try {
            val doc = Ksoup.parse(body)
            if (doc.selectFirst("a[href]") == null) return false
        } catch (e: Exception) {
            return false
        }
        if (body.contains(SERIES_KEY)) return true
        return !isChallenge(titleOf(body))
    }

    /**
     * Called when a real page yielded no novels. Prints a DOM fingerprint to logcat:
     * how many nodes match each candidate selector, plus the first series-ish <a> links
     * (href + text + surrounding tag). This reveals the actual structure the theme uses,
     * since the CF gate blocks us from viewing raw HTML from the desktop.
     */
    private fun diagnoseHtml(body: String) {
        try {
            val doc = Ksoup.parse(body)
            fun count(sel: String) = doc.select(sel).size
            diag("STRUCTURE: html=${body.length}B, links=${count("a")}, " +
                "a.serie-s=${count("a.serie-s")}, a[href*='series']=${count("a[href*='series']")}, " +
                "a[href*='/np-light/']=${count("a[href*='/np-light/']")}, " +
                ".listupd=${count(".listupd")}, .lexa=${count(".lexa")}, .maindet=${count(".maindet")}, " +
                ".utao=${count(".utao")}, .uta=${count(".uta")}, .dtl=${count(".dtl")}, " +
                ".mdinfo=${count(".mdinfo")}, .luf=${count(".luf")}, h2=${count("h2")}, h3=${count("h3")}, " +
                "article=${count("article")}, .entry-content=${count(".entry-content")}, " +
                "img=${count("img")}")

            // First real (non-menu) <a> candidates sorted by presence of a long path.
            val links = doc.select("a[href]").asSequence()
                .map { it to it.attr("href") }
                .filter { it.second.isNotBlank() && !it.second.startsWith("#") && !it.second.startsWith("javascript:") }
                .take(40)
                .toList()
            diag("STRUCTURE-LINKS: ${links.size} candidates:")
            links.forEach { (a, href) ->
                if (href.contains("series") || a.text().trim().length > 5) {
                    diag("  [$href] << ${a.text().trim().take(40)}")
                }
            }
        } catch (e: Exception) {
            diag("diagnoseHtml failed: ${e.message}")
        }
    }

    /**
     * Fingerprint the novelparadise HOME page specifically: how many top-level home sections,
     * their headings/section titles, and the structure/status badges of the rendered cards.
     * Prints to logcat so we can split the home page into the real listing categories
     * ("أحدث الروايات" / "الرائجة اليوم" / "الروايات المكتملة" ...) on-device instead of guessing.
     */
    private fun diagHome(body: String) {
        try {
            val doc = Ksoup.parse(body)
            fun count(sel: String) = doc.select(sel).size
            diag("HOME-BASE: html=${body.length}B, sections=${count("section, .home-section, .widget, .sec")}, " +
                "h1=${count("h1")}, h2=${count("h2")}, h3=${count("h3")}, " +
                "series-card=${count(".series-card")}, series-card-main=${count(".series-card-main")}, " +
                "status-badge=${count(".series-status-badge")}, new-pill=${count(".new-pill")}, " +
                "data-rating=${count("[data-series-rating]")}, trending-link=${count("a[href*='trending']")}")

            val headings = doc.select("section .section-head h1, section .section-head h2, section h1, section h2")
                .map { it.text().trim().take(50) }.filter { it.isNotBlank() }
            if (headings.isNotEmpty()) diag("HOME-HEADINGS: ${headings.distinct().joinToString(" | ")}")

            val badges = doc.select(".series-status-badge").take(8).map { it.text().trim().take(20) }
            if (badges.isNotEmpty()) diag("HOME-BADGES: ${badges.distinct().joinToString(" | ")}")

            val trending = doc.selectFirst("a[href*='trending']")?.let { it.attr("href").trim() }
            if (trending != null) diag("HOME-TRENDING: link=$trending")

            // How many real series anchors fall inside each <section>? Group by closest section h2.
            val sections = doc.select("section")
            val map = LinkedHashMap<String, Int>()
            doc.select(SERIES_ANCHOR_SELECTOR).forEach { a ->
                val sec = a.closest("section")
                val h2 = sec?.selectFirst("h2")?.text()?.trim() ?: "(outside section)"
                map[h2] = (map[h2] ?: 0) + 1
            }
            map.forEach { (title, n) -> diag("HOME-SEC: [$title] = $n series") }
        } catch (e: Exception) {
            diag("diagHome failed: ${e.message}")
        }
    }

    /** Dump real pagination links (page numbers / next) to logcat for [target]. */
    private fun diagPagination(doc: Document, target: String) {
        try {
            val raw = doc.select("a.page-numbers, .pagination a, a.next, a.prev").map { a ->
                "${a.text().trim().take(10)} => ${a.attr("href").trim().take(90)}"
            }
            if (raw.isNotEmpty()) diag("PG $target: ${raw.take(15).joinToString(" | ")}")
            else diag("PG $target: (no pagination links found)")
        } catch (e: Exception) {
            diag("PG diag failed: ${e.message}")
        }
    }

    /**
     * Does this page offer a *next* one?
     *
     * The theme's own pager is `<nav class="pager"><span>صفحة 1 من 137</span>
     * <a href="?page=2">التالي</a></nav>` — a QUERY param (`?page=2`), and the only
     * pagination element on the whole page. The classic Tachiyomi `a.next` /
     * `a.page-numbers` classes are absent, which is why every listing reported
     * hasNext=false and stopped after 20 novels out of 2724. Fall back to the pager's
     * own "صفحة N من M" counter when the next link is missing.
     */
    private fun hasNextPage(doc: Document): Boolean {
        if (doc.selectFirst("a.next, a.next.page-numbers, a.nextpostslink, .pagination a.next") != null) {
            return true
        }
        doc.selectFirst("nav.pager a, .pager a")?.let { return true }
        return readPagerCounts(doc)?.let { (cur, total) -> cur < total } ?: false
    }

    /**
     * Parse the theme's "صفحة 1 من 137" pager label into (current, total). Arabic-Indic
     * digits are normalised first, so "صفحة ١ من ١٣٧" reads the same as its ASCII form.
     */
    private fun readPagerCounts(doc: Document): Pair<Int, Int>? {
        val label = doc.selectFirst("nav.pager span, .pager span")?.text()?.trim()
            ?: doc.selectFirst("nav.pager, .pager")?.text()?.trim()
            ?: return null
        val m = PAGER_LABEL.find(normalizedDigits(label)) ?: return null
        val cur = m.groupValues[1].toIntOrNull() ?: return null
        val total = m.groupValues[2].toIntOrNull() ?: return null
        return if (cur <= 0 || total <= 0) null else cur to total
    }

    /** Dump form/select sort controls (&orderby options) to logcat for [target]. */
    private fun diagSortControls(doc: Document, target: String) {
        try {
            val opts = doc.select("select option").map { it.text().trim() to it.attr("value") }
            if (opts.isEmpty()) {
                diag("SORT $target: no <select> controls")
                return
            }
            diag("SORT $target: ${opts.take(20).joinToString(" | ") { "${it.first}=${it.second}" }}")
        } catch (e: Exception) {
            diag("SORT diag failed: ${e.message}")
        }
    }

    private fun titleOf(body: String): String {
        val start = body.indexOf("<title>", ignoreCase = true)
        if (start < 0) return ""
        val end = body.indexOf("</title>", start + 7, ignoreCase = true)
        if (end < 0) return ""
        return body.substring(start + 7, end)
    }

    private fun isChallenge(title: String): Boolean {
        if (title.isBlank()) return false
        val t = title.lowercase()
        return t.contains("just a moment") || t.contains("attention required") ||
            t.contains("security check") || t.contains("لحظة")
    }

    /**
     * Covers come out of the theme ROOT-RELATIVE ("/np-light/public/uploads/series/x.jpg" —
     * logcat v2.25), with no protocol and no host. Handed to the app that way the loader has
     * nothing to resolve against and every cover stays blank, so absolutise here. The detail
     * page's cover goes through the same path via [getCoverRequest].
     */
    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        val absoluteUrl = if (url.isBlank()) "" else getAbsoluteUrl(url.trim())
        return client to HttpRequestBuilder().apply { url(absoluteUrl) }
    }

    /**
     * True for a lazy-loader's stand-in image rather than a real cover: a 1×1 tracking pixel,
     * a base64/GIF placeholder, or a generic "no image" asset. Those live in `src`/`srcset`
     * while the real file sits in `data-src` — picking one is what made covers render blank.
     */
    /**
     * A `srcset` value is a comma-separated candidate list ("cover-320.jpg 320w, cover-640.jpg
     * 640w"). Take the LAST entry — the largest — and drop its descriptor, so a cover resolved
     * from `srcset` isn't a 320px thumbnail on a detail screen.
     */
    private fun firstSrcsetUrl(value: String): String {
        val v = value.trim()
        if (!v.contains(',')) return v
        val candidates = v.split(',').mapNotNull { part ->
            part.trim().substringBefore(' ').takeIf { it.isNotBlank() }
        }
        return candidates.lastOrNull { !isPlaceholder(it) } ?: candidates.lastOrNull() ?: ""
    }

    private fun isPlaceholder(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("data:") || u.contains("placeholder") || u.contains("blank.gif") ||
            u.contains("no-image") || u.contains("default-cover") || u.contains("spacer")
    }

    companion object {
        private const val TAG = "NovelParadise"

        // Order matters: the theme lazy-loads, so the real file is in a data-* attribute
        // while `src`/`srcset` may still hold the placeholder.
        private val IMG_ATTRS = listOf(
            "data-src", "data-lazy-src", "data-lazy", "data-original", "data-lazyload", "src", "srcset"
        )

        // The theme does NOT use the classic lightnovel card classes on the live site
        // (verified by the on-device STRUCTURE dump in v2.9: .listupd/.lexa/.maindet all = 0).
        // Cards are <article>-based; extraction goes through series anchors directly.
        const val ANY_CARD_SELECTOR = ".listupd .lexa, .maindet, .utao .uta"

        // Density match: every real listing/search page contains series <a>s whose hrefs are
        // /np-light/series/{slug}/. Also serves as the browser.fetch content marker — the
        // challenge stub has none, so contentReady() waits out Turnstile.
        const val SERIES_ANCHOR_SELECTOR = "a[href*='/np-light/series/']"

        // Novel detail / chapter / content selectors (lightnovel theme).
        const val CPAGE_CLASS = ".epcontent"
        const val SERIES_KEY = "/np-light/series/"
        const val CHAPTER_KEY = "/chapter-"

        // Marker for detail pages: a real novel detail always links to at least one chapter.
        const val CONTENT_MARKER_SELECTOR = "a[href*='$CHAPTER_KEY']"

        private val AD_TEXT_MARKERS = listOf("advertise here", "advertisement", "ad space", "ad start", "ad end")

        // The archive pager reads "صفحة 1 من 137" — "N من M".
        private val PAGER_LABEL = Regex("""(\d{1,6})\s*من\s*(\d{1,6})""")

        private val MANGAS_EMPTY = MangasPageInfo(emptyList(), false)
    }
}