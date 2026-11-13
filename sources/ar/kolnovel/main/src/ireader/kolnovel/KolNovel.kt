package ireader.kolnovel

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.findInstance
import ireader.core.source.helpers.DateParser
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations

@Extension
@AutoSourceId(seed = "KolNovel")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "مانهوا",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://kolnovel.com/series/48hours-a-day/",
    chapterUrl = "https://kolnovel.com/shaag2448hours-a-dayz435ggye-100093/",
    expectedTitle = "48 ساعة باليوم",
    expectedMinChapters = 100
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false
)
abstract class KolNovel(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://kolnovel.com"
    override val id: Long get() = KolNovelSourceId.ID
    override val name: String get() = "KolNovel"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular")),
        Filter.Select("Type", arrayOf("All", "Japanese", "Korean", "English", "Chinese")),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return fetchMangaList("$baseUrl/series/?order=update&paged=$page", page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val sortFilter = filters.findInstance<Filter.Sort>()

        val searchQuery = titleFilter?.value?.takeIf { it.isNotBlank() }
        if (searchQuery != null) {
            return fetchMangaList("$baseUrl/?s=${searchQuery.encodeURLParameter()}&paged=$page", page)
        }

        val sortParam = when (sortFilter?.value?.index) {
            1 -> "order=popular"
            else -> "order=update"
        }

        return fetchMangaList("$baseUrl/series/?$sortParam&paged=$page", page)
    }

    private suspend fun fetchMangaList(url: String, page: Int = 1): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder(url))
            val body = response.bodyAsText()
            parseMangaList(body, page)
        } catch (e: Exception) {
            Log.error { "KolNovel list error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private fun parseMangaList(body: String, page: Int): MangasPageInfo {
        val doc = Ksoup.parse(body)
        val mangaList = mutableListOf<MangaInfo>()

        // New "starless" theme: archive rows on /series/ and search results use a different card.
        doc.select("article.kol-series-archive__item, article.kol-search__item, article.maindet, .kol-home-update-row")
            .forEach { card ->
                val titleEl = card.selectFirst(
                    "h3[itemprop=headline] a, h2[itemprop=headline] a, .mdinfo h2 a, h3 a, .post-title a"
                ) ?: return@forEach

                val title = titleEl.text().trim()
                val key = seriesKey(titleEl.attr("href")) ?: return@forEach
                if (title.isBlank()) return@forEach

                val cover = card.selectFirst(
                    ".kol-series-archive__cover img, .kol-search__cover img, .mdthumb img, img"
                )?.attr("src")?.trim().orEmpty()

                mangaList.add(MangaInfo(key = key, title = title, cover = absoluteUrl(cover)))
            }

        if (mangaList.isEmpty()) {
            doc.select("a[href*='/series/']").mapNotNull { el ->
                val title = el.text().trim()
                if (title.isBlank()) return@mapNotNull null
                val key = seriesKey(el.attr("href")) ?: return@mapNotNull null
                val cover = el.closest("article, .post, .novel-item, .card")?.selectFirst("img")
                    ?.attr("src")?.trim().orEmpty()
                MangaInfo(key = key, title = title, cover = absoluteUrl(cover))
            }.forEach { mangaList.add(it) }
        }

        val distinct = mangaList.distinctBy { it.key }
        return MangasPageInfo(distinct, distinct.isNotEmpty() && hasNextPage(doc, page, distinct.size))
    }

    private fun hasNextPage(doc: Document, page: Int, itemCount: Int): Boolean {
        val navText = doc.selectFirst(
            "nav.kol-series-archive__pagination, nav.kol-search__pagination, nav[aria-label*=\"صفح\"]"
        )?.text().orEmpty()
        val totalPages = Regex("""من\s*(\d+)""").find(navText)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (totalPages != null) {
            val currentPage = Regex("""صفحة\s*(\d+)""").find(navText)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: page
            return currentPage < totalPages
        }
        return itemCount >= LIST_PAGE_SIZE
    }

    /** `https://kolnovel.com/series/<slug>/` — rejects archive/type/genre links. */
    private fun seriesKey(href: String): String? {
        val full = absoluteUrl(href).substringBefore('?').substringBefore('#')
        val marker = "/series/"
        val index = full.indexOf(marker)
        if (index < 0) return null
        val slug = full.substring(index + marker.length).trim('/')
        if (slug.isBlank() || slug.contains('/')) return null
        return "$baseUrl/series/$slug/"
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetailsFromHtml(cmd.html, manga)
        }

        val html = try {
            val response = client.get(requestBuilder(manga.key))
            response.bodyAsText()
        } catch (e: Exception) { return manga }

        return parseDetailsFromHtml(html, manga)
    }

    private fun parseDetailsFromHtml(html: String, manga: MangaInfo): MangaInfo {
        val doc = Ksoup.parse(html)
        val scrapedTitle = doc.selectFirst(
            "h1.kol-series-editorial__title, .post-title h1, h1[itemprop=name], h1"
        )?.text()?.trim()
        val title = if (!scrapedTitle.isNullOrBlank() && !scrapedTitle.contains("Loading", ignoreCase = true)) {
            scrapedTitle
        } else {
            manga.title
        }
        val cover = doc.selectFirst(
            ".kol-series-editorial__cover img, .summary_image img, meta[property=og:image]"
        )?.let { el ->
            if (el.tagName().equals("meta", ignoreCase = true)) el.attr("content") else el.attr("src")
        }?.trim()?.takeIf { it.isNotBlank() } ?: manga.cover

        val description = doc.selectFirst(
            ".kol-series-editorial__prose[itemprop=description], .kol-series-editorial__prose, .summary__content, .description-summary"
        )?.text()?.trim().orEmpty()

        val author = doc.selectFirst(
            ".kol-series-editorial__byline a, .author a, .summary-content .author"
        )?.text()?.trim().orEmpty()

        val genres = doc.select(
            ".kol-series-editorial__genres a, .genres-content a, .wp-manga-genres a"
        ).map { it.text().trim() }.filter { it.isNotBlank() }

        val status = doc.selectFirst(
            ".kol-series-editorial__quick-fact--status dd, .post-status .summary-content"
        )?.text()?.trim().orEmpty()

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = genres,
            status = when {
                status.contains("Completed", ignoreCase = true) || status.contains("مكتمل", ignoreCase = true) -> MangaInfo.COMPLETED
                status.contains("Ongoing", ignoreCase = true) || status.contains("مستمر", ignoreCase = true) -> MangaInfo.ONGOING
                status.contains("Hiatus", ignoreCase = true) || status.contains("متوقف", ignoreCase = true) -> MangaInfo.ON_HIATUS
                else -> MangaInfo.UNKNOWN
            }
        )
    }

    override fun getUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = "li.kol-series-chapter[data-kol-chapter-row], li[data-ID]",
            nameSelector = ".kol-series-chapter__title-copy",
            linkSelector = "a.kol-series-chapter__link, a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = true
        )

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                val chapters = loadChapters(cmd.html, manga.key)
                if (chapters.isNotEmpty()) return chapters
            }
        }

        return try {
            val response = client.get(requestBuilder(manga.key))
            val body = response.bodyAsText()
            loadChapters(body, manga.key)
        } catch (e: Exception) {
            Log.error { "KolNovel chapters error: ${e.message}" }
            emptyList()
        }
    }

    /**
     * The series page only renders 24 chapters (newest first). The complete list comes from
     * the theme's AJAX endpoint, which is paginated 24 chapters at a time, so every page has
     * to be requested and merged.
     */
    private suspend fun loadChapters(seriesHtml: String, mangaUrl: String): List<ChapterInfo> {
        val doc = Ksoup.parse(seriesHtml)

        val ajaxChapters = try {
            fetchChaptersViaAjax(doc, mangaUrl)
        } catch (e: Exception) {
            Log.error { "KolNovel ajax chapters error: ${e.message}" }
            emptyList()
        }
        if (ajaxChapters.isNotEmpty()) return sortChapters(ajaxChapters)

        // Fallback: server-rendered pages with order=oldest (heavier, but has no nonce).
        val pagedChapters = try {
            fetchChaptersViaPagedHtml(mangaUrl)
        } catch (e: Exception) {
            Log.error { "KolNovel paged chapters error: ${e.message}" }
            emptyList()
        }
        if (pagedChapters.isNotEmpty()) return sortChapters(pagedChapters)

        // Last resort: whatever the series page itself rendered.
        return sortChapters(parseChapterRows(seriesHtml))
    }

    private fun sortChapters(chapters: List<ChapterInfo>): List<ChapterInfo> =
        chapters.distinctBy { it.key }
            .sortedBy { if (it.number >= 0f) it.number else Float.MAX_VALUE }

    private suspend fun fetchChaptersViaAjax(doc: Document, mangaUrl: String): List<ChapterInfo> {
        val nonce = doc.select("[data-kol-volume-nonce]").attr("data-kol-volume-nonce").trim()
        val seriesId = doc.select("[data-kol-series-id]").attr("data-kol-series-id").trim()
        if (nonce.isBlank() || seriesId.isBlank()) return emptyList()

        val ajaxUrl = doc.select("[data-kol-volume-url]").attr("data-kol-volume-url").trim()
            .ifBlank { "$baseUrl/wp-admin/admin-ajax.php" }

        val firstPage = fetchAjaxChapterPage(ajaxUrl, nonce, seriesId, 1, mangaUrl)
            ?: return emptyList()

        val collected = LinkedHashMap<String, ChapterInfo>()
        firstPage.chapters.forEach { collected[it.key] = it }

        val totalPages = firstPage.pages.coerceIn(1, MAX_CHAPTER_PAGES)
        var page = 2
        while (page <= totalPages) {
            val chunk = (page..minOf(page + FETCH_CONCURRENCY - 1, totalPages)).toList()
            val results = coroutineScope {
                chunk.map { p ->
                    async { fetchAjaxChapterPage(ajaxUrl, nonce, seriesId, p, mangaUrl) }
                }.awaitAll()
            }
            results.forEachIndexed { index, result ->
                val pageResult = result
                    ?: fetchAjaxChapterPage(ajaxUrl, nonce, seriesId, chunk[index], mangaUrl)
                pageResult?.chapters?.forEach { collected[it.key] = it }
            }
            page += FETCH_CONCURRENCY
        }

        return collected.values.toList()
    }

    private suspend fun fetchAjaxChapterPage(
        ajaxUrl: String,
        nonce: String,
        seriesId: String,
        page: Int,
        referer: String,
    ): AjaxChapterPage? {
        return try {
            val url = "$ajaxUrl?action=kol_series_volume_chapters" +
                "&nonce=$nonce&series_id=$seriesId&volume=__kol_all__" +
                "&page=$page&anchor_id=0&search=&order=oldest"
            val body = client.get(
                requestBuilder(url) {
                    append(HttpHeaders.UserAgent, getUserAgent())
                    append(HttpHeaders.CacheControl, "max-age=0")
                    append(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
                    append("X-Requested-With", "XMLHttpRequest")
                    append("Referer", referer)
                }
            ).bodyAsText()

            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return null
            if (root["success"]?.jsonPrimitive?.booleanOrNull == false) return null

            val data = root["data"]?.let { runCatching { it.jsonObject }.getOrNull() }
                ?: return null
            val pages = data["pages"]?.jsonPrimitive?.intOrNull ?: 1
            val html = data["html"]?.jsonPrimitive?.contentOrNull.orEmpty()

            AjaxChapterPage(pages, parseChapterRows(html))
        } catch (e: Exception) {
            Log.error { "KolNovel ajax page $page error: ${e.message}" }
            null
        }
    }

    private suspend fun fetchChaptersViaPagedHtml(mangaUrl: String): List<ChapterInfo> {
        val base = mangaUrl.substringBefore('?').substringBefore('#').trimEnd('/')
        val collected = LinkedHashMap<String, ChapterInfo>()
        var totalPages = 0

        for (page in 1..MAX_CHAPTER_PAGES) {
            val url = "$base/?chapter_volume=__kol_all__&chapter_search=&chapter_order=oldest" +
                "&chapter_page=$page#chapters"
            val html = try {
                client.get(
                    requestBuilder(url) {
                        append(HttpHeaders.UserAgent, getUserAgent())
                        append(HttpHeaders.CacheControl, "max-age=0")
                    }
                ).bodyAsText()
            } catch (e: Exception) {
                Log.error { "KolNovel chapter page $page error: ${e.message}" }
                break
            }

            val rows = parseChapterRows(html)
            if (rows.isEmpty()) break
            rows.forEach { collected[it.key] = it }

            if (totalPages == 0) {
                totalPages = Ksoup.parse(html)
                    .selectFirst("[data-kol-volume-pages]")
                    ?.attr("data-kol-volume-pages")
                    ?.toIntOrNull() ?: 0
            }
            if (totalPages > 0 && page >= totalPages) break
            if (totalPages == 0 && rows.size < SERVER_PAGE_SIZE) break
        }

        return collected.values.toList()
    }

    private fun parseChapterRows(html: String): List<ChapterInfo> {
        if (html.isBlank()) return emptyList()
        val doc = Ksoup.parse(html)
        val rows = doc.select("li.kol-series-chapter[data-kol-chapter-row]")
            .ifEmpty { doc.select("li[data-ID]") }

        val chapters = mutableListOf<ChapterInfo>()
        rows.forEach { row ->
            val link = row.selectFirst("a.kol-series-chapter__link")
                ?: row.selectFirst("a[href*='/shaag'], a[href*='/chapter/']")
                ?: return@forEach

            val href = link.attr("href").trim()
            if (href.isBlank()) return@forEach
            val url = absoluteUrl(href)
            if (url.isBlank()) return@forEach

            // New markup: <span class="kol-series-chapter__number" data-kol-chapter-number="25">
            val numberText = row.selectFirst(".kol-series-chapter__number")?.text()?.trim().orEmpty()
            val numberAttr = row.selectFirst("[data-kol-chapter-number]")
                ?.attr("data-kol-chapter-number")?.trim().orEmpty()
            val number = numberAttr.toFloatOrNull()
                ?: parseChapterNumber(numberText).takeIf { it >= 0f }
                ?: parseChapterNumber(row.text()).takeIf { it >= 0f }
                ?: -1f

            val rawTitle = row.selectFirst(".kol-series-chapter__title-copy")?.text()?.trim().orEmpty()
            val title = rawTitle.takeUnless {
                it.isBlank() || it == "—" || it == "-" || it == "–" || it == "ـ"
            }.orEmpty()

            val numberLabel = when {
                numberText.isNotBlank() -> numberText
                numberAttr.isNotBlank() -> "فصل $numberAttr"
                else -> ""
            }

            val name = when {
                numberLabel.isNotBlank() && title.isNotBlank() -> "$numberLabel - $title"
                numberLabel.isNotBlank() -> numberLabel
                title.isNotBlank() -> title
                else -> link.text().trim()
            }
            if (name.isBlank()) return@forEach

            val dateUpload = row.selectFirst("time.kol-series-chapter__date")
                ?.attr("datetime")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { DateParser.parse(it) }.getOrDefault(0L) }
                ?: 0L

            chapters.add(ChapterInfo(name = name, key = url, number = number, dateUpload = dateUpload))
        }
        return chapters
    }

    private fun parseChapterNumber(name: String): Float {
        val patterns = listOf(
            Regex("""الفصل\s+(\d+(?:\.\d+)?)"""),
            Regex("""فصل\s+(\d+(?:\.\d+)?)"""),
            Regex("""Chapter\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(name)
            if (match != null) {
                return match.groupValues[1].toFloatOrNull() ?: -1f
            }
        }
        return -1f
    }

    private fun absoluteUrl(url: String): String {
        val value = url.trim()
        return when {
            value.isBlank() -> ""
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$baseUrl$value"
            else -> "$baseUrl/$value"
        }
    }

    override val contentFetcher: Content
        get() = Content(
            pageContentSelector = "#kol_content p",
        )

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseContentFromHtml(cmd.html)
        }

        return try {
            val response = client.get(requestBuilder(chapter.key))
            val body = response.bodyAsText()
            parseContentFromHtml(body)
        } catch (e: Exception) {
            listOf(Text("محتوى الفصل غير متاح."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)

        // The new reader keeps the chapter body in #kol_content.epcontent.entry-content.
        val contentDiv = doc.selectFirst("#kol_content")
            ?: doc.selectFirst(".epcontent")
            ?: doc.selectFirst(".reading-content, .text-left, .chapter-content, .entry-content")

        if (contentDiv != null) {
            val paragraphs = contentDiv.select("p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 1 }
                .filter { !it.startsWith("PDF") && !it.startsWith("اخبار") }
                .filter { !it.contains("ادعم الرواية", ignoreCase = true) }
                .filter { it != "\u00a0" && it != "&nbsp;" }
            if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }

            val text = contentDiv.text()
            if (text.isNotBlank()) {
                return text.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length > 5 }
                    .filter { it != "\u00a0" }
                    .map { Text(it) }
            }
        }

        val paragraphs = doc.select("#kol_content p, .epcontent p, .entry-content p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && it.length > 5 && it != "\u00a0" }
        if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }

        val bodyText = doc.body()?.text() ?: ""
        return bodyText.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 10 }
            .map { Text(it) }
    }

    private data class AjaxChapterPage(
        val pages: Int,
        val chapters: List<ChapterInfo>,
    )

    private companion object {
        const val LIST_PAGE_SIZE = 20
        const val SERVER_PAGE_SIZE = 24
        const val FETCH_CONCURRENCY = 6
        const val MAX_CHAPTER_PAGES = 100
    }
}
