package ireader.sunovels

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.log.Log
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
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import tachiyomix.annotations.Extension

@Extension
abstract class Sunovels(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://sunovels.com"
    override val id: Long get() = 42
    override val name: String get() = "Sunovels"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Select("الحالة", arrayOf("الكل", "مكتمل", "جديد", "مستمر")),
        Filter.Select("التصنيف", arrayOf(
            "الكل", "Wuxia", "Xianxia", "XUANHUAN", "أصلية", "أكشن", "إثارة",
            "إنتقال الى عالم أخر", "إيتشي", "الخيال العلمي", "بوليسي", "تاريخي",
            "تقمص شخصيات", "جريمة", "جوسى", "حريم", "حياة مدرسية", "خارقة للطبيعة",
            "خيالي", "دراما", "رعب", "رومانسي", "سحر", "سينن", "شريحة من الحياة",
            "شونين", "غموض", "فنون القتال", "قوى خارقة", "كوميدى", "مأساوي",
            "ما بعد الكارثة", "مغامرة", "ميكا", "ناضج", "نفسي", "فانتازيا",
            "رياضة", "ابراج", "الالهة", "شياطين", "السفر عبر الزمن", "رواية صينية",
            "رواية ويب", "لايت نوفل", "كوري", "+18", "إيسكاي", "ياباني", "مؤلفة"
        ))
    )

    private val statusMap = mapOf(0 to "", 1 to "Completed", 2 to "New", 3 to "Ongoing")
    private val categoryMap = mapOf(
        0 to "", 1 to "Wuxia", 2 to "Xianxia", 3 to "XUANHUAN", 4 to "أصلية",
        5 to "أكشن", 6 to "إثارة", 7 to "إنتقال+الى+عالم+أخر", 8 to "إيتشي",
        9 to "الخيال+العلمي", 10 to "بوليسي", 11 to "تاريخي", 12 to "تقمص+شخصيات",
        13 to "جريمة", 14 to "جوسى", 15 to "حريم", 16 to "حياة+مدرسية",
        17 to "خارقة+للطبيعة", 18 to "خيالي", 19 to "دراما", 20 to "رعب",
        21 to "رومانسي", 22 to "سحر", 23 to "سينن", 24 to "شريحة+من+الحياة",
        25 to "شونين", 26 to "غموض", 27 to "فنون+القتال", 28 to "قوى+خارقة",
        29 to "كوميدى", 30 to "مأساوي", 31 to "ما+بعد+الكارثة", 32 to "مغامرة",
        33 to "ميكا", 34 to "ناضج", 35 to "نفسي", 36 to "فانتازيا",
        37 to "رياضة", 38 to "ابراج", 39 to "الالهة", 40 to "شياطين",
        41 to "السفر+عبر+الزمن", 42 to "رواية+صينية", 43 to "رواية+ويب",
        44 to "لايت+نوفل", 45 to "كوري", 46 to "%2B18", 47 to "إيسكاي",
        48 to "ياباني", 49 to "مؤلفة"
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                key = "Latest", endpoint = "/library",
                selector = "li.list-item", nameSelector = "h4, h3",
                linkSelector = "a[href*='/novel/']", linkAtt = "href",
                coverSelector = "img", coverAtt = "src",
                addBaseUrlToLink = true, addBaseurlToCoverLink = false,
                maxPage = 1, onCover = { _, _ -> "" }
            ),
            BaseExploreFetcher(
                key = "Search", endpoint = "/search?title={query}",
                selector = "li.list-item", nameSelector = "h4, h3",
                linkSelector = "a[href*='/novel/']", linkAtt = "href",
                coverSelector = "img", coverAtt = "src",
                addBaseUrlToLink = true, addBaseurlToCoverLink = false,
                type = Type.Search, onCover = { _, _ -> "" }
            )
        )

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val doc = client.get(requestBuilder("$baseUrl/search?title=$encoded")).asJsoup()
                parseNovelList(doc)
            } catch (e: Exception) {
                Log.error { "Search error: ${e.message}" }
                MangasPageInfo(emptyList(), false)
            }
        }
        val selects = filters.filterIsInstance<Filter.Select>()
        val status = statusMap[selects.getOrNull(0)?.value ?: 0] ?: ""
        val category = categoryMap[selects.getOrNull(1)?.value ?: 0] ?: ""
        val url = buildString {
            append("$baseUrl/library?")
            if (category.isNotBlank()) append("&category=$category")
            if (status.isNotBlank()) append("&status=$status")
            append("&page=${page - 1}")
        }
        return try {
            val doc = client.get(requestBuilder(url)).asJsoup()
            parseNovelList(doc)
        } catch (e: Exception) {
            Log.error { "Library error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private fun parseNovelList(doc: Document): MangasPageInfo {
        val novels = doc.select("li.list-item a[href*='/novel/']").mapNotNull { el ->
            val title = el.selectFirst("h4")?.text()?.trim()
                ?: el.selectFirst("h3")?.text()?.trim()
                ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null
            val href = el.attr("href")
            if (href.isBlank()) return@mapNotNull null
            MangaInfo(
                key = if (href.startsWith("http")) href else "$baseUrl$href",
                title = title, cover = ""
            )
        }
        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    // ── Detail ───────────────────────────────────────────────

    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = "meta[property='og:title']", nameAtt = "content",
            coverSelector = "meta[property='og:image']", coverAtt = "content",
            descriptionSelector = "meta[name='description']", descriptionBookAtt = "content",
            categorySelector = "a[href*='category']",
            statusSelector = "meta[property='og:title']",
            onStatus = { ONGOING }
        )

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        if (detailFetch != null && detailFetch.html.isNotBlank()) {
            return parseDetailFromHtml(detailFetch.html.asJsoup(), manga)
        }
        return try {
            val doc = client.get(requestBuilder(manga.key)).asJsoup()
            parseDetailFromHtml(doc, manga)
        } catch (e: Exception) {
            Log.error { "Detail error: ${e.message}" }
            manga
        }
    }

    private fun parseDetailFromHtml(doc: Document, manga: MangaInfo): MangaInfo {
        val ogTitle = doc.select("meta[property='og:title']").attr("content")
        val title = ogTitle
            .replace(Regex("^رواية\\s+"), "")
            .replace(Regex("\\s+\\d+\\s*$"), "")
            .trim().ifBlank { manga.title }
        val cover = doc.select("meta[property='og:image']").attr("content").ifBlank { manga.cover }
        val description = doc.select("meta[name='description']").attr("content").trim()
        val bodyText = doc.text()
        val status = when {
            bodyText.contains("مستمر") || bodyText.contains("Ongoing") -> ONGOING
            bodyText.contains("مكتمل") || bodyText.contains("Completed") -> COMPLETED
            else -> manga.status
        }
        val categories = doc.select("a[href*='category']")
            .mapNotNull { it.text().trim() }.filter { it.isNotBlank() }
        return manga.copy(
            title = title, cover = cover,
            description = description.ifBlank { manga.description },
            genres = categories.ifEmpty { manga.genres },
            status = status
        )
    }

    // ── Chapters ─────────────────────────────────────────────
    // sunovels.com renders chapters as SSR HTML when fetching ?activeTab=chapters.
    // Each page returns ~50 chapters in <a> tags inside <ul class="chaptersList">.

    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = "ul.chaptersList a[href*='/novel/']",
            nameSelector = "strong.chapter-title",
            linkSelector = "a", linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = false
        )

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            val chapters = parseChaptersFromHtml(chapterFetch.html.asJsoup(), manga)
            if (chapters.isNotEmpty()) return chapters
        }

        return try {
            val novelSlug = manga.key.substringAfter("/novel/", "").substringBefore("?")
            val allChapters = mutableListOf<ChapterInfo>()

            // Fetch first page and determine total pages
            val firstDoc = fetchChaptersPageDoc(novelSlug, 0)
            allChapters.addAll(parseChaptersFromHtml(firstDoc, manga))

            val totalPages = firstDoc.select("ul.pagination li a[aria-label*=Page]").map {
                it.attr("aria-label").replace(Regex("[^0-9]"), "").trim()
            }.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 1

            // Fetch remaining pages
            val maxPages = minOf(totalPages, 50)
            for (page in 1 until maxPages) {
                try {
                    val doc = fetchChaptersPageDoc(novelSlug, page)
                    val chapters = parseChaptersFromHtml(doc, manga)
                    if (chapters.isEmpty()) break
                    allChapters.addAll(chapters)
                } catch (e: Exception) {
                    Log.error { "Error fetching chapters page $page: ${e.message}" }
                }
            }

            allChapters.distinctBy { it.key }.sortedBy { it.number }
        } catch (e: Exception) {
            Log.error { "Chapter list error: ${e.message}" }
            emptyList()
        }
    }

    private suspend fun fetchChaptersPageDoc(novelSlug: String, page: Int): Document {
        val url = "$baseUrl/novel/$novelSlug?activeTab=chapters&page=$page"
        val response = client.get(requestBuilder(url))
        val body = response.bodyAsText()
        return Ksoup.parse(body)
    }

    private fun parseChaptersFromHtml(doc: Document, manga: MangaInfo): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        val novelSlug = manga.key.substringAfter("/novel/", "").substringBefore("?")

        // Chapters are in <a title="X الفصل" href="/novel/slug/N"> inside <ul class="chaptersList">
        doc.select("a[title][href*='/novel/$novelSlug/']").forEach { el ->
            val title = el.attr("title").trim()
            val href = el.attr("href")
            if (title.isBlank() || href.isBlank()) return@forEach

            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            if (chapters.any { it.key == fullUrl }) return@forEach

            val name = title.ifBlank { el.text().trim() }
            if (name.isBlank()) return@forEach

            // Extract number from URL slug (more reliable than from Arabic title)
            val slug = href.substringAfterLast("/")
            val number = slug.replace(Regex("[^0-9].*"), "").toFloatOrNull()
                ?: ChapterInfo.extractChapterNumber(name)
            chapters.add(ChapterInfo(name = name, key = fullUrl, number = number))
        }

        // Fallback: look for <strong class="chapter-title"> elements
        if (chapters.isEmpty()) {
            doc.select("a[href*='/novel/$novelSlug/']").forEach { el ->
                val name = el.selectFirst("strong.chapter-title")?.text()?.trim()
                    ?: el.attr("title").trim()
                    ?: el.text().trim()
                val href = el.attr("href")
                if (name.isBlank() || href.isBlank()) return@forEach

                val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                if (chapters.any { it.key == fullUrl }) return@forEach

                val slug = href.substringAfterLast("/")
                val number = slug.replace(Regex("[^0-9].*"), "").toFloatOrNull()
                    ?: ChapterInfo.extractChapterNumber(name)
                chapters.add(ChapterInfo(name = name, key = fullUrl, number = number))
            }
        }

        return chapters.distinctBy { it.key }.sortedBy { it.number }
    }

    // ── Content ──────────────────────────────────────────────

    override val contentFetcher: Content
        get() = Content(
            pageTitleSelector = "h2",
            pageContentSelector = "div.chapter-content > p:not(.d-none)",
            onContent = { contents ->
                contents
                    .filter { it.isNotBlank() }
                    .filter { !it.contains("Tahtoh", ignoreCase = true) }
                    .filter { !it.contains("شمس الروايات", ignoreCase = true) }
                    .map { it.trim() }
            }
        )

    override fun pageContentParse(document: Document): List<Page> {
        val content = mutableListOf<String>()
        val title = document.selectFirst("h2")?.text()?.trim()
        if (!title.isNullOrBlank()) content.add(title)
        val paragraphs = document.select("div.chapter-content > p:not(.d-none)")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .filter { !it.contains("Tahtoh", ignoreCase = true) }
            .filter { !it.contains("شمس الروايات", ignoreCase = true) }
            .filter { !it.startsWith("©") }
            .filter { it.length > 5 }
        content.addAll(paragraphs)
        if (content.size <= 1) {
            val fallback = document.select("section.page-in.content-wrap p:not(.d-none)")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 5 }
            if (fallback.isNotEmpty()) content.addAll(fallback)
        }
        return content.map { Text(it) }
    }

    override fun getUserAgent(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}
