package ireader.realmnovel

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.http.DEFAULT_USER_AGENT
import ireader.core.log.Log
import ireader.core.source.Dependencies
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
import ireader.core.source.SourceFactory
import ireader.core.source.dsl.filters
import ireader.core.source.model.Listing
import ireader.core.source.model.Page
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

/**
 * 🌍 RealmNovel - Arabic Novel Source
 *
 * Uses API for fetching novels and chapters with dynamic JSON parsing.
 * API Endpoints:
 * - Novels list: /api/novels?page={page}&limit=20
 * - Novel detail: /api/novels/{slug} → returns _id (novelId)
 * - Chapters list: /api/chapters/{novelId}?page={page}&limit=100
 */
@Extension
@AutoSourceId(seed = "RealmNovel")
abstract class RealmNovel(private val deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://www.realmnovel.com"
    override val id: Long get() = 44
    override val name: String get() = "RealmNovel"

    // ═══════════════════════════════════════════════════════════════
    // 🔧 JSON PARSER (Dynamic - NO @Serializable!)
    // ═══════════════════════════════════════════════════════════════
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ═══════════════════════════════════════════════════════════════
    // 🔍 FILTERS
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular", "Views 24h", "Views 30d")),
    )

    // ═══════════════════════════════════════════════════════════════
    // ⚡ COMMANDS
    // ═══════════════════════════════════════════════════════════════
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // ═══════════════════════════════════════════════════════════════
    // 📚 EXPLORE FETCHERS (Fallback for WebView)
    // ═══════════════════════════════════════════════════════════════
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novels/",
                selector = "div.novel-card, div.series-item, article.novel-item, .novel-grid .item",
                nameSelector = "h3 a, a.novel-title, .title a",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail, .novel-cover img, .thumb img",
                coverAtt = "src",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?q={query}",
                selector = "div.novel-card, div.series-item, article.novel-item, .novel-grid .item",
                nameSelector = "h3 a, a.novel-title, .title a",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail, .novel-cover img, .thumb img",
                coverAtt = "src",
                type = SourceFactory.Type.Search
            ),
        )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val sortParam = "updatedAt"

        // Fetch from API
        val url = "$baseUrl/api/novels?page=$page&limit=20&sort=$sortParam&order=desc"
        val responseText = client.get(requestBuilder(url)).bodyAsText()
        return parseNovelListResponse(responseText)
    }
    // ═══════════════════════════════════════════════════════════════
    // 📚 API-BASED NOVEL LIST FETCHING
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value

        // Handle search
        if (!query.isNullOrBlank()) {
            return searchNovels(query, page)
        }

        // Get sort option
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val sortParam = when (sortIndex) {
            0 -> "updatedAt"    // Latest
            1 -> "views"        // Popular (total views)
            2 -> "views24h"     // Views 24h
            3 -> "views30d"     // Views 30d
            else -> "updatedAt"
        }

        // Fetch from API
        val url = "$baseUrl/api/novels?page=$page&limit=20&sort=$sortParam&order=desc"
        val responseText = client.get(requestBuilder(url)).bodyAsText()
        return parseNovelListResponse(responseText)
    }

    private suspend fun searchNovels(query: String, page: Int): MangasPageInfo {
        // Use dedicated search API endpoint: /api/novels/search?q={query}
        val encodedQuery = query.trim().replace(" ", "%20")
        val url = "$baseUrl/api/novels/search?q=$encodedQuery"
        val responseText = client.get(requestBuilder(url)).bodyAsText()

        return parseSearchResponse(responseText)
    }

    /**
     * Parse search response - different structure from novel list.
     * Search API returns: {"novels": [...]} without pagination info.
     */
    private fun parseSearchResponse(responseText: String): MangasPageInfo {
        val jsonObj = json.parseToJsonElement(responseText).jsonObject

        val novelsArray = jsonObj["novels"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)

        val novels = novelsArray.mapNotNull { element ->
            val novel = element.jsonObject

            val title = novel["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val slug = novel["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cover = novel["cover"]?.jsonPrimitive?.contentOrNull ?: ""
            val description = novel["description"]?.jsonPrimitive?.contentOrNull ?: ""

            // Get categories
            val categories = novel["categories"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            } ?: emptyList()

            // Get status
            val completionStatus = novel["completionStatus"]?.jsonPrimitive?.contentOrNull ?: ""
            val status = parseStatus(completionStatus)

            MangaInfo(
                key = "$baseUrl/novel/$slug",
                title = title,
                cover = if (cover.startsWith("/")) "$baseUrl$cover" else cover,
                description = description,
                genres = categories,
                status = status
            )
        }

        // Search API doesn't have pagination, so hasMore is always false
        return MangasPageInfo(novels, false)
    }

    /**
     * Parse novel list response using dynamic JSON parsing.
     * CRITICAL: Do NOT use @Serializable data classes - causes IncompatibleClassChangeError!
     */
    private fun parseNovelListResponse(responseText: String): MangasPageInfo {
        val jsonObj = json.parseToJsonElement(responseText).jsonObject

        val novelsArray = jsonObj["novels"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
        val hasMore = jsonObj["hasMore"]?.jsonPrimitive?.booleanOrNull ?: false

        val novels = novelsArray.mapNotNull { element ->
            val novel = element.jsonObject

            val title = novel["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val slug = novel["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cover = novel["cover"]?.jsonPrimitive?.contentOrNull ?: ""
            val description = novel["description"]?.jsonPrimitive?.contentOrNull ?: ""

            // Get categories
            val categories = novel["categories"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            } ?: emptyList()

            // Get status
            val completionStatus = novel["completionStatus"]?.jsonPrimitive?.contentOrNull ?: ""
            val status = parseStatus(completionStatus)

            MangaInfo(
                key = "$baseUrl/novel/$slug",
                title = title,
                cover = if (cover.startsWith("/")) "$baseUrl$cover" else cover,
                description = description,
                genres = categories,
                status = status
            )
        }

        return MangasPageInfo(novels, hasMore)
    }

    // ═══════════════════════════════════════════════════════════════
    // 📚 API-BASED CHAPTER LIST FETCHING
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        // Check for WebView HTML first
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return chaptersParse(chapterFetch.html.asJsoup()).reversed()
        }

        // Extract slug from manga.key (e.g., "https://www.realmnovel.com/novel/some-slug")
        val slug = manga.key.substringAfterLast("/novel/").substringBefore("/").substringBefore("?")

        // First, get the novel ID from the novel detail API
        val novelDetailUrl = "$baseUrl/api/novels/$slug"
        val novelDetailText = client.get(requestBuilder(novelDetailUrl)).bodyAsText()
        val novelObj = json.parseToJsonElement(novelDetailText).jsonObject
        val novelId = novelObj["_id"]?.jsonPrimitive?.contentOrNull
            ?: return emptyList()

        // Fetch all chapters using pagination
        val allChapters = mutableListOf<ChapterInfo>()
        var currentPage = 1
        var hasMore = true

        while (hasMore) {
            val chaptersUrl = "$baseUrl/api/chapters/$novelId?page=$currentPage&limit=100"
            val chaptersText = client.get(requestBuilder(chaptersUrl)).bodyAsText()
            val chaptersObj = json.parseToJsonElement(chaptersText).jsonObject

            val chaptersArray = chaptersObj["chapters"]?.jsonArray ?: break
            val totalPages = chaptersObj["totalPages"]?.jsonPrimitive?.intOrNull ?: 1

            chaptersArray.forEach { element ->
                val chapter = element.jsonObject
                val number = chapter["number"]?.jsonPrimitive?.intOrNull ?: 0
                val title = chapter["title"]?.jsonPrimitive?.contentOrNull ?: "Chapter $number"

                allChapters.add(
                    ChapterInfo(
                        key = "$baseUrl/novel/$slug/chapter/$number",
                        name = title,
                        number = number.toFloat()
                    )
                )
            }

            hasMore = currentPage < totalPages
            currentPage++
        }

        // Sort by chapter number ascending (oldest first)
        return allChapters.sortedBy { it.number }
    }

    // ═══════════════════════════════════════════════════════════════
    // 🔧 HELPER FUNCTIONS
    // ═══════════════════════════════════════════════════════════════
    private fun parseStatus(status: String): Long {
        val lowerStatus = status.lowercase()
        return when {
            lowerStatus.contains("completed") || lowerStatus.contains("مكتملة") || lowerStatus.contains("انتهت") -> COMPLETED
            lowerStatus.contains("hiatus") || lowerStatus.contains("متوقفة") || lowerStatus.contains("معلقة") -> ON_HIATUS
            lowerStatus.contains("ongoing") || lowerStatus.contains("مستمرة") || lowerStatus.contains("جارية") -> ONGOING
            else -> ONGOING
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 🌐 CUSTOM HEADERS (Arabic language support)
    // ═══════════════════════════════════════════════════════════════
    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "application/json, text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            append(HttpHeaders.AcceptLanguage, "ar-SA,ar;q=0.9,en;q=0.8")
            block()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 📖 DETAIL FETCHER (Fallback for WebView)
    // ═══════════════════════════════════════════════════════════════
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.title, .novel-title, h1.entry-title, .post-title, h1",
            coverSelector = "img.cover, .novel-cover img, .series-cover img, .thumb img, img[src*='cover']",
            coverAtt = "src",
            descriptionSelector = "div.description, .synopsis, .summary, .post-content p:first-of-type",
            authorBookSelector = ".author a, span.author, .novel-author, .post-meta .author",
            categorySelector = ".genres a, .tags a, .categories a, .post-tags a",
            statusSelector = ".status span, .novel-status, .post-meta .status",
            onStatus = { status -> parseStatus(status) },
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CHAPTER FETCHER (Fallback for WebView)
    // ═══════════════════════════════════════════════════════════════
//    override val chapterFetcher: Chapters
//        get() = SourceFactory.Chapters(
//            selector = "ul.chapters li, .chapter-list li, div.chapter-list a, .chapters .chapter-item, a[href*='chapter']",
//            nameSelector = "a.chapter-title, a, .chapter-link",
//            linkSelector = "a",
//            linkAtt = "href",
//            reverseChapterList = true,
//        )

    // ═══════════════════════════════════════════════════════════════
    // 📄 CONTENT FETCHING - Uses API: /api/chapters/{novelId}/{chapterNumber}
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getContents(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        // Extract slug and chapter number from chapter.key
        // Format: https://www.realmnovel.com/novel/{slug}/chapter/{number}
        val urlParts = chapter.key.split("/")
        val chapterIndex = urlParts.indexOf("chapter")
        val novelIndex = urlParts.indexOf("novel")

        if (chapterIndex == -1 || novelIndex == -1) {
            // Fallback to WebView if URL format is unexpected
            val contentFetch = commands.findInstance<Command.Content.Fetch>()
            if (contentFetch != null && contentFetch.html.isNotBlank()) {
                return pageContentParse(contentFetch.html.asJsoup())
            }
            return emptyList()
        }

        val slug = urlParts.getOrNull(novelIndex + 1) ?: return emptyList()
        val chapterNumber = urlParts.getOrNull(chapterIndex + 1)?.substringBefore("?")?.toIntOrNull() ?:0

        // First, get the novel ID from the novel detail API
        val novelDetailUrl = "$baseUrl/api/novels/$slug"
        val novelDetailText = client.get(requestBuilder(novelDetailUrl)).bodyAsText()
        val novelObj = json.parseToJsonElement(novelDetailText).jsonObject
        val novelId = novelObj["_id"]?.jsonPrimitive?.contentOrNull ?: return emptyList()

        // Fetch chapter content from API
        val chapterUrl = "$baseUrl/api/chapters/$novelId/$chapterNumber"
        val chapterText = client.get(requestBuilder(chapterUrl)).bodyAsText()
        val chapterObj = json.parseToJsonElement(chapterText).jsonObject

        val title = chapterObj["title"]?.jsonPrimitive?.contentOrNull ?: ""
        val content = chapterObj["content"]?.jsonPrimitive?.contentOrNull ?: ""

        // Parse content into paragraphs
        val paragraphs = content
            .replace("\r\r\n", "\n")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                line.length > 3 &&
                !line.contains("الفصل السابق") &&
                !line.contains("الفصل التالي") &&
                !line.contains("إقرأ فقط على") &&
                !line.contains("اقرأ فقط على") &&
                !line.contains("Read only on") &&
                !line.contains("realmnovel", ignoreCase = true)
            }

        return buildList {
            if (title.isNotBlank()) add(title.toPage())
            paragraphs.forEach { paragraph ->
                add(paragraph.toPage())
            }
        }
    }
//
//    override fun pageContentParse(document: Document): List<Page> {
//        // Fallback for WebView HTML parsing
//        val contentDivs = document.select(".chapter-content-card > div:not(.my-8)")
//
//        val rawText = if (contentDivs.isNotEmpty()) {
//            contentDivs.joinToString("\n") { it.text() }
//        } else {
//            document.select("main div").text()
//        }
//
//        val paragraphs = rawText
//            .replace("\r\r\n", "\n")
//            .replace("\r\n", "\n")
//            .replace("\r", "\n")
//            .split("\n")
//            .map { it.trim() }
//            .filter { line ->
//                line.isNotBlank() &&
//                line.length > 3 &&
//                !line.contains("الفصل السابق") &&
//                !line.contains("الفصل التالي") &&
//                !line.contains("إقرأ فقط على") &&
//                !line.contains("اقرأ فقط على") &&
//                !line.contains("Read only on") &&
//                !line.contains("realmnovel", ignoreCase = true)
//            }
//
//        val title = document.selectFirst("h1")?.text()?.trim() ?: ""
//
//        return buildList {
//            if (title.isNotBlank()) add(title.toPage())
//            paragraphs.forEach { paragraph ->
//                add(paragraph.toPage())
//            }
//        }
//    }
//
//    override fun getUserAgent() = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
//
//    // ═══════════════════════════════════════════════════════════════
//    // 📄 CONTENT FETCHER (Fallback selectors for WebView)
//    // ═══════════════════════════════════════════════════════════════
//    override val contentFetcher: Content
//        get() = SourceFactory.Content(
//            pageTitleSelector = "h1",
//            pageContentSelector = ".chapter-content-card > div:not(.my-8)",
//            onContent = { contents ->
//                contents.flatMap { text ->
//                    text.replace("\r\r\n", "\n")
//                        .replace("\r\n", "\n")
//                        .replace("\r", "\n")
//                        .split("\n")
//                        .map { line -> line.trim() }
//                        .filter { line ->
//                            line.isNotBlank() &&
//                            line.length > 3 &&
//                            !line.contains("الفصل السابق") &&
//                            !line.contains("الفصل التالي") &&
//                            !line.contains("إقرأ فقط على") &&
//                            !line.contains("اقرأ فقط على") &&
//                            !line.contains("Read only on")
//                        }
//                }
//            }
//        )
}

@kotlinx.serialization.Serializable
data class ChapterContent(
    @SerialName("_id") val id: String,
    val novelId: String,
    val number: Int,
    val title: String,
    val content: String,
    val views: Int,
    val views24h: Int,
    val views30d: Int,
    val wordCount: Int,
    val isPublished: Boolean,
    val scheduledPublish: ScheduledPublish,
    val publishedAt: String,
    val lastReset24h: String,
    val lastReset30d: String,
    @SerialName("__v") val version: Int,
    val createdAt: String,
    val updatedAt: String,
    val nextChapter: Int? = null // Using nullable in case it's the last chapter
)

@Serializable
data class ScheduledPublish(
    val isScheduled: Boolean,
    val publishAt: String? = null,
    val batchNumber: Int? = null,
    val totalBatches: Int? = null,
    val chaptersPerBatch: Int? = null
)
