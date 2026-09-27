package ireader.mtlarabic

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import ireader.core.log.Log
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

/**
 * مكتبة الخيال (mtlarabic.com) — Arabic-translated Chinese web novels.
 *
 * The site is a purpose-built app (no WordPress, no public REST for content),
 * but it does expose a small JSON API that its own front-end uses. Everything
 * this source needs is read from those endpoints or from the SSR payloads the
 * server embeds in the page, so no HTML scraping heuristics are involved:
 *
 *   GET /api/search?q={query}                      → [{id,name,originalName,type,image}]
 *   GET /api/novels/{id}/chapters?page=N&limit=500 → {"chapters":[…],"pagination":{…}}
 *
 * The detail page embeds the novel record as `<script id="__NOVEL__">` and the
 * chapter page embeds the chapter text as `<script id="__CHAPTER__">`. Both are
 * server-rendered JSON, which is why the selectors below never need a WebView.
 */
@Extension
@AutoSourceId(seed = "MtlArabic")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "ملك",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://mtlarabic.com/ملك-الحكام",
    chapterUrl = "https://mtlarabic.com/ملك-الحكام/1",
    expectedTitle = "ملك الحكام",
    expectedAuthor = "",
    expectedMinChapters = 100
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class MtlArabic(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://mtlarabic.com"
    override val id: Long get() = MtlArabicSourceId.ID
    override val name: String get() = "MtlArabic"

    /**
     * The homepage splits the library into exactly three sections, and these tabs
     * reproduce them one-for-one:
     *
     *   - FamousNovels    → #famous-novels    "أشهر روايات خيال المعجبين"
     *   - CompletedNovels → #completed-novels "الروايات المكتملة"
     *   - LatestStories   → #latest-novels    "أحدث القصص"
     *
     * Each section is the whole catalogue re-ordered, not a fixed 12-card strip, so
     * every tab has the full library to page through and "load more" keeps working
     * long past the homepage's first page.
     */
    class FamousNovelsListing : Listing("روايات المعجبين")
    class CompletedNovelsListing : Listing("الروايات المكتملة")
    class LatestStoriesListing : Listing("أحدث القصص")

    override fun getListings(): List<Listing> = listOf(
        FamousNovelsListing(),
        CompletedNovelsListing(),
        LatestStoriesListing(),
    )

    /** The site is fronted by Cloudflare but serves plain HTML/JSON to browser UAs. */
    override val client get() = deps.httpClients.cloudflareClient

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Site status string for a finished novel. */
    private val COMPLETED = "مكتملة"

    private val PAGE_SIZE = 24

    /** One record of GET /api/novels — the catalogue the three listings are cut from. */
    private data class CatalogueItem(
        val id: Int,
        val slug: String,
        val name: String,
        val image: String,
        val type: String,
        val status: String,
        val views: Int,
        val lastUpdate: String,
    )

    private fun Listing?.isCompleted() = this is CompletedNovelsListing

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
    )

    // ── listings ────────────────────────────────────────────────────────────

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            // The homepage shows 12 cards per section, but the underlying orderings
            // are properties of the catalogue, so all three tabs are cut from
            // /api/novels and paged client-side. Each tab therefore exposes the whole
            // library rather than the homepage's first 12.
            val ordered = when {
                sort.isCompleted() ->
                    fetchCatalogue()
                        .filter { it.status == COMPLETED }
                        .sortedByDescending { it.lastUpdate }
                sort is FamousNovelsListing ->
                    fetchCatalogue().sortedByDescending { it.views }
                else ->
                    fetchCatalogue().sortedByDescending { it.lastUpdate }
            }
            val from = (page - 1) * PAGE_SIZE
            MangasPageInfo(
                ordered.drop(from).take(PAGE_SIZE).map { it.toMangaInfo() },
                from + PAGE_SIZE < ordered.size,
            )
        } catch (e: Exception) {
            Log.error { "MtlArabic: list failed: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    /** Cached per process: the catalogue is a single ~200 KB payload and never changes mid-session. */
    private var catalogue: List<CatalogueItem>? = null

    private suspend fun fetchCatalogue(): List<CatalogueItem> {
        catalogue?.let { return it }
        val items = json.parseToJsonElement(apiGet("/api/novels")).jsonArray.mapNotNull { el ->
            val o = el.jsonObjectOrNull() ?: return@mapNotNull null
            val slug = o.str("slug") ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            CatalogueItem(
                id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                slug = slug,
                name = name,
                image = o.str("image").orEmpty(),
                type = o.str("type").orEmpty(),
                status = o.str("status").orEmpty(),
                views = o["views"]?.jsonPrimitive?.intOrNull ?: 0,
                lastUpdate = o.str("lastUpdate").orEmpty(),
            )
        }
        Log.info { "MtlArabic: catalogue has ${items.size} novels" }
        catalogue = items
        return items
    }

    /**
     * Covers are served through a responsive <picture>: the <img src> is a `?w=120`
     * thumbnail while the widest entry of the <source srcset> is the real image.
     * The bare file (no `?w=`) is what the app should load, so a card in the main
     * interface shows the full-size cover rather than an upscaled thumbnail.
     */
    private fun CatalogueItem.coverUrl(): String {
        if (image.isBlank()) return ""
        // The catalogue stores a bare filename; the card's widest srcset entry is the
        // same file with a `?w=240` thumbnail parameter that the server would ignore.
        return "$baseUrl/images/novels/$image"
    }

    private fun CatalogueItem.toMangaInfo() = MangaInfo(
        key = "$baseUrl/$slug",
        title = name,
        cover = coverUrl(),
        genres = if (type.isBlank()) emptyList() else listOf(type),
    )

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim().orEmpty()
        if (query.isBlank()) return getMangaList(null, page)
        return search(query, page)
    }

    private suspend fun search(query: String, page: Int): MangasPageInfo {
        return try {
            val body = apiGet("/api/search?q=${encode(query)}")
            val hits = json.parseToJsonElement(body).jsonArray.mapNotNull { it.jsonObjectOrNull() }

            // /api/search returns ids but no slug, and the detail route is
            // slug-only (/novel/{id} is a 404). The catalogue carries slug + id for
            // every novel, so hits are resolved through it and keep their full cover
            // and genre instead of the thinner search payload.
            val byId = fetchCatalogue().associateBy { it.id }
            val results = hits.mapNotNull { o ->
                val id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                byId[id]?.toMangaInfo() ?: return@mapNotNull null
            }.distinctBy { it.key }

            // Search is not paginated server-side; cut the page client-side so
            // "load more" behaves predictably.
            val from = (page - 1) * PAGE_SIZE
            MangasPageInfo(results.drop(from).take(PAGE_SIZE), from + PAGE_SIZE < results.size)
        } catch (e: Exception) {
            Log.error { "MtlArabic: search failed: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    // ── details ─────────────────────────────────────────────────────────────

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetails(cmd.html, manga)
        }

        return try {
            parseDetails(client.get(requestBuilder(manga.key)).bodyAsText(), manga)
        } catch (e: Exception) {
            Log.error { "MtlArabic: details failed for ${manga.key}: ${e.message}" }
            manga
        }
    }

    private fun parseDetails(html: String, manga: MangaInfo): MangaInfo {
        val doc = Ksoup.parse(html)
        val novel = readInjected(html, "__NOVEL__")

        val title = novel?.str("name")
            ?: doc.selectFirst(".novel-details-title")?.text()?.trim()
            ?: manga.title

        val coverFile = novel?.str("image")
        val cover = when {
            !coverFile.isNullOrBlank() -> "$baseUrl/images/novels/$coverFile"
            else -> doc.selectFirst(".novel-details-image img")?.attr("src") ?: manga.cover
        }

        val description = novel?.str("description")
            ?: doc.selectFirst(".novel-details-description")?.text()?.trim()
            ?: ""

        val status = novel?.str("status").orEmpty()

        // Genres are rendered as .tag-genre next to .tag-status-<ongoing|…>.
        val genres = doc.select(".tag-genre").map { it.text().trim() }.filter { it.isNotBlank() }

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = "",
            genres = genres,
            status = parseArabicStatus(status),
        )
    }

    /**
     * MangaInfo.parseStatus only matches English, and the site reports status in
     * Arabic ("مستمرة", "مكتملة", …) in both the SSR payload and the API.
     */
    private fun parseArabicStatus(raw: String?): Long {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return MangaInfo.UNKNOWN
        return when {
            text.contains("مستمر") || text.contains("متجد") -> MangaInfo.ONGOING
            text.contains("مكتمل") || text.contains("منتهي") -> MangaInfo.COMPLETED
            text.contains("متوقف") || text.contains("معلّق") || text.contains("معلق") -> MangaInfo.ON_HIATUS
            text.contains("ملغى") || text.contains("ملغي") -> MangaInfo.CANCELLED
            else -> MangaInfo.parseStatus(text)
        }
    }

    // ── chapters ────────────────────────────────────────────────────────────

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        // The novel id lives in the SSR payload; the chapter endpoint needs it.
        val html = try {
            client.get(requestBuilder(manga.key)).bodyAsText()
        } catch (e: Exception) {
            Log.error { "MtlArabic: chapter-list page failed: ${e.message}" }
            return emptyList()
        }

        val novel = readInjected(html, "__NOVEL__")
        val novelId = novel?.get("id")?.jsonPrimitive?.intOrNull
        val slug = novel?.str("slug") ?: manga.key.trimEnd('/').substringAfterLast('/')
        if (novelId == null) {
            Log.error { "MtlArabic: no novel id in payload for ${manga.key}" }
            return emptyList()
        }

        return fetchAllChapters(novelId, slug)
    }

    private suspend fun fetchAllChapters(novelId: Int, slug: String): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        // The endpoint caps `limit` at 500 and reports totalPages in the payload,
        // so 3 requests covers the largest novel found (1460 chapters).
        val limit = 500
        var page = 1
        var totalPages = 1
        var totalDeclared = 0
        do {
            val body = apiGet("/api/novels/$novelId/chapters?page=$page&limit=$limit")
            val obj = json.parseToJsonElement(body).jsonObject
            val arr = obj["chapters"]?.jsonArray ?: JsonArray(emptyList())
            val paging = obj["pagination"]?.jsonObject
            // The site reports the authoritative total here; prefer it over our own
            // count so the app's "N chapters" header matches the site's own number.
            totalDeclared = paging?.get("totalChapters")?.jsonPrimitive?.intOrNull ?: totalDeclared

            arr.forEach { element ->
                val o = element.jsonObject
                val number = o["number"]?.jsonPrimitive?.intOrNull ?: return@forEach
                val title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val key = "$baseUrl/$slug/$number"
                chapters += ChapterInfo(
                    name = if (title.isBlank()) "الفصل $number" else "الفصل $number: $title",
                    key = key,
                    number = number.toFloat(),
                )
            }

            totalPages = paging?.get("totalPages")?.jsonPrimitive?.intOrNull ?: 1
            page++
        } while (page <= totalPages)

        Log.info { "MtlArabic: $totalPages page(s) of chapters, ${chapters.size} total for novel $novelId" }
        return chapters.sortedBy { it.number }
    }

    // ── content ─────────────────────────────────────────────────────────────

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".reader-chapter-title",
            pageContentSelector = "#chapterText p, .chapter-text-content p",
        )

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseContent(cmd.html)
        }

        return try {
            parseContent(client.get(requestBuilder(chapter.key)).bodyAsText())
        } catch (e: Exception) {
            Log.error { "MtlArabic: content failed for ${chapter.key}: ${e.message}" }
            emptyList()
        }
    }

    private fun parseContent(html: String): List<Page> {
        val doc = Ksoup.parse(html)

        val title = doc.selectFirst(".reader-chapter-title")?.text()?.trim().orEmpty()

        // .chapter-paragraph is the site's own wrapper; #chapterText is the
        // container id. Accept both so a markup change in either still works.
        val paragraphs = doc.select("#chapterText p, .chapter-text-content p, .chapter-paragraph")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) {
            Log.error { "MtlArabic: no paragraphs found for chapter page" }
            return emptyList()
        }

        val pages = mutableListOf<Page>()
        if (title.isNotBlank()) pages += Text(title)
        paragraphs.forEach { pages += Text(it) }
        return pages
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private suspend fun apiGet(path: String): String =
        client.get(requestBuilder("$baseUrl$path") {
            // The front-end tags its own XHR with this header; the API answers 401
            // to requests that don't carry it. The default User-Agent and cache
            // headers are re-applied because a custom block replaces the default.
            append(HttpHeaders.UserAgent, getUserAgent())
            append(HttpHeaders.CacheControl, "max-age=0")
            append("X-App-Request", "true")
        }).bodyAsText()

    /**
     * Reads a `<script id="...">` JSON payload the server embeds in the page.
     * Falls back to Ksoup so a whitespace/attribute-order change doesn't break it.
     */
    private fun readInjected(html: String, id: String): JsonObject? {
        val direct = Regex("<script[^>]*id=\"$id\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
        if (!direct.isNullOrBlank()) {
            runCatching { return json.parseToJsonElement(direct).jsonObject }
        }
        return runCatching {
            Ksoup.parse(html).selectFirst("script#$id")?.html()
                ?.let { json.parseToJsonElement(it).jsonObject }
        }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    private fun encode(value: String): String =
        value.trim().let { v ->
            v.split(" ", " ")
                .filter { it.isNotBlank() }
                .joinToString("+") { it }
                .let { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        }
}
