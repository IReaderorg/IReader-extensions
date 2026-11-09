package ireader.novltime

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
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
import kotlinx.serialization.json.booleanOrNull
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
 * NovelTime (نوفل تايم) — Arabic web-novel site built on Next.js.
 *
 * The public site ships a JSON API that the web reader itself uses; it needs no login and
 * is not behind Cloudflare:
 *
 *  - `GET /api/v1/novels?limit=100&sort=latest|popular|rating[&genre=<key>]`
 *  - `GET /api/v1/novels/<slug>`                                  → details
 *  - `GET /api/v1/novels/<novelId>/chapters/published?limit=200&sort=asc[&cursor=…]`
 *  - `GET /api/v1/chapters/<chapterId>`                           → chapter body
 *  - `GET /api/v1/search?q=<query>&limit=20[&cursor=…]`
 *
 * Pagination is cursor based: the `page` parameter is rejected with HTTP 400 for values
 * above 1, so every listing keeps the `nextCursor` returned by the previous page and
 * replays it when the app asks for the following page.
 */
@Extension
@AutoSourceId(seed = "NovelTime")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "الظل",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://novltime.com/ar/novel/reverend-insanity",
    chapterUrl = "https://novltime.com/ar/novel/reverend-insanity/chapter/chapter-1?chapterId=6ac75814c24455d171b82029",
    expectedTitle = "القس المجنون",
    expectedAuthor = "طارق منصور"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false
)
abstract class NovelTime(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://novltime.com"
    override val id: Long get() = NovelTimeSourceId.ID
    override val name: String get() = "NovelTime"

    private val api: String get() = "$baseUrl/api/v1"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** novel slug → numeric/mongo id, so the chapter list needs no extra detail call. */
    private val novelIdCache = HashMap<String, String>(8)

    /** "<listing-key>|<page>" → cursor that fetches the following page. */
    private val cursorCache = HashMap<String, String>(16)

    /** slug → author name scraped once from the detail page (the JSON API omits it). */
    private val authorCache = HashMap<String, String>(8)

    // ── listings & filters ──────────────────────────────────────────────────────

    class LatestListing : Listing("الأحدث")
    class PopularListing : Listing("الأكثر شعبية")
    class RatingListing : Listing("الأعلى تقييمًا")

    override fun getListings(): List<Listing> = listOf(
        LatestListing(),
        PopularListing(),
        RatingListing(),
    )

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("الترتيب", arrayOf("الأحدث", "الأكثر شعبية", "الأعلى تقييمًا")),
        Filter.Select("التصنيف", GENRE_LABELS),
    )

    // Everything is served by the JSON API, so no hidden WebView pass is needed.
    override fun getCommands(): CommandList = emptyList()

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val sortKey = when (sort) {
            is PopularListing -> "popular"
            is RatingListing -> "rating"
            else -> "latest"
        }
        return fetchNovelListing(sortKey, null, page)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim().orEmpty()
        if (query.isNotEmpty()) return search(query, page)

        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val sortKey = when (sortIndex) {
            1 -> "popular"
            2 -> "rating"
            else -> "latest"
        }
        val genreIndex = filters.findInstance<Filter.Select>()?.value ?: 0
        val genre = GENRE_KEYS.getOrNull(genreIndex - 1)
        return fetchNovelListing(sortKey, genre, page)
    }

    private suspend fun fetchNovelListing(
        sort: String,
        genre: String?,
        page: Int,
    ): MangasPageInfo {
        val cacheKey = "list|$sort|${genre.orEmpty()}"
        val cursor = cursorFor(cacheKey, page)
        if (page > 1 && cursor == null) return MangasPageInfo(emptyList(), false)

        val url = buildString {
            append("$api/novels?limit=$PAGE_SIZE&sort=$sort")
            if (genre != null) append("&genre=${genre.encodeURLParameter()}")
            if (cursor != null) append("&cursor=${cursor.encodeURLParameter()}")
        }
        return try {
            val root = fetchJson(url)
            val meta = root["meta"]?.jsonObject
            val hasMore = meta?.get("hasMore")?.jsonPrimitive?.booleanOrNull ?: false
            storeCursor(cacheKey, page, meta?.get("nextCursor")?.jsonPrimitive?.contentOrNull, hasMore)
            val mangas = root["data"]?.jsonArray
                ?.mapNotNull { element -> element.jsonObjectOrNull()?.let(::toMangaInfo) }
                ?: emptyList()
            MangasPageInfo(mangas.distinctBy { it.key }, hasMore)
        } catch (e: Exception) {
            Log.error { "NovelTime listing error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private suspend fun search(query: String, page: Int): MangasPageInfo {
        val cacheKey = "search|${query.lowercase()}"
        val cursor = cursorFor(cacheKey, page)
        if (page > 1 && cursor == null) return MangasPageInfo(emptyList(), false)

        val url = buildString {
            append("$api/search?q=${query.encodeURLParameter()}&limit=$PAGE_SIZE")
            if (cursor != null) append("&cursor=${cursor.encodeURLParameter()}")
        }
        return try {
            val root = fetchJson(url)
            val data = root["data"]?.jsonObject
            val hasMore = data?.get("hasMore")?.jsonPrimitive?.booleanOrNull ?: false
            storeCursor(cacheKey, page, data?.get("nextCursor")?.jsonPrimitive?.contentOrNull, hasMore)
            val mangas = data?.get("items")?.jsonArray
                ?.mapNotNull { element -> element.jsonObjectOrNull()?.let(::toMangaInfo) }
                ?: emptyList()
            MangasPageInfo(mangas.distinctBy { it.key }, hasMore)
        } catch (e: Exception) {
            Log.error { "NovelTime search error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private fun toMangaInfo(obj: kotlinx.serialization.json.JsonObject): MangaInfo? {
        val slug = obj["slug"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: slug
        val coverUrl = obj["coverURL"]?.jsonPrimitive?.contentOrNull
            ?: obj["coverUrl"]?.jsonPrimitive?.contentOrNull
        val genres = obj["genres"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        val status = parseStatus(obj["completionStatus"]?.jsonPrimitive?.contentOrNull)

        val key = buildString {
            append("$baseUrl/ar/novel/$slug")
            if (!id.isNullOrBlank()) append("?novelId=$id")
        }
        return MangaInfo(
            key = key,
            title = title,
            cover = absoluteCover(coverUrl),
            genres = genres,
            status = status,
        )
    }

    // ── details ─────────────────────────────────────────────────────────────────

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val slug = novelSlugOf(manga.key) ?: return manga
        return try {
            val root = fetchJson("$api/novels/${slug.encodeURLParameter()}")
            val data = root["data"]?.jsonObject ?: return manga

            data["id"]?.jsonPrimitive?.contentOrNull?.let { novelIdCache[slug] = it }

            val title = data["title"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: manga.title
            val description = data["descriptionLong"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: data["descriptionShort"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val alternative = data["alternativeTitles"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.joinToString(" / ")
                .orEmpty()
            val genres = data["genres"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            val cover = absoluteCover(
                data["coverURL"]?.jsonPrimitive?.contentOrNull
                    ?: data["coverUrl"]?.jsonPrimitive?.contentOrNull
            ).ifBlank { manga.cover }
            val status = parseStatus(data["completionStatus"]?.jsonPrimitive?.contentOrNull)
            val chapterCount = data["stats"]?.jsonObject
                ?.get("chapterCount")?.jsonPrimitive?.intOrNull ?: 0

            val author = authorOf(slug)

            val fullDescription = buildString {
                if (description.isNotBlank()) append(description.trim())
                if (alternative.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("الأسماء البديلة: ").append(alternative)
                }
                if (chapterCount > 0) {
                    if (isNotEmpty()) append("\n")
                    append("عدد الفصول: ").append(chapterCount)
                }
            }

            manga.copy(
                title = title,
                author = author.ifBlank { manga.author },
                description = fullDescription,
                genres = genres.ifEmpty { manga.genres },
                status = if (status == MangaInfo.UNKNOWN) manga.status else status,
                cover = cover,
            )
        } catch (e: Exception) {
            Log.error { "NovelTime details error: ${e.message}" }
            manga
        }
    }

    /**
     * The JSON API exposes only `authorId`, so the author name is read once from the
     * rendered detail page (`<a href="/ar/u/…">`). Failure is harmless.
     */
    private suspend fun authorOf(slug: String): String {
        authorCache[slug]?.let { return it }
        return try {
            val html = client.get(requestBuilder("$baseUrl/ar/novel/$slug")).bodyAsText()
            val doc = Ksoup.parse(html)
            val name = doc.select("a[href*='/ar/u/']").firstOrNull()?.text()?.trim().orEmpty()
            if (name.isNotBlank()) authorCache[slug] = name
            name
        } catch (e: Exception) {
            Log.error { "NovelTime author error: ${e.message}" }
            ""
        }
    }

    // ── chapters ────────────────────────────────────────────────────────────────

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): List<ChapterInfo> {
        val slug = novelSlugOf(manga.key) ?: return emptyList()
        val novelId = novelIdOf(manga.key, slug) ?: return emptyList()

        val chapters = mutableListOf<ChapterInfo>()
        var cursor: String? = null
        var guard = 0
        while (guard++ < MAX_CHAPTER_REQUESTS) {
            val url = buildString {
                append("$api/novels/$novelId/chapters/published?limit=$CHAPTER_PAGE_SIZE&sort=asc")
                if (cursor != null) append("&cursor=${cursor.encodeURLParameter()}")
            }
            val root = try {
                fetchJson(url)
            } catch (e: Exception) {
                Log.error { "NovelTime chapters error: ${e.message}" }
                break
            }
            val items = root["data"]?.jsonArray ?: break
            items.forEach { element ->
                val obj = element.jsonObjectOrNull() ?: return@forEach
                val chapterId = obj["id"]?.jsonPrimitive?.contentOrNull
                val chapterSlug = obj["slug"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: return@forEach
                val order = obj["order"]?.jsonPrimitive?.intOrNull ?: -1
                val title = obj["title"]?.jsonPrimitive?.contentOrNull
                    ?.trim()?.takeIf { it.isNotBlank() }
                    ?: if (order >= 0) "الفصل $order" else chapterSlug
                val key = buildString {
                    append("$baseUrl/ar/novel/$slug/chapter/$chapterSlug")
                    if (!chapterId.isNullOrBlank()) append("?chapterId=$chapterId")
                }
                chapters.add(
                    ChapterInfo(
                        key = key,
                        name = title,
                        number = order.toFloat(),
                        type = ChapterInfo.NOVEL,
                    )
                )
            }

            val meta = root["meta"]?.jsonObject
            val hasMore = meta?.get("hasMore")?.jsonPrimitive?.booleanOrNull ?: false
            cursor = meta?.get("nextCursor")?.jsonPrimitive?.contentOrNull
            if (!hasMore || cursor.isNullOrBlank()) break
        }
        return chapters.distinctBy { it.key }
    }

    private suspend fun novelIdOf(key: String, slug: String): String? {
        queryParamOf(key, "novelId")?.takeIf { it.isNotBlank() }?.let { return it }
        novelIdCache[slug]?.let { return it }
        return try {
            val root = fetchJson("$api/novels/${slug.encodeURLParameter()}")
            root["data"]?.jsonObject
                ?.get("id")?.jsonPrimitive?.contentOrNull
                ?.also { novelIdCache[slug] = it }
        } catch (e: Exception) {
            Log.error { "NovelTime novel id error: ${e.message}" }
            null
        }
    }

    // ── content ─────────────────────────────────────────────────────────────────

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val chapterId = queryParamOf(chapter.key, "chapterId")

        if (!chapterId.isNullOrBlank()) {
            try {
                val root = fetchJson("$api/chapters/${chapterId.encodeURLParameter()}")
                val data = root["data"]?.jsonObject
                if (data != null) {
                    val paragraphs = data["contentJson"]?.jsonArray
                        ?.mapNotNull { it.jsonObjectOrNull()?.get("text")?.jsonPrimitive?.contentOrNull }
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                    if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }

                    val content = data["content"]?.jsonPrimitive?.contentOrNull
                    if (!content.isNullOrBlank()) {
                        val lines = content.split('\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        if (lines.isNotEmpty()) return lines.map { Text(it) }
                    }
                }
            } catch (e: Exception) {
                Log.error { "NovelTime content error: ${e.message}" }
            }
        }

        // Fallback: the chapter page is server rendered and keeps the body in `.chapter-body`.
        return try {
            val html = client.get(requestBuilder(chapterUrl(chapter.key))).bodyAsText()
            val doc = Ksoup.parse(html)
            val container = doc.selectFirst(".chapter-body")
                ?: doc.selectFirst("article")
                ?: doc.selectFirst("main")
            val paragraphs = container?.select("p")
                ?.map { it.text().trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            if (paragraphs.isNotEmpty()) {
                paragraphs.map { Text(it) }
            } else {
                listOf(Text("تعذر تحميل محتوى الفصل"))
            }
        } catch (e: Exception) {
            Log.error { "NovelTime content fallback error: ${e.message}" }
            listOf(Text("تعذر تحميل محتوى الفصل"))
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private suspend fun fetchJson(url: String): kotlinx.serialization.json.JsonObject {
        val body = client.get(requestBuilder(url)).bodyAsText()
        return json.parseToJsonElement(body).jsonObject
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): kotlinx.serialization.json.JsonObject? =
        try {
            jsonObject
        } catch (e: Exception) {
            null
        }

    private fun cursorFor(key: String, page: Int): String? =
        if (page <= 1) null else cursorCache["$key|${page - 1}"]

    private fun storeCursor(key: String, page: Int, cursor: String?, hasMore: Boolean) {
        if (hasMore && !cursor.isNullOrBlank()) {
            cursorCache["$key|$page"] = cursor
        } else {
            cursorCache.remove("$key|$page")
        }
    }

    /** `https://novltime.com/ar/novel/<slug>[?novelId=…][/chapter/…]` → `<slug>` */
    private fun novelSlugOf(key: String): String? {
        val path = key.substringBefore('?')
        val marker = "/ar/novel/"
        val index = path.indexOf(marker)
        if (index < 0) return null
        return path.substring(index + marker.length)
            .substringBefore('/')
            .takeIf { it.isNotBlank() }
    }

    private fun chapterUrl(key: String): String = key.substringBefore('?')

    private fun queryParamOf(key: String, name: String): String? {
        val query = key.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }

    private fun absoluteCover(url: String?): String {
        val value = url?.trim().orEmpty()
        if (value.isEmpty()) return ""
        return when {
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> baseUrl + value
            else -> "$baseUrl/$value"
        }
    }

    private fun parseStatus(status: String?): Long = when (status?.trim()?.uppercase()) {
        "ONGOING" -> MangaInfo.ONGOING
        "COMPLETED" -> MangaInfo.COMPLETED
        "HIATUS", "ON_HIATUS" -> MangaInfo.ON_HIATUS
        "CANCELLED", "CANCELED" -> MangaInfo.CANCELLED
        else -> MangaInfo.UNKNOWN
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val CHAPTER_PAGE_SIZE = 200

        /** Safety net: 40 × 200 = 8000 chapters — far above the longest novel on the site. */
        const val MAX_CHAPTER_REQUESTS = 40

        val GENRE_KEYS = arrayOf(
            "action", "adventure", "comedy", "drama", "fantasy", "historical",
            "horror", "litrpg", "mystery", "scifi", "sliceoflife", "system",
            "thriller", "urban", "wuxia", "xianxia",
        )

        val GENRE_LABELS = arrayOf(
            "الكل", "أكشن", "مغامرة", "كوميديا", "دراما", "فانتازيا", "تاريخي",
            "رعب", "ليت-آر بي جي", "غموض", "خيال علمي", "شرائح الحياة", "نظام",
            "إثارة", "حضرية", "ووكسيا", "شيانكسيا",
        )
    }
}
