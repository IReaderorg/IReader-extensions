package ireader.markazriwayat

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
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
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import ireader.core.source.SourceFactory
import ireader.core.util.DefaultDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "sword",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://markazriwayat.com/novel/my-wife-is-a-sword-god/",
    chapterUrl = "https://markazriwayat.com/novel/my-wife-is-a-sword-god/%D8%A7%D9%84%D9%81%D8%B5%D9%84-838/",
    expectedTitle = "زوجتي هي حاكمة السيف",
    expectedAuthor = "لورد غامض"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false
)
abstract class MarkazRiwayat(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"

    override val baseUrl: String
        get() = "https://markazriwayat.com"

    override val id: Long
        get() = 842746329  // Unique ID for MarkazRiwayat

    override val name: String
        get() = "MarkazRiwayat"

    // JSON parser for API responses
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Recently Added",
                endpoint = "/new/",
                selector = "a.lib-card",
                nameSelector = ".lib-card__title",
                coverSelector = ".lib-card__img img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a.lib-card",
                linkAtt = "href",
                addBaseUrlToLink = true,
            ),
            BaseExploreFetcher(
                "Library",
                endpoint = "/library/",
                selector = "a.lib-card",
                nameSelector = ".lib-card__title",
                coverSelector = ".lib-card__img img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a.lib-card",
                linkAtt = "href",
                addBaseUrlToLink = true,
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/?s={query}",
                selector = "a.lib-card",
                nameSelector = ".lib-card__title",
                coverSelector = ".lib-card__img img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a.lib-card",
                linkAtt = "href",
                addBaseUrlToLink = true,
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.manga-title",
            coverSelector = ".manga-cover-wrap img",
            coverAtt = "data-src",
            addBaseurlToCoverLink = true,
            authorBookSelector = ".manga-author",
            descriptionSelector = ".manga-summary",
            statusSelector = ".manga-status-pill",
            onStatus = { status ->
                val lowerStatus = status.lowercase()
                when {
                    lowerStatus.contains("complete") || lowerStatus.contains("مكتملة") -> COMPLETED
                    lowerStatus.contains("ongoing") || lowerStatus.contains("جارية") -> ONGOING
                    else -> ONGOING
                }
            },
            categorySelector = ".pill-list .pill",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".ch-row",
            nameSelector = ".ch-title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,  // Newest first, so reverse for reading order
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".reading-content .text-right p",
        )

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM API-BASED SEARCH
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Custom search using MarkazRiwayat's JSON API
     * API endpoint: /wp-json/theam/v1/novel-search?term={query}&per_page=20
     */
    private suspend fun searchViaApi(query: String, perPage: Int = 20): MangasPageInfo {
        // Encode the search query for URL
        val encodedQuery = query.encodeURLParameter()
        val apiUrl = "$baseUrl/wp-json/theam/v1/novel-search?term=$encodedQuery&per_page=$perPage"
        
        // Fetch JSON response
        val response = client.get(requestBuilder(apiUrl)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        
        // Parse the items array
        val items = jsonObj["items"]?.jsonArray ?: emptyList()
        
        val novels = items.mapNotNull { element ->
            val item = element.jsonObject
            
            // Extract required fields
            val id = item["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val link = item["link"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cover = item["cover"]?.jsonPrimitive?.contentOrNull ?: ""
            
            // Extract genres (optional)
            val genres = item["genres"]?.jsonArray?.mapNotNull { 
                it.jsonPrimitive.contentOrNull 
            } ?: emptyList()
            
            // Extract chapter count (optional)
            val chaptersCount = item["chapters_count"]?.jsonPrimitive?.intOrNull ?: 0
            
            MangaInfo(
                key = link,
                title = title,
                cover = cover,
                genres = genres,
                // Add chapter count to description if available
                description = if (chaptersCount > 0) {
                    "عدد الفصول: $chaptersCount"
                } else {
                    ""
                }
            )
        }
        
        // API doesn't provide pagination info, so assume no next page for search
        return MangasPageInfo(novels, hasNextPage = false)
    }

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM API-BASED CHAPTER FETCHING
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Extract manga ID from the novel detail page
     * Selector: #manga-chapters-list with data-manga-id attribute
     */
    private suspend fun extractMangaId(novelUrl: String): String? {
        val document = client.get(requestBuilder(novelUrl)).asJsoup()
        return document.select("#manga-chapters-list").attr("data-manga-id").takeIf { it.isNotBlank() }
    }
    
    /**
     * Fetch chapters via API with concurrent pagination.
     * The API returns at most 100 chapters per request, so a novel with many
     * chapters needs several requests. They are fired concurrently to avoid the
     * slow sequential page-by-page fetching.
     * API endpoint: /wp-json/theam/v1/manga-chapters?manga_id={id}&order=DESC&page={page}&per_page=100
     */
    private suspend fun fetchChaptersViaApi(
        mangaId: String,
        order: String = "DESC",
        perPage: Int = 100
    ): List<ChapterInfo> = withContext(DefaultDispatcher) {
        val apiBase = "$baseUrl/wp-json/theam/v1/manga-chapters?manga_id=$mangaId"
        val firstUrl = "$apiBase&order=$order&page=1&per_page=$perPage"
        val firstJson = try {
            json.parseToJsonElement(client.get(requestBuilder(firstUrl)).bodyAsText()).jsonObject
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        val total = firstJson["total"]?.jsonPrimitive?.intOrNull ?: 0
        val apiPerPage = firstJson["per_page"]?.jsonPrimitive?.intOrNull ?: perPage
        val firstItems = firstJson["items"]?.jsonArray ?: emptyList()
        val allChapters = parseChapterItems(firstItems).toMutableList()

        if (total <= 0 || apiPerPage <= 0 || allChapters.isEmpty()) return@withContext allChapters
        val maxPage = (total + apiPerPage - 1) / apiPerPage
        if (maxPage <= 1) return@withContext allChapters

        val deferred = (2..maxPage).map { page ->
            async {
                val url = "$apiBase&order=$order&page=$page&per_page=$perPage"
                try {
                    val obj = json.parseToJsonElement(client.get(requestBuilder(url)).bodyAsText()).jsonObject
                    parseChapterItems(obj["items"]?.jsonArray ?: emptyList())
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        allChapters.addAll(deferred.awaitAll().flatten())
        allChapters
    }

    /** Parse the [items] array returned by the manga-chapters API into [ChapterInfo] objects. */
    private fun parseChapterItems(items: List<JsonElement>): List<ChapterInfo> {
        return items.mapNotNull { element ->
            val item = element.jsonObject
            val label = item["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val num = item["num"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: -1f
            val ts = item["ts"]?.jsonPrimitive?.longOrNull ?: 0L
            ChapterInfo(
                name = label,
                key = url,
                number = num,
                dateUpload = ts * 1000L,
            )
        }
    }

    /** Override getMangaList to use API search when a query is present. */
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) return searchViaApi(query)

        // Fall back to default HTML-based fetching for listings
        return super.getMangaList(filters, page)
    }

    /** Override getChapterList to always use the API for the full chapter list (HTML only exposes ~30). */
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        // Priority 1: API-based fetching with concurrency (gets all chapters)
        try {
            val mangaId = extractMangaId(manga.key)
            if (mangaId != null) {
                val chapters = fetchChaptersViaApi(mangaId, perPage = 100)
                if (chapters.isNotEmpty()) return chapters.reversed()
            }
        } catch (e: Exception) {
            // Fall through to HTML-based fetching
        }

        // Priority 2: Check for WebView HTML
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return chaptersParse(chapterFetch.html.asJsoup()).reversed()
        }

        // Priority 3: HTML-based fetching (only first ~30 chapters)
        try {
            val document = client.get(requestBuilder(manga.key)).asJsoup()
            val chapters = chaptersParse(document)
            if (chapters.isNotEmpty()) return chapters.reversed()
        } catch (e: Exception) {
            // Return empty list if all methods fail
        }

        return emptyList()
    }

    /**
     * The chapter page embeds copy-protection watermarks (span.theam-chobf) inside the
     * real paragraph elements. The default [onContent] filter would discard whole
     * paragraphs because their combined text contains the watermark phrases, which is
     * why real chapter text was missing. Strip the watermark spans first so the real
     * paragraph text is preserved.
     */
    override fun pageContentParse(document: Document): List<Page> {
        val doc = document.clone()
        val contentEl = doc.selectFirst(".reading-content") ?: return emptyList()

        // Remove watermark / copy-protection spans embedded inside paragraphs
        contentEl.select("span.theam-chobf, span[data-theam-chobf]").remove()
        // Remove any non-content markup
        contentEl.select("script, style, noscript, iframe").remove()

        val paragraphs = contentEl.select(".text-right > p").takeIf { it.isNotEmpty() }
            ?: contentEl.select("p")

        return paragraphs.mapNotNull { p ->
            var text = p.text().trim()
            if (text.isBlank()) return@mapNotNull null
            text = text.replace(Regex("\\s{2,}"), " ")
            val lower = text.lowercase()
            if (
                lower.contains("مركز الروايات") ||
                lower.contains("محتوى مسروق") ||
                lower.contains("النسخة الأصلية") ||
                lower.contains("تابعونا") ||
                lower.contains("رابط الفصل") ||
                lower.contains("ترجمة النص") ||
                lower.contains("المحتوى الأصلي") ||
                lower.contains("نافذة القراءة")
            ) return@mapNotNull null
            text
        }.map { Text(it) }
    }
}
