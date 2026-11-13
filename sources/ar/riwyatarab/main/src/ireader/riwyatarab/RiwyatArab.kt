package ireader.riwyatarab

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@Extension
@AutoSourceId(seed = "RiwyatArab")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "سيد",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://riwayatarab.com/novel/kingdom-s-bloodline",
    chapterUrl = "https://riwayatarab.com/novel/kingdom-s-bloodline/chapter/1",
    expectedTitle = "سلالة المملكة",
    expectedMinChapters = 100
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false
)
abstract class RiwyatArab(private val deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://riwayatarab.com"
    override val id: Long get() = RiwyatArabSourceId.ID
    override val name: String get() = "RiwyatArab"

    private val apiBase = "https://api.riwayatarab.com/api"
    private val pageSize = 24
    private val chapterPageSize = 100

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun requestBuilder(url: String, block: HeadersBuilder.() -> Unit): HttpRequestBuilder {
        return super.requestBuilder(url, block).apply {
            headers {
                append(HttpHeaders.Accept, "application/json, text/plain, */*")
                append(HttpHeaders.Origin, baseUrl)
                append(HttpHeaders.Referrer, "$baseUrl/")
                append(HttpHeaders.AcceptLanguage, "ar,en;q=0.8")
            }
        }
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    class LatestListing : Listing("آخر التحديثات")
    class PopularListing : Listing("الأكثر شعبية")
    class NewListing : Listing("الأحدث")

    override fun getListings(): List<Listing> = listOf(
        LatestListing(),
        PopularListing(),
        NewListing(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val sortKey = when (sort) {
            is PopularListing -> "views"
            is NewListing -> "created_at"
            else -> "updated_at"
        }
        return fetchNovels(page = page, sort = sortKey)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        return if (query.isNullOrBlank()) {
            fetchNovels(page = page, sort = "updated_at")
        } else {
            fetchNovels(page = page, sort = "updated_at", query = query)
        }
    }

    private suspend fun fetchNovels(
        page: Int,
        sort: String,
        query: String? = null,
    ): MangasPageInfo {
        return try {
            val url = buildString {
                append("$apiBase/novels?page=$page&limit=$pageSize&sort=$sort")
                if (!query.isNullOrBlank()) {
                    append("&search=${query.encodeURLParameter()}")
                }
            }
            val body = client.get(requestBuilder(url)).bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val novels = root["novels"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)

            val mangas = novels.mapNotNull { item ->
                val novel = item.jsonObject
                val title = novel.string("title") ?: return@mapNotNull null
                val slug = normalizeSlug(novel.string("slug")) ?: return@mapNotNull null
                MangaInfo(
                    key = novelUrl(slug),
                    title = title,
                    cover = novel.string("cover_image").orEmpty(),
                )
            }.distinctBy { it.key }

            val pagination = root["pagination"]?.jsonObject
            val hasNext = pagination?.boolean("hasNext")
                ?: pagination?.int("currentPage")?.let { current ->
                    val totalPages = pagination.int("totalPages") ?: 0
                    current < totalPages
                }
                ?: (mangas.size >= pageSize)

            MangasPageInfo(mangas, hasNext)
        } catch (e: Exception) {
            Log.error { "RiwyatArab: error fetching novels: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val slug = slugFromUrl(manga.key) ?: return manga
        return try {
            val body = client.get(requestBuilder("$apiBase/novels/$slug")).bodyAsText()
            val novel = json.parseToJsonElement(body).jsonObject

            val title = novel.string("title") ?: manga.title
            val cover = novel.string("cover_image") ?: manga.cover
            val author = novel.string("author").orEmpty()
            val description = novel.string("description").orEmpty()
            val tags = novel["tags"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val category = novel.string("category_name")
            val genres = buildList {
                if (!category.isNullOrBlank()) add(category)
                addAll(tags)
            }.distinct()

            manga.copy(
                title = title,
                cover = cover,
                author = if (author == "غير محدد") "" else author,
                description = description,
                genres = genres,
                status = statusFrom(novel.string("status")),
            )
        } catch (e: Exception) {
            Log.error { "RiwyatArab: error fetching details for $slug: ${e.message}" }
            manga
        }
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): List<ChapterInfo> {
        val slug = slugFromUrl(manga.key) ?: return emptyList()
        return try {
            val chapters = mutableListOf<ChapterInfo>()
            var page = 1
            var totalPages = 1

            while (page <= totalPages) {
                val body = client.get(
                    requestBuilder("$apiBase/novels/$slug/chapters?page=$page&limit=$chapterPageSize")
                ).bodyAsText()
                val root = json.parseToJsonElement(body).jsonObject
                val items = root["chapters"]?.jsonArray ?: break

                items.forEachIndexed { index, item ->
                    val chapter = item.jsonObject
                    val chapterSlug = chapter.string("slug") ?: return@forEachIndexed
                    val number = chapter.double("chapter_number")
                        ?.toFloat()
                        ?: ((page - 1) * chapterPageSize + index + 1).toFloat()
                    val title = chapter.string("title")
                        ?.takeIf { it.isNotBlank() }
                        ?: "الفصل ${number.toInt()}"
                    val date = chapter.string("created_at")
                        ?.let { DateParser.parse(it) }
                        ?: 0L

                    chapters.add(
                        ChapterInfo(
                            name = title,
                            key = "$baseUrl/novel/$slug/chapter/$chapterSlug",
                            number = number,
                            dateUpload = date,
                            type = ChapterInfo.NOVEL,
                        )
                    )
                }

                val pagination = root["pagination"]?.jsonObject
                totalPages = pagination?.int("totalPages") ?: page
                val hasNext = pagination?.boolean("hasNext") ?: (page < totalPages)
                if (!hasNext) break
                page++
            }

            chapters.distinctBy { it.key }.sortedBy { it.number }
        } catch (e: Exception) {
            Log.error { "RiwyatArab: error fetching chapters for $slug: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                val pages = parseWebsiteContent(Ksoup.parse(cmd.html))
                if (pages.isNotEmpty()) return pages
            }
        }

        val (slug, chapterSlug) = chapterParts(chapter.key)
        if (slug != null && chapterSlug != null) {
            try {
                val body = client.get(
                    requestBuilder("$apiBase/chapters/$slug/$chapterSlug")
                ).bodyAsText()
                val content = json.parseToJsonElement(body).jsonObject
                    .string("content")
                    .orEmpty()
                val pages = parseApiContent(content)
                if (pages.isNotEmpty()) return pages
            } catch (e: Exception) {
                Log.error { "RiwyatArab: error fetching content: ${e.message}" }
            }
        }

        return try {
            val doc = client.get(requestBuilder(chapter.key)).asJsoup()
            parseWebsiteContent(doc).ifEmpty {
                listOf(Text("محتوى الفصل غير متاح"))
            }
        } catch (e: Exception) {
            Log.error { "RiwyatArab: error fetching chapter page: ${e.message}" }
            listOf(Text("محتوى الفصل غير متاح"))
        }
    }

    private fun parseApiContent(rawContent: String): List<Page> {
        if (rawContent.isBlank()) return emptyList()

        val normalized = rawContent
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("&nbsp;", " ")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace('\u00A0', ' ')

        return normalized
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                line.contains("عالم الروايات") ||
                    line.contains("هذا التطبيق يسرق") ||
                    line.contains("فضاء الروايات") && line.contains("اقرأ")
            }
            .map { Text(it) }
    }

    private fun parseWebsiteContent(doc: Document): List<Page> {
        val root = doc.selectFirst(".chapter-content")
            ?: doc.selectFirst("[itemprop='text']")
            ?: return emptyList()

        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val text = current.toString().trim()
            if (text.isNotBlank()) paragraphs.add(text)
            current.clear()
        }

        fun walk(node: Node) {
            when (node) {
                is TextNode -> current.append(node.text())
                is Element -> {
                    val tag = node.tagName().lowercase()
                    val isBreak = tag in PAGE_BREAK_TAGS
                    if (isBreak) flush()
                    node.childNodes().forEach(::walk)
                    if (isBreak) flush()
                }
                else -> node.childNodes().forEach(::walk)
            }
        }

        root.childNodes().forEach(::walk)
        flush()

        return paragraphs
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private fun novelUrl(slug: String): String = "$baseUrl/novel/$slug"

    private fun normalizeSlug(slug: String?): String? {
        if (slug.isNullOrBlank()) return null
        return try {
            if (slug.contains('%')) {
                java.net.URLDecoder.decode(slug, "UTF-8")
            } else {
                slug
            }
        } catch (_: Exception) {
            slug
        }
    }

    private fun slugFromUrl(url: String): String? {
        val slug = url.substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .substringAfterLast('/')
        return normalizeSlug(slug)
    }

    private fun chapterParts(key: String): Pair<String?, String?> {
        val rest = key.substringAfter("/novel/", "")
        if (rest.isBlank() || !rest.contains("/chapter/")) return null to null
        val novelSlug = rest.substringBefore("/chapter/")
        val chapterSlug = rest.substringAfter("/chapter/").substringBefore('?').substringBefore('#')
        return normalizeSlug(novelSlug) to normalizeSlug(chapterSlug)
    }

    private fun statusFrom(status: String?): Long = when (status?.trim()?.lowercase()) {
        "ongoing" -> MangaInfo.ONGOING
        "completed" -> MangaInfo.COMPLETED
        else -> MangaInfo.UNKNOWN
    }

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun kotlinx.serialization.json.JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull

    private fun kotlinx.serialization.json.JsonObject.double(name: String): Double? =
        this[name]?.jsonPrimitive?.doubleOrNull

    private fun kotlinx.serialization.json.JsonObject.boolean(name: String): Boolean? =
        this[name]?.jsonPrimitive?.booleanOrNull
            ?: this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private companion object {
        val PAGE_BREAK_TAGS = setOf("p", "div", "h1", "h2", "h3", "h4", "li", "blockquote", "br")
    }
}
