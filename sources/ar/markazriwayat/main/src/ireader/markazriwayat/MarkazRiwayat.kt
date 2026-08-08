package ireader.markazriwayat

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
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import ireader.core.util.DefaultDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
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
    novelUrl = "https://markazriwayat.com/novel/زوجتي-هي-حاكمة-السيف/",
    chapterUrl = "https://markazriwayat.com/novel/زوجتي-هي-حاكمة-السيف/الفصل-1/",
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

    class PopularListing : Listing("الأكثر شهرة")
    class NewListing : Listing("أضيف حديثاً")
    class LatestChaptersListing : Listing("أحدث الفصول")

    override fun getListings(): List<Listing> {
        return listOf(
            PopularListing(),
            NewListing(),
            LatestChaptersListing(),
        )
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return when (sort) {
            is PopularListing -> getLists(exploreFetchers[0], page, "", emptyList())
            is NewListing -> getLists(exploreFetchers[1], page, "", emptyList())
            is LatestChaptersListing -> getLists(exploreFetchers[2], page, "", emptyList())
            else -> getLists(
                exploreFetchers.firstOrNull { it.type != Type.Search } ?: return emptyMangaPage(),
                page,
                "",
                emptyList(),
            )
        }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "الأكثر شهرة",
                endpoint = "/popular/",
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
                "أضيف حديثاً",
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
                "أحدث الفصول",
                endpoint = "/",
                selector = "article.latest-card",
                nameSelector = "a.latest-title",
                coverSelector = "a.latest-cover img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a.latest-title",
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
            numberSelector = ".ch-num",
            reverseChapterList = true,
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
     * Fetch ALL chapters via API with fast parallel pagination.
     * The server caps per_page at 100, so we fetch page 1 (which returns `total`)
     * then download the remaining pages concurrently.
     * API endpoint: /wp-json/theam/v1/manga-chapters?manga_id={id}&order=DESC&page={page}&per_page=100
     */
    private suspend fun fetchChaptersViaApi(mangaId: String): List<ChapterInfo> {
        return withContext(DefaultDispatcher) {
            val firstPage = fetchApiChapterPage(mangaId, 1)
                ?: return@withContext emptyList()
            val total = firstPage.second
            val totalPages = ((total + 99) / 100).coerceAtLeast(1)

            val allChapters = firstPage.first.toMutableList()
            val remaining = (2..totalPages).map { page ->
                async { fetchApiChapterPage(mangaId, page) }
            }
            remaining.forEach { deferred ->
                deferred.await()?.first?.let(allChapters::addAll)
            }
            allChapters.sortedByDescending { it.number }
        }
    }

    /**
     * Fetch a single page of chapters from the API.
     * Returns the parsed chapters plus the reported `total` count.
     */
    private suspend fun fetchApiChapterPage(mangaId: String, page: Int): Pair<List<ChapterInfo>, Int>? {
        return try {
            val apiUrl = "$baseUrl/wp-json/theam/v1/manga-chapters?manga_id=$mangaId&order=DESC&page=$page&per_page=100"
            val response = client.get(requestBuilder(apiUrl)).bodyAsText()
            val jsonObj = json.parseToJsonElement(response).jsonObject
            val total = jsonObj["total"]?.jsonPrimitive?.intOrNull ?: 0
            val items = jsonObj["items"]?.jsonArray ?: return null

            val chapters = items.mapNotNull { element ->
                val item = element.jsonObject
                val label = item["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val num = item["num"]?.jsonPrimitive?.contentOrNull ?: ""
                val ts = item["ts"]?.jsonPrimitive?.longOrNull ?: 0L
                ChapterInfo(
                    name = label,
                    key = url,
                    number = num.toFloatOrNull() ?: 0f,
                    dateUpload = ts
                )
            }
            chapters to total
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchLatestChapters(page: Int): MangasPageInfo {
        val perPage = 50
        val apiUrl = "$baseUrl/wp-json/theam/v1/latest-chapters?per_page=$perPage&page=$page"
        return try {
            val response = client.get(requestBuilder(apiUrl)).bodyAsText()
            val jsonObj = json.parseToJsonElement(response).jsonObject
            val items = jsonObj["items"]?.jsonArray ?: emptyList()
            val novels = items.mapNotNull { element ->
                val item = element.jsonObject
                val title = item["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val permalink = item["permalink"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val cover = item["cover"]?.jsonPrimitive?.contentOrNull ?: ""
                MangaInfo(key = permalink, title = title, cover = cover)
            }
            val hasNext = items.size >= perPage
            MangasPageInfo(novels, hasNext)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    /**
     * Override getMangaList to use API search when query is present
     */
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        // Check if there's a search query
        val query = filters.findInstance<Filter.Title>()?.value
        
        if (!query.isNullOrBlank()) {
            // Use API-based search
            return searchViaApi(query)
        }
        
        // Fall back to default HTML-based fetching for listings
        return super.getMangaList(filters, page)
    }

    /**
     * Override getChapterList to use API-based fetching first (all chapters, fast parallel pagination)
     * Priority:
     * 1. API-based fetching (fetches every chapter, ~5s via parallel pages)
     * 2. WebView HTML (if Command.Chapter.Fetch is present)
     * 3. HTML-based fetching from novel page (single request, but only 30 chapters)
     */
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        // Priority 1: API - fetch ALL chapters
        try {
            val mangaId = extractMangaId(manga.key)
            if (!mangaId.isNullOrBlank()) {
                val apiChapters = fetchChaptersViaApi(mangaId)
                if (apiChapters.isNotEmpty()) {
                    return apiChapters
                }
            }
        } catch (e: Exception) {
            // fall through to HTML if the API path fails
        }

        // Priority 2: Check for WebView HTML
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return chaptersParse(chapterFetch.html.asJsoup()).reversed()
        }

        // Priority 3: HTML-based fetching (fast, but only first ~30 chapters)
        return try {
            super.getChapterList(manga, commands)
        } catch (e: Exception) {
            // Priority 3: Fall back to API if HTML fails
            emptyList()
        }
    }
}
