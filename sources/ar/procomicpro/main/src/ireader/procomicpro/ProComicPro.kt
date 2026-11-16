package ireader.procomicpro

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
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

/**
 * ProComic Pro — procomic.pro (Arabic only)
 *
 * Public API:
 *  - Series list : /api/public/series/search?status=approved&limit=18&page=N&sort=latest
 *  - Content     : /api/content/{id}
 *  - Chapters    : /api/public/chapters?contentId={id}&page=N&limit=100
 *
 * Series URL : /ar/{slug}-{id}
 * Chapter URL: /ar/chapter/{slug}-{number}-{chapterId}
 *
 * Only Arabic (AR) chapters are exposed. Each chapter name includes the
 * translator/team name so duplicate numbers can be told apart.
 */
@Extension
@AutoSourceId(seed = "ProComicPro")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "رواية",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://procomic.pro/ar/sister-do-i-have-to-be-a-maid-together-with-you-too-929",
    chapterUrl = "https://procomic.pro/ar/chapter/sister-do-i-have-to-be-a-maid-together-with-you-too-355-247925",
    expectedTitle = "Sister, Do I Have to Be a Maid Together With You Too?",
    expectedAuthor = "电子熊猫"
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class ProComicPro(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://procomic.pro"
    override val id: Long get() = ProComicProSourceId.ID
    override val name: String get() = "ProComicPro"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val typeOptions = arrayOf("الكل", "روايات", "مانجا", "مانهوا", "مانها")
    private val typeValues = arrayOf("", "novels", "manga", "manhwa", "manhua")

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Select("النوع", typeOptions),
        Filter.Sort("الترتيب", arrayOf("الأحدث", "الأكثر شعبية"))
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // ──────────────────────────────────────────────────────────────
    // EXPLORE (main tabs)
    // ──────────────────────────────────────────────────────────────

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "أحدث الروايات",
                endpoint = "/ar/series?type=novels&sort=latest",
                selector = "a[href*='/ar/']",
                nameSelector = "h3",
                coverSelector = "img",
                coverAtt = "src",
                addBaseurlToCoverLink = true,
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "أحدث السلاسل",
                endpoint = "/ar/series?sort=latest",
                selector = "a[href*='/ar/']",
                nameSelector = "h3",
                coverSelector = "img",
                coverAtt = "src",
                addBaseurlToCoverLink = true,
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "الأكثر شعبية",
                endpoint = "/ar/series?sort=popular",
                selector = "a[href*='/ar/']",
                nameSelector = "h3",
                coverSelector = "img",
                coverAtt = "src",
                addBaseurlToCoverLink = true,
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true
            )
        )

    // ──────────────────────────────────────────────────────────────
    // LIST / SEARCH  (JSON API)
    // ──────────────────────────────────────────────────────────────

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.takeIf { it.isNotBlank() }
        val typeIdx = filters.findInstance<Filter.Select>()?.value ?: 0
        val sortIdx = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val type = typeValues.getOrElse(typeIdx) { "" }
        val sort = if (sortIdx == 1) "popular" else "latest"

        val url = buildString {
            append(baseUrl)
            append("/api/public/series/search?status=approved&limit=18&page=")
            append(page)
            append("&sort=")
            append(sort)
            if (type.isNotEmpty()) {
                append("&type=")
                append(type)
            }
            if (!query.isNullOrBlank()) {
                append("&search=")
                append(encode(query))
            }
        }
        return parseListPage(url)
    }

    private suspend fun parseListPage(url: String): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder(url))
            if (response.status.value >= 400) {
                Log.error { "ProComicPro API error ${response.status.value}: $url" }
                return MangasPageInfo(emptyList(), false)
            }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val data = root["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val hasNextPage = root["hasMore"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false

            val items = mutableListOf<MangaInfo>()
            data.forEach { node ->
                val obj = node.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@forEach
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val slug = obj["slug"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val thumbnail = obj["thumbnail"]?.jsonPrimitive?.contentOrNull
                val coverImage = obj["coverImage"]?.jsonPrimitive?.contentOrNull
                val cover = coverImage ?: thumbnail ?: ""

                val key = if (slug.isNotEmpty()) {
                    "$baseUrl/ar/$slug-$id"
                } else {
                    "$baseUrl/ar/series-$id"
                }

                items.add(
                    MangaInfo(
                        key = key,
                        title = title,
                        cover = absImage(cover),
                        description = description
                    )
                )
            }

            MangasPageInfo(mangas = items, hasNextPage = hasNextPage)
        } catch (e: Exception) {
            Log.error { "ProComicPro list error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // DETAIL (JSON API)
    // ──────────────────────────────────────────────────────────────

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return try {
            val id = extractIdFromUrl(manga.key) ?: return manga
            val response = client.get(requestBuilder("$baseUrl/api/content/$id"))
            if (response.status.value >= 400) return manga

            val obj = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: manga.title
            val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: manga.description
            val thumbnail = obj["thumbnail"]?.jsonPrimitive?.contentOrNull
            val coverImage = obj["coverImage"]?.jsonPrimitive?.contentOrNull
            val cover = coverImage ?: thumbnail ?: manga.cover

            val metadata = obj["metadata"]?.jsonObject
            val author = metadata?.get("author")?.jsonPrimitive?.contentOrNull ?: manga.author
            val genres = metadata?.get("genres")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            manga.copy(
                title = title,
                cover = absImage(cover),
                description = description ?: "",
                author = author,
                genres = if (genres.isNotEmpty()) genres else manga.genres,
                status = ONGOING
            )
        } catch (e: Exception) {
            Log.error { "ProComicPro detail error: ${e.message}" }
            manga
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CHAPTERS — Arabic (AR) only, translator shown in the name
    // ──────────────────────────────────────────────────────────────

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return try {
            val id = extractIdFromUrl(manga.key) ?: return emptyList()
            val slug = extractSlugFromUrl(manga.key) ?: return emptyList()

            val chapters = mutableListOf<ChapterInfo>()
            val seenIds = mutableSetOf<String>()

            var page = 1
            var guard = 0
            var emptyArabicPages = 0

            // Do NOT trust `hasMore` — the API sometimes reports it
            // inconsistently. Keep paging until an empty page arrives so the
            // earliest Arabic chapters (e.g. chapter 1, 50 …) are included.
            while (guard < 120) {
                val response = client.get(
                    requestBuilder(
                        "$baseUrl/api/public/chapters?contentId=$id&page=$page&limit=200"
                    )
                )
                if (response.status.value >= 400) break

                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val arr = root["chapters"]?.jsonArray ?: break
                if (arr.isEmpty()) break

                var addedArabic = 0
                arr.forEach { node ->
                    val obj = node.jsonObject

                    // Arabic chapters only
                    val language = obj["language"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (!language.equals("ar", ignoreCase = true)) return@forEach

                    val chapterId = obj["id"]?.jsonPrimitive?.contentOrNull
                        ?: return@forEach
                    if (!seenIds.add(chapterId)) return@forEach

                    val number = obj["chapter_number"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    if (number.isBlank()) return@forEach

                    val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    val translator = obj["translator"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()

                    // e.g. "الفصل 355 • Pro Chan" or "الفصل 355 - Title • Pro Chan"
                    val name = buildString {
                        append("الفصل ")
                        append(number)
                        if (rawTitle.isNotBlank() && !rawTitle.contains(number, ignoreCase = false)) {
                            append(" - ")
                            append(rawTitle)
                        } else if (rawTitle.isNotBlank() && rawTitle.length > number.length + 3) {
                            append(" - ")
                            append(rawTitle)
                        }
                        if (translator.isNotBlank()) {
                            append(" • ")
                            append(translator)
                        }
                    }

                    chapters.add(
                        ChapterInfo(
                            name = name,
                            key = "$baseUrl/ar/chapter/$slug-$number-$chapterId"
                        )
                    )
                    addedArabic++
                }

                if (addedArabic == 0) {
                    emptyArabicPages++
                    if (emptyArabicPages >= 3) break
                } else {
                    emptyArabicPages = 0
                }

                page++
                guard++
            }

            chapters
                .distinctBy { it.key }
                .sortedWith(compareBy { chapterNumber(it.name) })
        } catch (e: Exception) {
            Log.error { "ProComicPro chapters error: ${e.message}" }
            emptyList()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CONTENT — images from __NEXT_DATA__ / DOM, text for novels
    // ──────────────────────────────────────────────────────────────

    override suspend fun getContents(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): List<Page> {
        return try {
            val response = client.get(requestBuilder(chapter.key))
            val status = response.status.value
            val html = response.bodyAsText()

            if (isLocked(html)) {
                return listOf(
                    Text("🔒 هذا الفصل مقفل على ProComic ويحتاج إلى فتح/عملات لقراءته.")
                )
            }

            val doc: Document = Ksoup.parse(html)
            val pages = mutableListOf<Page>()

            // 1) DOM text — the real chapter body lives in
            //    <div class="chapter-text__content prose" dir="rtl">, with
            //    <br> line breaks inside paragraphs.
            val textContainer = sequenceOf(
                ".chapter-text__content",
                ".chapter-content", ".reading-content", ".chapter-body",
                "[class*=chapter-text]", "[class*=chapter-content]", "[class*=reading]",
                "article", "main"
            ).mapNotNull { doc.selectFirst(it) }.firstOrNull()

            val textParagraphs = textContainer?.let { extractParagraphs(it) } ?: emptyList()
            if (textParagraphs.isNotEmpty()) {
                textParagraphs.forEach { pages.add(Text(it)) }
            }

            // 2) Images inside the chapter container (manga/manhwa/manhua)
            val imageContainer = textContainer ?: doc.body()
            imageContainer?.select("img[src], img[data-src]")?.forEach { img ->
                val src = img.attr("src").ifBlank { img.attr("data-src") }
                if (isContentImage(src)) {
                    pages.add(ImageUrl(absImage(src)))
                }
            }

            // 3) Next.js embedded data (classic pages router)
            val nextData = doc.selectFirst("script#__NEXT_DATA__")?.html()
            if (pages.isEmpty() && !nextData.isNullOrBlank()) {
                extractFromEmbeddedJson(nextData, pages)
            }

            // 4) Next.js App-Router flight data — only if nothing was found yet
            if (pages.isEmpty()) {
                extractFromScripts(doc, pages)
            }

            if (pages.isEmpty()) {
                val hasNext = !nextData.isNullOrBlank()
                val scriptCount = doc.select("script").size
                val imgCount = doc.select("img").size
                val sample = html.replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(280)
                listOf(
                    Text(
                        "لم يتم العثور على محتوى هذا الفصل.\n\n" +
                            "تشخيص:\n" +
                            "• كود الاستجابة: $status\n" +
                            "• طول الصفحة: ${html.length}\n" +
                            "• بيانات Next: $hasNext\n" +
                            "• السكربتات: $scriptCount\n" +
                            "• الصور: $imgCount\n" +
                            "• الرابط: ${chapter.key}\n\n" +
                            "عينة من الصفحة:\n$sample"
                    )
                )
            } else {
                pages
            }
        } catch (e: Exception) {
            Log.error { "ProComicPro content error: ${e.message}" }
            listOf(Text("خطأ في تحميل محتوى الفصل: ${e.message}"))
        }
    }

    /**
     * Walks the embedded Next.js JSON and collects chapter images and novel text.
     */
    private fun extractFromEmbeddedJson(raw: String, pages: MutableList<Page>) {
        val root = try {
            json.parseToJsonElement(raw)
        } catch (e: Exception) {
            Log.error { "ProComicPro next-data parse error: ${e.message}" }
            return
        }

        val images = linkedSetOf<String>()
        val texts = linkedSetOf<String>()

        fun walk(element: JsonElement) {
            when (element) {
                is JsonPrimitive -> {
                    val value = element.contentOrNull?.trim() ?: return
                    if (value.isBlank()) return
                    if (looksLikeContentImage(value)) {
                        images.add(value)
                    } else if (looksLikeNovelText(value)) {
                        texts.add(value)
                    }
                }

                is JsonObject -> element.values.forEach { walk(it) }
                is JsonArray -> element.forEach { walk(it) }
            }
        }

        walk(root)

        images.forEach { pages.add(ImageUrl(absImage(it))) }
        texts.forEach { pages.add(Text(it)) }
    }

    /**
     * Extracts readable paragraphs from the chapter container.
     *
     * The site renders the whole chapter inside one <p> with <br> separators,
     * so we convert <br>/</p> into newlines before stripping tags.
     */
    private fun extractParagraphs(container: com.fleeksoft.ksoup.nodes.Element): List<String> {
        val raw = container.html()
        if (raw.isBlank()) return emptyList()

        val withBreaks = raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</div\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")

        val decoded = decodeHtmlEntities(withBreaks)

        val lines = decoded
            .split(Regex("\\n+"))
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }

        val paragraphs = mutableListOf<String>()
        lines.forEach { line ->
            if (isBoilerplate(line)) return@forEach
            paragraphs.addAll(splitLongParagraph(line))
        }

        return paragraphs.filter { it.length > 20 }
    }

    /** Splits a very long paragraph on sentence boundaries. */
    private fun splitLongParagraph(text: String): List<String> {
        if (text.length <= 1200) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!؟…])\\s+"))
        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        sentences.forEach { sentence ->
            if (sb.length + sentence.length > 900 && sb.isNotEmpty()) {
                chunks.add(sb.toString().trim())
                sb.clear()
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(sentence)
        }
        if (sb.isNotBlank()) chunks.add(sb.toString().trim())
        return chunks
    }

    private fun decodeHtmlEntities(value: String): String {
        return value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&rdquo;", "”")
            .replace("&ldquo;", "“")
            .replace("&hellip;", "…")
    }

    /**
     * Collects content from inline <script> payloads.
     *
     * Newer Next.js builds stream data through `self.__next_f.push([1,"..."])`
     * instead of `#__NEXT_DATA__`, and the chapter body is JSON-escaped inside
     * those strings. We decode the escapes and pull out image URLs and prose.
     */
    private fun extractFromScripts(doc: Document, pages: MutableList<Page>) {
        val scripts = doc.select("script")
        if (scripts.isEmpty()) return

        val raw = StringBuilder()
        scripts.forEach { script ->
            val data = script.data().ifBlank { script.html() }
            if (data.isNotBlank()) raw.append(data).append('\n')
        }
        if (raw.isEmpty()) return

        val decoded = decodeNextEscapes(raw.toString())

        val images = linkedSetOf<String>()
        val imageRegex = Regex(
            "https?://[^\\s\"'\\\\<>]+?\\.(?:avif|webp|jpe?g|png)",
            RegexOption.IGNORE_CASE
        )
        imageRegex.findAll(decoded).forEach { match ->
            val url = match.value
            if (looksLikeContentImage(url)) {
                images.add(url)
            }
        }

        // Long JSON string values → novel prose
        val texts = linkedSetOf<String>()
        val stringRegex = Regex("\"((?:[^\"\\\\]|\\\\.){100,})\"")
        stringRegex.findAll(decoded).forEach { match ->
            val candidate = unescapeSimple(match.groupValues[1])
            if (looksLikeNovelText(candidate)) {
                texts.add(candidate.trim())
            }
        }

        images.forEach { pages.add(ImageUrl(absImage(it))) }
        texts.forEach { pages.add(Text(it)) }
    }

    /** Decodes \\uXXXX / \\n / \\" escapes produced by Next.js flight data. */
    private fun decodeNextEscapes(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 5 < input.length && input[i + 1] == 'u') {
                val hex = input.substring(i + 2, i + 6)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 6
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun unescapeSimple(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\t", " ")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\r", "")
    }

    private fun looksLikeContentImage(value: String): Boolean {
        if (!value.startsWith("http") && !value.startsWith("/")) return false
        val lower = value.lowercase()
        val hasImageExt = lower.endsWith(".avif") || lower.endsWith(".webp") ||
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
        if (!hasImageExt) return false
        if (lower.contains("logo") || lower.contains("icon") || lower.contains("avatar")) return false
        if (lower.contains("series-cards") || lower.contains("series-app") ||
            lower.contains("/seo/") || lower.contains("appcard") || lower.contains("slider")
        ) return false
        return lower.contains("chapter") || lower.contains("/image_") ||
            lower.contains("cdn") || lower.contains("prochan") || lower.contains("procomic")
    }

    private fun looksLikeNovelText(value: String): Boolean {
        if (value.length < 70) return false
        if (value.contains("http") || value.contains("<") || value.contains("{")) return false
        if (isBoilerplate(value)) return false
        // Require a reasonable amount of Arabic or Latin prose
        val prose = value.count { it.isLetter() || it.isWhitespace() || it in "،؛:,.!?\"'«»…-" }
        return prose.toDouble() / value.length > 0.75
    }

    private fun isLocked(html: String): Boolean {
        // Only a paid/locked chapter shows the explicit purchase prompt.
        // The "محتوى مميز" badge also appears on free chapters, so ignore it.
        return html.contains("افتح الفصل الآن") ||
            html.contains("التصفح الآمن مطلوب")
    }

    // ──────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────

    private fun isContentImage(src: String): Boolean {
        if (src.isBlank()) return false
        val lower = src.lowercase()
        if (lower.contains("logo") || lower.contains("icon") || lower.contains("avatar")) return false
        if (lower.startsWith("data:")) return false
        if (lower.contains("series-cards") || lower.contains("/seo/")) return false
        return lower.contains("cdn") || lower.contains("prochan") || lower.contains("procomic") ||
            lower.contains("/image_")
    }

    private fun isBoilerplate(text: String): Boolean {
        val markers = listOf(
            "ProComic", "افتح الفصل الآن", "محتوى مميز", "عرض أكثر", "عرض أقل",
            "وصف الفصل", "أسئلة شائعة", "يقع الفصل", "أُضيف الفصل", "من تأليف",
            "تصنيفات العمل", "يُعرف", "كم عدد فصول", "يتوفر أكثر", "متى نُشر",
            "ما تصنيف", "عدد الدعم", "الداعمون", "ادعم", "احتمالية استمرار",
            "المؤلف", "العاملون", "آخر تحديث", "التصنيفات", "مشاركة", "نسخ الرابط",
            "تقرأ الآن", "صفحة العمل", "قائمة الفصول", "السابق", "التالي",
            "إنشاء حساب", "تسجيل الدخول", "جميع الحقوق"
        )
        return markers.any { text.contains(it, ignoreCase = true) }
    }

    private fun chapterNumber(name: String): Double {
        // Prefer the number that directly follows "الفصل" so numbers inside
        // the translated title or translator name do not corrupt the order.
        val afterPrefix = Regex("الفصل\\s+(\\d+(?:\\.\\d+)?)").find(name)
        if (afterPrefix != null) {
            afterPrefix.groupValues.getOrNull(1)?.toDoubleOrNull()?.let { return it }
        }
        val matches = Regex("\\d+(?:\\.\\d+)?").findAll(name).toList()
        return matches.firstOrNull()?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun absImage(src: String): String {
        val s = src.trim()
        return when {
            s.startsWith("http") -> s
            s.startsWith("//") -> "https:$s"
            s.startsWith("/") -> "$baseUrl$s"
            s.isBlank() -> ""
            else -> "$baseUrl/$s"
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    /** /ar/slug-929 → 929 */
    private fun extractIdFromUrl(url: String): Long? {
        val lastPart = url.trimEnd('/').substringAfterLast('/')
        return lastPart.substringAfterLast('-').toLongOrNull()
    }

    /** /ar/slug-929 → slug */
    private fun extractSlugFromUrl(url: String): String? {
        val lastPart = url.trimEnd('/').substringAfterLast('/')
        val idPart = lastPart.substringAfterLast('-')
        if (idPart.isEmpty() || idPart.any { !it.isDigit() }) return null
        return lastPart.substringBeforeLast("-$idPart").ifBlank { null }
    }
}
