package ireader.seanovel

import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations

@Extension
@AutoSourceId(seed = "SeaNovel")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "dragon",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://seanovel.org/novels/shadow-slave",
    chapterUrl = "https://seanovel.org/novels/shadow-slave/chapters/1",
    expectedTitle = "عبد الظل",
    expectedAuthor = "Guiltythree",
    expectedMinChapters = 100
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 50,
    supportsPagination = true,
    requiresLogin = false
)
abstract class SeaNovel(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://seanovel.org"
    override val id: Long get() = SeaNovelSourceId.ID
    override val name: String get() = "SeaNovel"

    private val json = Json { ignoreUnknownKeys = true }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/novels?limit=50&page=$page"))
            val body = response.bodyAsText()
            val arr = json.parseToJsonElement(body).jsonArray
            val novels = arr.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val slug = obj["slug"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val titleAr = obj["title_ar"]?.jsonPrimitive?.content ?: ""
                    val titleOriginal = obj["title_original"]?.jsonPrimitive?.content ?: ""
                    val coverVersion = obj["cover_version"]?.jsonPrimitive?.content ?: "1"
                    val title = if (titleAr.isNotBlank()) titleAr else titleOriginal
                    val cover = "$baseUrl/api/novel/$slug/cover?v=$coverVersion"
                    val key = "$baseUrl/novels/$slug"
                    MangaInfo(key = key, title = title, cover = cover)
                } catch (e: Exception) { null }
            }
            MangasPageInfo(novels, true)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.takeIf { it.isNotBlank() }
        if (query != null) {
            return try {
                val response = client.get(requestBuilder("$baseUrl/api/novels?limit=50&page=$page"))
                val body = response.bodyAsText()
                val arr = json.parseToJsonElement(body).jsonArray
                val novels = arr.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        val slug = obj["slug"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val titleAr = obj["title_ar"]?.jsonPrimitive?.content ?: ""
                        val titleOriginal = obj["title_original"]?.jsonPrimitive?.content ?: ""
                        val coverVersion = obj["cover_version"]?.jsonPrimitive?.content ?: "1"
                        val title = if (titleAr.isNotBlank()) titleAr else titleOriginal
                        if (!title.contains(query, ignoreCase = true) &&
                            !titleOriginal.contains(query, ignoreCase = true)) return@mapNotNull null
                        val cover = "$baseUrl/api/novel/$slug/cover?v=$coverVersion"
                        val key = "$baseUrl/novels/$slug"
                        MangaInfo(key = key, title = title, cover = cover)
                    } catch (e: Exception) { null }
                }
                MangasPageInfo(novels, true)
            } catch (e: Exception) {
                MangasPageInfo(emptyList(), false)
            }
        }
        return getMangaList(sort = null, page = page)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetailsFromHtml(cmd.html, manga)
        }

        val slug = manga.key.substringAfterLast("/novels/")

        return try {
            val response = client.get(requestBuilder("$baseUrl/api/novel/$slug"))
            val body = response.bodyAsText()
            val obj = json.parseToJsonElement(body).jsonObject
            val titleAr = obj["title_ar"]?.jsonPrimitive?.content ?: manga.title
            val titleOriginal = obj["title_original"]?.jsonPrimitive?.content ?: ""
            val author = obj["author"]?.jsonPrimitive?.content ?: ""
            val status = obj["status"]?.jsonPrimitive?.content ?: ""
            val description = obj["description"]?.jsonPrimitive?.content ?: ""
            val genres = obj["genres"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val coverVersion = obj["cover_version"]?.jsonPrimitive?.content ?: "1"
            val cover = "$baseUrl/api/novel/$slug/cover?v=$coverVersion"
            val displayTitle = if (titleAr.isNotBlank() && titleOriginal.isNotBlank()) {
                "$titleAr | $titleOriginal"
            } else if (titleAr.isNotBlank()) titleAr else titleOriginal

            manga.copy(
                title = displayTitle,
                cover = cover,
                description = description,
                author = author,
                genres = genres,
                status = when (status) {
                    "completed" -> MangaInfo.COMPLETED
                    "ongoing" -> MangaInfo.ONGOING
                    else -> MangaInfo.UNKNOWN
                }
            )
        } catch (e: Exception) {
            manga
        }
    }

    private fun parseDetailsFromHtml(html: String, manga: MangaInfo): MangaInfo {
        val doc = Ksoup.parse(html)
        val title = doc.selectFirst(".novel-title")?.text() ?: manga.title
        val cover = doc.selectFirst(".novel-cover")?.attr("src") ?: manga.cover
        val description = doc.selectFirst(".novel-desc-section-ios")?.text() ?: ""
        val author = doc.selectFirst(".info-author-ios")?.text() ?: ""
        val genres = doc.select(".genre-pill-modern-ios").map { it.text() }
        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = genres
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseChaptersFromHtml(cmd.html)
        }

        val slug = manga.key.substringAfterLast("/novels/")

        return try {
            val response = client.get(requestBuilder("$baseUrl/api/novel/$slug/chapters?offset=0&limit=5000"))
            val body = response.bodyAsText()
            val obj = json.parseToJsonElement(body).jsonObject
            val chaptersArr = obj["chapters"]?.jsonArray ?: return emptyList()
            chaptersArr.mapNotNull { element ->
                try {
                    val chapterObj = element.jsonObject
                    val id = chapterObj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                    val title = chapterObj["title"]?.jsonPrimitive?.content ?: "Chapter $id"
                    ChapterInfo(
                        name = title,
                        key = "$baseUrl/novels/$slug/chapters/$id"
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseChaptersFromHtml(html: String): List<ChapterInfo> {
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()
        doc.select("a[href*='/chapters/']").forEach { link ->
            val href = link.attr("href")
            val linkText = link.text().trim()
            if (linkText.isBlank()) return@forEach
            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            chapters.add(ChapterInfo(name = linkText, key = fullUrl))
        }
        return chapters.distinctBy { it.key }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseContentFromHtml(cmd.html)
        }

        return try {
            val response = client.get(requestBuilder(chapter.key))
            val body = response.bodyAsText()
            parseContentFromHtml(body)
        } catch (e: Exception) {
            listOf(Text("محتوى الفصل غير متاح حالياً."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)

        val content = doc.selectFirst(".chapter-content, .reader-content, article.reader-content")
        if (content != null) {
            val extracted = extractContentFromElement(content)
            if (extracted.isNotEmpty()) return extracted.map { Text(it) }
        }

        val readerContainer = doc.selectFirst(".reader-container")
        if (readerContainer != null) {
            val paragraphs = readerContainer.select("p")
                .filter { !it.hasClass("sr-only") && !it.hasClass("hidden") }
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 1 }
            if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }
        }

        val bodyText = doc.body()?.text() ?: ""
        val bodyParagraphs = extractChapterContentFromText(bodyText)
        if (bodyParagraphs.isNotEmpty()) return bodyParagraphs.map { Text(it) }

        return extractRscParagraphs(html).map { Text(it) }
    }

    private fun extractContentFromElement(element: com.fleeksoft.ksoup.nodes.Element): List<String> {
        val pElements = element.select("p")
            .filter { !it.hasClass("sr-only") && !it.hasClass("hidden") }
            .map { it.text().trim() }
            .filter { it.isNotBlank() && it.length > 1 }
        if (pElements.isNotEmpty()) return pElements

        val allTextElements = element.select("p, div, span")
            .filter { !it.hasClass("sr-only") && !it.hasClass("hidden") && !it.hasClass("skeleton") }
            .map { it.text().trim() }
            .filter { it.isNotBlank() && it.length > 10 }
        if (allTextElements.isNotEmpty()) return allTextElements

        val text = element.text().trim()
        return extractParagraphsFromText(text)
    }

    private fun extractParagraphsFromText(text: String): List<String> {
        return text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
    }

    private fun extractChapterContentFromText(text: String): List<String> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.length > 1 }
        val startIndex = lines.indexOfFirst { it.contains("الفصل") && (it.contains(":") || it.contains("ـ")) }
        val endIndex = lines.indexOfFirst { it.contains("انتهى الفصل") }
        val end = if (endIndex >= 0) endIndex else lines.size
        val start = if (startIndex >= 0) startIndex + 1 else 0
        if (end > start) {
            return lines.subList(start, end).filter { isChapterLine(it) }
        }
        return lines.filter { isChapterLine(it) }
    }

    private fun isChapterLine(line: String): Boolean {
        if (line.length < 5) return false
        val junk = listOf(
            "رواية", "قائمة الفصول",
            "الفصل السابق", "روايات مشابهة",
            "الرئيسية", "المفضلة",
            "معلومات قانونية", "عن الموقع", "اتصل بنا", "سياسة الخصوصية",
            "شروط الاستخدام", "© 2026", "بحر الروايات",
            "الفصل التالي", "انتهى الفصل", "تابع القراءة"
        )
        return junk.none { line.contains(it, ignoreCase = true) }
    }

    private val pushPayloadPattern = Regex(
        """self\.__next_f\.push\(\[1,"((?:[^"\\]|\\.)*)"\]\)""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val rscRefPattern = Regex("^${'$'}L[a-f0-9]+${'$'}")

    private val junkPatterns = listOf(
        "className", "function(", "localStorage", "document.",
        "setAttribute", "push([0", "__next_s", "static/chunks",
        "viewBox", "xmlns", "fetchPriority",
        "application/ld+json", "data-readersync",
        "description\",", "breadcrumb", "ld+json",
        "skeleton", "footer", "similar-novels",
        "novel-header", "aria-label"
    )

    private fun extractRscParagraphs(html: String): List<String> {
        val paragraphs = mutableListOf<String>()

        val pushPayload = buildString {
            pushPayloadPattern.findAll(html).forEach { match ->
                append(match.groupValues[1])
                append("\n")
            }
        }

        val searchText = if (pushPayload.isNotEmpty()) {
            try {
                unescapeFullPayload(pushPayload)
            } catch (e: Exception) {
                pushPayload
            }
        } else {
            html
        }

        val tPattern = Regex("""T\d+:((?:(?!T\d+:).)*)""", RegexOption.DOT_MATCHES_ALL)
        tPattern.findAll(searchText).forEach { match ->
            val text = match.groupValues[1].trim()
            if (isContentParagraph(text) && text.length > 5) {
                text.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.length > 5 }.forEach { line ->
                    if (isContentParagraph(line)) paragraphs.add(line)
                }
            }
        }

        extractInitialParagraphs(searchText, paragraphs)

        extractChildrenPatterns(searchText, paragraphs)

        if (paragraphs.isEmpty()) {
            extractNestedContent(searchText, paragraphs)
        }

        if (paragraphs.isEmpty()) {
            extractPlainTextParagraphs(searchText, paragraphs)
        }

        return paragraphs.distinct().filter { it.isNotBlank() && it.length > 1 }
    }

    private fun extractInitialParagraphs(searchText: String, paragraphs: MutableList<String>) {
        val startMarker = "\"initialParagraphs\":["
        val startIdx = searchText.indexOf(startMarker)
        if (startIdx < 0) return
        val arrayStart = startIdx + startMarker.length
        var depth = 1
        var i = arrayStart
        while (i < searchText.length && depth > 0) {
            when (searchText[i]) {
                '[' -> depth++
                ']' -> depth--
            }
            i++
        }
        val arrayContent = searchText.substring(arrayStart, i - 1)
        val stringPattern = Regex(""""((?:[^"\\]|\\.)*)"""")
        stringPattern.findAll(arrayContent).forEach { m ->
            val text = unescapeJsonString(m.groupValues[1])
            if (isContentParagraph(text)) paragraphs.add(text)
        }
    }

    private fun extractChildrenPatterns(searchText: String, paragraphs: MutableList<String>) {
        val pPattern = Regex(
            """\["\$","p","(\d+)",\{"children":"((?:[^"\\]|\\.)*)"\}\]""",
            RegexOption.DOT_MATCHES_ALL
        )
        pPattern.findAll(searchText).forEach { match ->
            val text = unescapeJsonString(match.groupValues[2])
            if (isContentParagraph(text)) paragraphs.add(text)
        }

        if (paragraphs.isEmpty()) {
            val allChildrenPattern = Regex(
                """"children":"((?:[^"\\]|\\.)*)"(?:"[^"]*"|,)(?:\}|$)""",
                RegexOption.DOT_MATCHES_ALL
            )
            allChildrenPattern.findAll(searchText).forEach { match ->
                val text = unescapeJsonString(match.groupValues[1])
                if (isContentParagraph(text)) paragraphs.add(text)
            }
        }
    }

    private fun unescapeFullPayload(payload: String): String {
        return payload
            .replace("\\\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\\\\\", "\\\\")
    }

    private fun extractNestedContent(searchText: String, paragraphs: MutableList<String>) {
        val nestedPattern = Regex(
            """\\"children\\":\\"\${'$'}${'$'}L[a-f0-9]+\\".*?"children":"((?:[^"\\]|\\.)*)"(?:"[^"]*"|,)(?:\}|${'$'})""",
            RegexOption.DOT_MATCHES_ALL
        )
        nestedPattern.findAll(searchText).forEach { match ->
            val text = unescapeJsonString(match.groupValues[1])
            if (isContentParagraph(text)) paragraphs.add(text)
        }
    }

    private fun extractPlainTextParagraphs(searchText: String, paragraphs: MutableList<String>) {
        val textPattern = Regex(""""children":"((?:[^"\\]|\\.)*)"""")
        textPattern.findAll(searchText).forEach { match ->
            val text = unescapeJsonString(match.groupValues[1])
            if (isContentParagraph(text) && text.length > 5) paragraphs.add(text)
        }
    }

    private fun isContentParagraph(text: String): Boolean {
        if (text.length <= 1) return false
        if (rscRefPattern.matches(text)) return false
        return junkPatterns.none { text.contains(it, ignoreCase = true) }
    }

    private fun unescapeJsonString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val hex = raw.substring(i + 2, i + 6)
                            val codePoint = hex.toIntOrNull(16)
                            if (codePoint != null) sb.appendCodePoint(codePoint)
                            i += 4
                        } else sb.append(raw[i])
                    }
                    else -> {
                        sb.append(raw[i])
                        sb.append(raw[i + 1])
                    }
                }
                i += 2
            } else {
                sb.append(raw[i])
                i++
            }
        }
        return sb.toString().trim()
    }
}
