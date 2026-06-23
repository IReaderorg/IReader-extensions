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
            if (cmd.html.isNotBlank()) {
                try { return parseChaptersFromHtml(cmd.html) } catch (_: Exception) { }
            }
        }

        val slug = manga.key.substringAfterLast("/novels/")
        val allChapters = mutableListOf<ChapterInfo>()
        var offset = 0
        val limit = 100

        do {
            try {
                val response = client.get(requestBuilder("$baseUrl/api/novel/$slug/chapters?offset=$offset&limit=$limit"))
                val body = response.bodyAsText()
                val obj = try {
                    json.parseToJsonElement(body).jsonObject
                } catch (_: Exception) {
                    break
                }
                val chaptersArr = obj["chapters"]?.jsonArray ?: break
                val hasMore = obj["hasMore"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

                for (element in chaptersArr) {
                    try {
                        val chapterObj = element.jsonObject
                        val idStr = chapterObj["id"]?.jsonPrimitive?.content ?: continue
                        val id = idStr.toDoubleOrNull()?.toInt() ?: idStr.toIntOrNull() ?: continue
                        val title = chapterObj["title"]?.jsonPrimitive?.content ?: "Chapter $id"
                        allChapters.add(
                            ChapterInfo(
                                name = title,
                                key = "$baseUrl/novels/$slug/chapters/$id",
                                number = id.toFloat()
                            )
                        )
                    } catch (_: Exception) { }
                }

                if (!hasMore || chaptersArr.size < limit) break
                offset += limit
            } catch (_: Exception) {
                break
            }
        } while (true)

        return allChapters
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
            if (cmd.html.isNotBlank()) {
                try { return parseContentFromHtml(cmd.html) } catch (_: Exception) { }
            }
        }

        return try {
            val response = client.get(requestBuilder(chapter.key))
            val body = response.bodyAsText()
            val result = try { parseContentFromHtml(body) } catch (_: Exception) { emptyList() }
            if (result.isNotEmpty()) return result

            val fallback = try { extractRscParagraphs(body) } catch (_: Exception) { emptyList() }
            if (fallback.isNotEmpty()) return fallback.map { Text(it) }

            val plain = try { extractPlainTextFallback(body) } catch (_: Exception) { emptyList() }
            if (plain.isNotEmpty()) return plain.map { Text(it) }

            listOf(Text("محتوى الفصل غير متاح حالياً."))
        } catch (e: Exception) {
            listOf(Text("محتوى الفصل غير متاح حالياً."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val allParagraphs = mutableListOf<String>()

        try {
            val doc = Ksoup.parse(html)
            val readerContent = doc.selectFirst("article.reader-content")
            if (readerContent != null) {
                val inlineParagraphs = readerContent.select("p")
                    .filter { !it.hasClass("sr-only") && !it.hasClass("hidden") }
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() && it.length > 1 }
                allParagraphs.addAll(inlineParagraphs)
            }
        } catch (_: Exception) { }

        try {
            val hiddenDivParagraphs = extractFromHiddenDivs(html)
            allParagraphs.addAll(hiddenDivParagraphs)
        } catch (_: Exception) { }

        if (allParagraphs.size > 3) {
            val filtered = allParagraphs.filter { it.isNotBlank() && it.length > 1 }
            if (filtered.isNotEmpty()) return filtered.map { Text(it) }
        }

        try {
            val doc = Ksoup.parse(html)
            val content = doc.selectFirst(".chapter-content, .reader-content")
            if (content != null) {
                val extracted = extractContentFromElement(content)
                if (extracted.isNotEmpty()) return extracted.map { Text(it) }
            }
        } catch (_: Exception) { }

        try {
            val initialParagraphs = extractInitialParagraphsFromJson(html)
            if (initialParagraphs.isNotEmpty()) return initialParagraphs.map { Text(it) }
        } catch (_: Exception) { }

        return try {
            extractRscParagraphs(html).map { Text(it) }
        } catch (_: Exception) {
            extractPlainTextFallback(html).map { Text(it) }
        }
    }

    private val hiddenDivPattern = Regex(
        """<div hidden id="S:(\w+)">\s*<p(?:\s[^>]*)?>(.*?)</p>\s*</div>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private fun extractFromHiddenDivs(html: String): List<String> {
        val paragraphs = mutableListOf<String>()
        val matches = hiddenDivPattern.findAll(html)
        for (match in matches) {
            val rawText = match.groupValues[2]
            val text = unescapeHtmlEntities(rawText).trim()
            if (text.isNotBlank() && text.length > 1 && isContentParagraph(text)) {
                paragraphs.add(text)
            }
        }
        return paragraphs
    }

    private fun unescapeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { match ->
                try { String(Character.toChars(match.groupValues[1].toInt())) } catch (_: Exception) { match.value }
            }
    }

    private fun extractPlainTextFallback(html: String): List<String> {
        val textPattern = Regex(""""children":"((?:[^"\\]|\\.)*)"""")
        return try {
            textPattern.findAll(html)
                .map { unescapeJsonString(it.groupValues[1]) }
                .filter { it.length > 10 && isContentParagraph(it) }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractInitialParagraphsFromJson(html: String): List<String> {
        val pushPayload = try {
            buildString {
                pushPayloadPattern.findAll(html).forEach { match ->
                    append(match.groupValues[1])
                    append("\n")
                }
            }
        } catch (_: Exception) { "" }

        val searchText = if (pushPayload.isNotEmpty()) {
            try { unescapeFullPayload(pushPayload) } catch (_: Exception) { pushPayload }
        } else html

        val startMarker = "\"initialParagraphs\":"
        val startIdx = searchText.indexOf(startMarker)
        if (startIdx < 0) return emptyList()

        val arrayStart = startIdx + startMarker.length
        var depth = 0
        var i = arrayStart
        try {
            while (i < searchText.length && i < arrayStart + 500_000) {
                when (searchText[i]) {
                    '[' -> depth++
                    ']' -> { depth--; if (depth == 0) break }
                }
                i++
            }
        } catch (_: Exception) {
            return emptyList()
        }

        if (depth != 0 || i >= searchText.length) return emptyList()
        val arrayContent = searchText.substring(arrayStart, i)
        val stringPattern = Regex(""""((?:[^"\\]|\\.)*)"""")
        return try {
            stringPattern.findAll(arrayContent)
                .map { unescapeJsonString(it.groupValues[1]) }
                .filter { it.isNotBlank() && it.length > 1 && isContentParagraph(it) }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
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
        """self\.__next_f\.push\(\[1,"((?:[^\\]|\\.)*)"\]\)""",
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

        val pushPayload = try {
            buildString {
                pushPayloadPattern.findAll(html).forEach { match ->
                    append(match.groupValues[1])
                    append("\n")
                }
            }
        } catch (_: Exception) { "" }

        val searchText = if (pushPayload.isNotEmpty()) {
            try {
                unescapeFullPayload(pushPayload)
            } catch (_: Exception) {
                pushPayload
            }
        } else {
            html
        }

        try {
            val tPattern = Regex("""T\d+:((?:(?!T\d+:).)*)""", RegexOption.DOT_MATCHES_ALL)
            tPattern.findAll(searchText).forEach { match ->
                val text = match.groupValues[1].trim()
                if (isContentParagraph(text) && text.length > 5) {
                    text.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.length > 5 }.forEach { line ->
                        if (isContentParagraph(line)) paragraphs.add(line)
                    }
                }
            }
        } catch (_: Exception) { }

        try { extractInitialParagraphs(searchText, paragraphs) } catch (_: Exception) { }

        try { extractChildrenPatterns(searchText, paragraphs) } catch (_: Exception) { }

        try { extractRscParagraphsWithRefs(searchText, paragraphs) } catch (_: Exception) { }

        if (paragraphs.isEmpty()) {
            try { extractNestedContent(searchText, paragraphs) } catch (_: Exception) { }
        }

        if (paragraphs.isEmpty()) {
            try { extractPlainTextParagraphs(searchText, paragraphs) } catch (_: Exception) { }
        }

        return paragraphs.distinct().filter { it.isNotBlank() && it.length > 1 }
    }

    private fun extractInitialParagraphs(searchText: String, paragraphs: MutableList<String>) {
        val startMarker = "\"initialParagraphs\":"
        val startIdx = searchText.indexOf(startMarker)
        if (startIdx < 0) return
        val arrayStart = startIdx + startMarker.length
        var depth = 0
        var i = arrayStart
        while (i < searchText.length) {
            when (searchText[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            i++
        }
        val arrayContent = searchText.substring(arrayStart, i)
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

        var startPos = 0
        while (startPos < searchText.length - 30) {
            val idx = searchText.indexOf(""""children":"[""", startPos)
            if (idx < 0) break

            var i = idx + 13
            while (i < searchText.length - 2) {
                if (searchText[i] == ']' && searchText[i + 1] == '"') {
                    val text = searchText.substring(idx + 13, i)
                    if (text.isNotEmpty() && isContentParagraph(text) && text.length > 5) {
                        paragraphs.add(unescapeJsonString(text))
                    }
                    break
                }
                if (searchText[i] == '"' && i + 2 < searchText.length &&
                    searchText[i + 1] == '}' && searchText[i + 2] == ']') {
                    val text = searchText.substring(idx + 13, i)
                    if (text.isNotEmpty() && isContentParagraph(text) && text.length > 5) {
                        paragraphs.add(unescapeJsonString(text))
                    }
                    break
                }
                i++
            }

            startPos = idx + 1
        }
    }

    private fun extractRscParagraphsWithRefs(searchText: String, paragraphs: MutableList<String>) {
        // Pattern: $Lxx:["$","p","id",{"children":"..."}] 
        val marker = "${'$'}L"
        var searchPos = 0
        while (searchPos < searchText.length - 20) {
            val refIdx = searchText.indexOf(marker, searchPos)
            if (refIdx < 0) break

            var hexEnd = refIdx + 2
            while (hexEnd < searchText.length && searchText[hexEnd].isLetterOrDigit()) hexEnd++

            if (hexEnd >= searchText.length || searchText[hexEnd] != ':') {
                searchPos = hexEnd
                continue
            }

            val afterColon = searchText.substring(hexEnd + 1)
            if (!afterColon.startsWith("[")) {
                searchPos = hexEnd
                continue
            }

            val pIdx = afterColon.indexOf("\"p\"")
            if (pIdx < 0) {
                searchPos = hexEnd
                continue
            }

            val afterP = afterColon.substring(pIdx + 4)
            val childrenIdx = afterP.indexOf("\"children\"")
            if (childrenIdx < 0) {
                searchPos = hexEnd + pIdx + 4
                continue
            }

            val afterChildrenKey = afterP.substring(childrenIdx + 11)
            var textStart = -1
            if (afterChildrenKey.startsWith(":\"")) {
                textStart = 2
            } else if (afterChildrenKey.startsWith(":")) {
                val quoteIdx = afterChildrenKey.indexOf('"')
                if (quoteIdx >= 0) textStart = quoteIdx + 1
            }

            if (textStart >= 0) {
                val textAfterQuote = afterChildrenKey.substring(textStart)
                // Find pattern "}]+ 
                var endPos = -1
                for (i in textAfterQuote.indices) {
                    if (textAfterQuote[i] == '"' && 
                        i + 2 < textAfterQuote.length && 
                        textAfterQuote[i + 1] == '}' && 
                        textAfterQuote[i + 2] == ']') {
                        endPos = i
                        break
                    }
                }
                if (endPos > 0) {
                    val extracted = textAfterQuote.substring(0, endPos)
                    if (extracted.isNotEmpty() && isContentParagraph(extracted) && extracted.length > 5) {
                        paragraphs.add(unescapeJsonString(extracted))
                    }
                }
            }

            searchPos = hexEnd + pIdx + childrenIdx + 20
        }
    }

    private fun unescapeFullPayload(payload: String): String {
        val sb = StringBuilder(payload.length)
        var i = 0
        while (i < payload.length) {
            if (payload[i] == '\\' && i + 1 < payload.length) {
                when (payload[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < payload.length) {
                            val hex = payload.substring(i + 2, i + 6)
                            val codePoint = hex.toIntOrNull(16)
                            if (codePoint != null) sb.appendCodePoint(codePoint)
                            i += 4
                        } else {
                            sb.append(payload[i])
                            sb.append(payload[i + 1])
                        }
                    }
                    else -> {
                        sb.append(payload[i])
                        sb.append(payload[i + 1])
                    }
                }
                i += 2
            } else {
                sb.append(payload[i])
                i++
            }
        }
        return sb.toString()
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