package ireader.linovels

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.ImageUrl
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

/**
 * Linovels — مملكة الروايات (linovels.com), Arabic novels.
 *
 * The site runs on Blogger, so the whole source is powered by the public
 * Blogger JSON feeds (no HTML scraping needed for lists/chapters):
 *
 *  - Series list : /feeds/posts/default/-/Series?alt=json&max-results=20&start-index=N
 *  - Search      : /feeds/posts/default?q=QUERY&alt=json
 *  - Chapters    : /feeds/posts/default/-/{seriesLabel}?alt=json&max-results=150&start-index=N
 *  - Chapter body: <textarea id="nk-raw-data"> with one-level HTML entities
 *
 * Series URL : /{slug}/
 * Chapter URL: /{slug}-chapter-{n}/
 */
@Extension
@AutoSourceId(seed = "Linovels")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "رواية",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://linovels.com/omniscient-readers-viewpoint/",
    chapterUrl = "https://linovels.com/omniscient-readers-viewpoint-chapter-544/",
    expectedTitle = "Omniscient Reader’s Viewpoint",
    expectedAuthor = "korazon"
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class Linovels(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://linovels.com"
    override val id: Long get() = LinovelsSourceId.ID
    override val name: String get() = "Linovels"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val pageSize = 20

    /** Labels that are never the series' own label. */
    private val genericLabels = setOf(
        "series", "chapter", "novel", "web novel", "webnovel",
        "ongoing", "completed", "hiatus", "new", "hot",
        "جديدة", "مكتملة", "مستمرة", "رائجة", "جديدة مكتملة",
        "action", "adventure", "comedy", "drama", "fantasy", "horror",
        "mystery", "romance", "sci-fi", "slice of life", "supernatural",
        "tragedy", "historical", "martial arts", "xianxia", "wuxia",
        "reincarnation", "magic", "apocalypse", "harem", "school life",
        "china", "korea", "japan", "japanese", "chinese", "korean",
        "أكشن", "مغامرة", "كوميديا", "دراما", "فانتازيا", "خيال",
        "رعب", "غموض", "رومانسي", "خيال علمي", "حريم", "مدرسة"
    )

    override fun getFilters(): FilterList = listOf(
        Filter.Title()
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "أحدث الروايات",
                endpoint = "/search/label/Series/",
                selector = "a.zm-cc",
                nameSelector = "span.zm-cc-t",
                coverSelector = "img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "مكتملة",
                endpoint = "/search/label/Completed/",
                selector = "a.zm-cc",
                nameSelector = "span.zm-cc-t",
                coverSelector = "img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "الأكثر رواجاً",
                endpoint = "/search/label/hot/",
                selector = "a.zm-cc",
                nameSelector = "span.zm-cc-t",
                coverSelector = "img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true
            )
        )

    // ──────────────────────────────────────────────────────────────
    // LIST / SEARCH (Blogger JSON feed)
    // ──────────────────────────────────────────────────────────────

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.takeIf { it.isNotBlank() }
        val start = (page - 1) * pageSize + 1

        val url = if (!query.isNullOrBlank()) {
            "$baseUrl/feeds/posts/default?alt=json&max-results=$pageSize&start-index=$start" +
                "&q=${encode(query)}"
        } else {
            "$baseUrl/feeds/posts/default/-/Series?alt=json&max-results=$pageSize&start-index=$start"
        }

        return parseFeed(url, requireSeriesCategory = !query.isNullOrBlank(), page = page)
    }

    private suspend fun parseFeed(
        url: String,
        requireSeriesCategory: Boolean,
        page: Int
    ): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder(url))
            if (response.status.value >= 400) {
                Log.error { "Linovels feed error ${response.status.value}: $url" }
                return MangasPageInfo(emptyList(), false)
            }

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val feed = root["feed"]?.jsonObject ?: return MangasPageInfo(emptyList(), false)
            val total = feed["openSearch\$totalResults"]?.jsonObject
                ?.get("\$t")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

            val entries = entriesOf(feed)
            val items = mutableListOf<MangaInfo>()

            entries.forEach { entry ->
                val categories = categoriesOf(entry)
                if (requireSeriesCategory && categories.none { it.equals("Series", true) }) {
                    return@forEach
                }

                val title = entry["title"]?.jsonObject
                    ?.get("\$t")?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@forEach
                val link = alternateLink(entry) ?: return@forEach

                val cover = entry["media\$thumbnail"]?.jsonObject
                    ?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                val summary = entry["summary"]?.jsonObject
                    ?.get("\$t")?.jsonPrimitive?.contentOrNull.orEmpty()

                items.add(
                    MangaInfo(
                        key = link,
                        title = title,
                        cover = cover,
                        description = summary
                    )
                )
            }

            val consumed = (page - 1) * pageSize + items.size
            MangasPageInfo(mangas = items, hasNextPage = total > consumed)
        } catch (e: Exception) {
            Log.error { "Linovels list error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // DETAIL
    // ──────────────────────────────────────────────────────────────

    override suspend fun getMangaDetails(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): MangaInfo {
        return try {
            val html = client.get(requestBuilder(manga.key)).bodyAsText()
            val doc: Document = Ksoup.parse(html)

            val title = doc.selectFirst("h1")?.text()?.trim()
                ?.ifBlank { null }
                ?: manga.title

            val cover = ogImage(doc)
                ?.ifBlank { null }
                ?: manga.cover

            val description = longParagraph(doc)
                ?.ifBlank { null }
                ?: manga.description

            val labels = labelsOf(doc)
            val status = if (labels.any {
                    it.equals("completed", true) || it.equals("مكتملة", true)
                }
            ) COMPLETED else ONGOING

            // Author + summary are more reliable from the post feed.
            val feedEntry = findEntryByLink(
                "$baseUrl/feeds/posts/default?alt=json&max-results=10&q=${encode(title)}",
                manga.key
            )
            val author = feedEntry?.get("author")?.let { authorsOf(it) }?.firstOrNull()
                ?: doc.select("a[href*='/search/label/']").firstOrNull { it.text().trim().isNotBlank() }
                    ?.text()?.trim()
                ?: ""

            val genres = labels.filter { it.isNotBlank() && !isGenericLabel(it) }

            manga.copy(
                title = title,
                cover = cover,
                description = description ?: "",
                author = author,
                genres = if (genres.isNotEmpty()) genres else manga.genres,
                status = status
            )
        } catch (e: Exception) {
            Log.error { "Linovels detail error: ${e.message}" }
            manga
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CHAPTERS (feed by series label)
    // ──────────────────────────────────────────────────────────────

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return try {
            val html = client.get(requestBuilder(manga.key)).bodyAsText()
            val doc: Document = Ksoup.parse(html)

            val label = seriesLabel(doc) ?: manga.title
            val encoded = encode(label).replace("+", "%20")

            val chapters = mutableListOf<ChapterInfo>()
            var startIndex = 1
            var total = Int.MAX_VALUE
            var guard = 0

            while (startIndex <= total && guard < 40) {
                val url = "$baseUrl/feeds/posts/default/-/$encoded" +
                    "?alt=json&max-results=150&start-index=$startIndex"
                val response = client.get(requestBuilder(url))
                if (response.status.value >= 400) break

                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val feed = root["feed"]?.jsonObject ?: break
                total = feed["openSearch\$totalResults"]?.jsonObject
                    ?.get("\$t")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

                val entries = entriesOf(feed)
                if (entries.isEmpty()) break

                entries.forEach { entry ->
                    val entryTitle = entry["title"]?.jsonObject
                        ?.get("\$t")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val number = chapterNumber(entryTitle) ?: return@forEach
                    val link = alternateLink(entry) ?: return@forEach

                    chapters.add(
                        ChapterInfo(
                            name = "الفصل $number",
                            key = link
                        )
                    )
                }

                startIndex += 150
                guard++
                if (startIndex > total) break
            }

            chapters
                .distinctBy { it.key }
                .sortedWith(compareBy { chapterNumberOfName(it.name) })
        } catch (e: Exception) {
            Log.error { "Linovels chapters error: ${e.message}" }
            emptyList()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CONTENT (textarea#nk-raw-data with encoded HTML)
    // ──────────────────────────────────────────────────────────────

    override suspend fun getContents(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): List<Page> {
        return try {
            val html = client.get(requestBuilder(chapter.key)).bodyAsText()
            val doc: Document = Ksoup.parse(html)

            val raw = doc.selectFirst("textarea#nk-raw-data")
                ?.let { it.text().ifBlank { it.html() } }
                ?: doc.selectFirst("textarea")?.let { it.text().ifBlank { it.html() } }

            val pages = mutableListOf<Page>()

            val rawText = raw.orEmpty()
            if (rawText.isNotBlank()) {
                // Entities are double-encoded (`&amp;#8220;`), so decode twice.
                var decoded: String = rawText
                repeat(2) { decoded = decodeEntities(decoded) }

                val inner = if (decoded.contains("<")) Ksoup.parse(decoded) else null
                if (inner != null) {
                    inner.select("p").forEach { p ->
                        val text = p.text().trim()
                        if (text.isNotEmpty() && !isBoilerplate(text)) {
                            pages.add(Text(text))
                        }
                    }
                    inner.select("img[src]").forEach { img ->
                        val src = img.attr("src").trim()
                        if (src.startsWith("http")) pages.add(ImageUrl(src))
                    }
                } else {
                    decoded.split(Regex("\\n+"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !isBoilerplate(it) }
                        .forEach { pages.add(Text(it)) }
                }
            }

            // Fallback: regular article paragraphs
            if (pages.isEmpty()) {
                val container = sequenceOf("article", "main", "[class*=content]", "[class*=post]")
                    .mapNotNull { doc.selectFirst(it) }
                    .firstOrNull() ?: doc.body()

                container?.select("p")?.forEach { p ->
                    val text = p.text().trim()
                    if (text.length > 30 && !isBoilerplate(text)) {
                        pages.add(Text(text))
                    }
                }
            }

            if (pages.isEmpty()) {
                listOf(Text("لا يوجد محتوى متاح لهذا الفصل."))
            } else {
                pages
            }
        } catch (e: Exception) {
            Log.error { "Linovels content error: ${e.message}" }
            listOf(Text("خطأ في تحميل محتوى الفصل: ${e.message}"))
        }
    }

    // ──────────────────────────────────────────────────────────────
    // JSON helpers
    // ──────────────────────────────────────────────────────────────

    private fun entriesOf(feed: JsonObject): List<JsonObject> {
        val entry = feed["entry"] ?: return emptyList()
        return when (entry) {
            is JsonArray -> entry.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(entry)
            else -> emptyList()
        }
    }

    private fun categoriesOf(entry: JsonObject): List<String> {
        val categories = entry["category"] ?: return emptyList()
        return when (categories) {
            is JsonArray -> categories.mapNotNull {
                it.jsonObject["term"]?.jsonPrimitive?.contentOrNull
            }

            is JsonObject -> listOfNotNull(
                categories.jsonObject["term"]?.jsonPrimitive?.contentOrNull
            )

            else -> emptyList()
        }
    }

    private fun authorsOf(authors: JsonElement): List<String> {
        val elements = when (authors) {
            is JsonArray -> authors.toList()
            is JsonObject -> listOf(authors)
            else -> emptyList()
        }
        return elements.mapNotNull {
            it.jsonObject["name"]?.jsonObject
                ?.get("\$t")?.jsonPrimitive?.contentOrNull
        }
    }

    private fun alternateLink(entry: JsonObject): String? {
        val links = entry["link"] ?: return null
        val list = when (links) {
            is JsonArray -> links.toList()
            is JsonObject -> listOf(links)
            else -> emptyList()
        }
        return list.firstOrNull {
            it.jsonObject["rel"]?.jsonPrimitive?.contentOrNull == "alternate"
        }?.jsonObject?.get("href")?.jsonPrimitive?.contentOrNull
    }

    private suspend fun findEntryByLink(url: String, link: String): JsonObject? {
        return try {
            val body = client.get(requestBuilder(url)).bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val feed = root["feed"]?.jsonObject ?: return null
            entriesOf(feed).firstOrNull { alternateLink(it) == link }
        } catch (e: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // HTML helpers
    // ──────────────────────────────────────────────────────────────

    private fun ogImage(doc: Document): String? {
        doc.selectFirst("meta[property='og:image']")?.attr("content")?.let {
            if (it.isNotBlank()) return it
        }
        return doc.select("img[data-src]")
            .map { it.attr("data-src").trim() }
            .firstOrNull { it.contains("/uploads/") && !it.contains("/flags/") }
    }

    private fun longParagraph(doc: Document): String? {
        return doc.select("p")
            .map { it.text().trim() }
            .filter { it.length > 80 && !isBoilerplate(it) }
            .firstOrNull()
    }

    private fun labelsOf(doc: Document): List<String> {
        return doc.select("a[href*='/search/label/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** Blogger stores the series' own label as the last non-generic label. */
    private fun seriesLabel(doc: Document): String? {
        return labelsOf(doc).lastOrNull { !isGenericLabel(it) }
    }

    private fun isGenericLabel(label: String): Boolean {
        return genericLabels.contains(label.lowercase())
    }

    private fun chapterNumber(title: String): String? {
        val match = Regex("(?i)chapter\\s*([0-9]+(?:\\.[0-9]+)?)").find(title)
            ?: Regex("الفصل\\s*([0-9]+(?:\\.[0-9]+)?)").find(title)
        return match?.groupValues?.getOrNull(1)
    }

    private fun chapterNumberOfName(name: String): Double {
        return Regex("[0-9]+(?:\\.[0-9]+)?").find(name)
            ?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun isBoilerplate(text: String): Boolean {
        val markers = listOf(
            "مملكة الروايات", "Linovels", "جميع الحقوق", "سياسة الخصوصية",
            "اتصل بنا", "من نحن", "الرئيسية", "الأرشيف", "الأكثر شعبية",
            "أحدث الفصول", "الفصل السابق", "الفصل التالي", "تعليقات",
            "جارٍ التحميل", "فك تشفير", "مفتاح فك التشفير", "اسبق",
            "اشترك", "الإعلانات", "شارك", "المزيد"
        )
        return markers.any { text.contains(it, ignoreCase = true) }
    }

    private fun decodeEntities(input: String): String {
        var s = input

        val numeric = Regex("&#(x?[0-9A-Fa-f]+);")
        s = numeric.replace(s) { match ->
            val value = match.groupValues[1]
            val code = if (value.startsWith("x", true)) {
                value.drop(1).toIntOrNull(16)
            } else {
                value.toIntOrNull()
            }
            code?.let { String(Character.toChars(it)) } ?: match.value
        }

        return s
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&hellip;", "…")
            .replace("&amp;", "&")
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
