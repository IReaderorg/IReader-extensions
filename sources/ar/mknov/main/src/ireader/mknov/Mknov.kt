package ireader.mknov

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
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
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangaInfo.Companion.UNKNOWN
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.Json
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

private const val MKNOV_BASE = "https://mknov.com"

/**
 * mknov.com — مملكة الروايات (translated-novels site).
 *
 * Chapter text is protected with a **font-substitution cipher**: each chapter
 * ships a per-chapter WOFF2 font whose glyphs are *named after the real
 * letters* (ciphertext display char → glyph `uniXXXX` → real codepoint).
 * [Woff2Cmap] decodes the font into a `cp → realCp` map; we substitute the
 * ciphertext read from the chapter's `div.whitespace-pre-wrap`.
 *
 * Data comes from JSON APIs:
 *  - listing / search: `GET /api/library[?query=q]` → `{"works":[{id, slug,
 *    title, titleAr, image, author, translator, status, year, totalViews,
 *    totalChapters, genres, description}]}`
 *  - chapters: `GET /api/works/{id}/chapters` → `{"volumes":[{volumeNumber,
 *    title, chapters:[{id, chapterNumber, chapterTitle, publishDate, views}]}]}`
 *  - chapter body: `GET /novel/{workId}/chapter/{chapterId}` (HTML).
 */
@Extension
@AutoSourceId(seed = "MKNOV")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "الفصل",
    minSearchResults = 1,
)
@TestFixture(
    novelUrl = "$MKNOV_BASE/novel/109",
    chapterUrl = "$MKNOV_BASE/novel/109/chapter/43222",
    expectedTitle = "سأختم السماء",
    expectedMinChapters = 100,
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false,
)
abstract class Mknov(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = MKNOV_BASE
    override val id: Long get() = MknovSourceId.ID
    override val name: String get() = "مملكة الروايات"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // ── Listing / search ───────────────────────────────────────────────

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val query = titleFilter?.value?.takeIf { it.isNotBlank() }
        return if (query != null) {
            try {
                parseLibrary(
                    client.get(
                        requestBuilder("$baseUrl/api/library?query=${query.encodeURLParameter()}&limit=100"),
                    ).bodyAsText(),
                )
            } catch (e: Exception) {
                MangasPageInfo(emptyList(), false)
            }
        } else {
            getMangaList(sort = null, page = page)
        }
    }

    /** The three home-page sections, as the site's own carousels. */
    override fun getListings(): List<Listing> = listOf(
        NewNovelsListing(),
        MostViewedNovelsListing(),
        LatestUpdatesListing(),
    )

    /** "الروايات الجديدة" — freshly added works (`sort=newest`). */
    class NewNovelsListing : Listing("الروايات الجديدة")
    /** "الأكثر مشاهدة" — most-viewed works (`sort=views`). */
    class MostViewedNovelsListing : Listing("الأكثر مشاهدة")
    /** "آخر الإصدارات" — the default library order (`sort=latest`). */
    class LatestUpdatesListing : Listing("آخر الإصدارات")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val sortKey = when (sort) {
            is NewNovelsListing -> "newest"
            is MostViewedNovelsListing -> "views"
            is LatestUpdatesListing -> "latest"
            else -> null
        }
        val url = if (sortKey != null) {
            "$baseUrl/api/library?sort=$sortKey&page=${page + 1}&limit=100"
        } else {
            "$baseUrl/api/library?page=${page + 1}&limit=100"
        }
        return try {
            parseLibrary(client.get(requestBuilder(url)).bodyAsText())
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    private fun parseLibrary(body: String): MangasPageInfo {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return MangasPageInfo(emptyList(), false)
        val works = root["works"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
        val total = root["total"]?.jsonPrimitive?.intOrNull ?: 0
        val mangas = works.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            toManga(obj)
        }
        // hasNextPage: the API caps each response at 100 items; a full page or
        // fewer-delivered-than-total means more data is available.
        val hasNext = if (mangas.isNotEmpty()) {
            if (total > 0) mangas.size < total else mangas.size >= 100
        } else {
            false
        }
        return MangasPageInfo(mangas, hasNext)
    }

    private fun toManga(obj: kotlinx.serialization.json.JsonObject): MangaInfo? {
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return null
        // Prefer the Arabic title (titleAr); some works expose the original
        // title in `title` (e.g. Chinese) and the site displays the Arabic one.
        val title = obj["titleAr"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: obj["title"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val cover = obj["image"]?.jsonPrimitive?.contentOrNull ?: ""
        val author = obj["author"]?.jsonPrimitive?.contentOrNull ?: ""
        val translator = obj["translator"]?.jsonPrimitive?.contentOrNull ?: ""
        val year = obj["year"]?.jsonPrimitive?.intOrNull ?: 0
        val views = obj["totalViews"]?.jsonPrimitive?.intOrNull ?: 0
        val genres = obj["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val status = when (obj["status"]?.jsonPrimitive?.contentOrNull) {
            "completed" -> COMPLETED
            "ongoing" -> ONGOING
            else -> UNKNOWN
        }
        return MangaInfo(
            key = "$baseUrl/novel/$id",
            title = title,
            cover = cover,
            author = author,
            description = buildDescription(description, translator, year, views),
            genres = genres,
            status = status,
        )
    }

    private fun buildDescription(description: String, translator: String, year: Int, views: Int): String {
        val meta = mutableListOf<String>()
        if (translator.isNotBlank()) meta.add("المترجم: $translator")
        if (year > 0) meta.add("سنة النشر: $year")
        if (views > 0) meta.add("المشاهدات: $views")
        val metaNote = meta.joinToString(" — ")
        return if (description.isBlank()) metaNote
        else if (metaNote.isBlank()) description
        else "$description\n\n$metaNote"
    }

    // ── Detail ─────────────────────────────────────────────────────────

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        // The library API already carries the full record (description, author,
        // genres, status), which we filled at list time — nothing left to fetch.
        return manga
    }

    // ── Chapters ───────────────────────────────────────────────────────

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val workId = manga.key.substringAfterLast('/')
        return try {
            val body = client.get(requestBuilder("$baseUrl/api/works/$workId/chapters")).bodyAsText()
            parseChaptersJson(body, workId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseChaptersJson(body: String, workId: String): List<ChapterInfo> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        val volumes = root["volumes"]?.jsonArray ?: return emptyList()
        val chapters = mutableListOf<ChapterInfo>()
        for (volEl in volumes) {
            val vol = runCatching { volEl.jsonObject }.getOrNull() ?: continue
            val chs = vol["chapters"]?.jsonArray ?: continue
            for (chEl in chs) {
                val ch = runCatching { chEl.jsonObject }.getOrNull() ?: continue
                val chapterId = ch["id"]?.jsonPrimitive?.intOrNull ?: continue
                val chapterNumber = ch["chapterNumber"]?.jsonPrimitive?.intOrNull ?: 0
                val chapterTitle = ch["chapterTitle"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = if (chapterTitle.isNotBlank()) {
                    "الفصل $chapterNumber: $chapterTitle"
                } else {
                    "الفصل $chapterNumber"
                }
                chapters.add(
                    ChapterInfo(
                        name = name,
                        key = "$baseUrl/novel/$workId/chapter/$chapterId",
                        number = chapterNumber.toFloat(),
                    ),
                )
            }
        }
        return chapters
    }

    // ── Chapter content (font-substitution cipher) ─────────────────────

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return decryptHtml(cmd.html, emptyList(), chapter.key)
        }
        return try {
            val html = client.get(requestBuilder(chapter.key)).bodyAsText()
            decryptHtml(html, emptyList(), chapter.key)
        } catch (e: Exception) {
            listOf(Text("تعذر تحميل محتوى الفصل."))
        }
    }

    /**
     * Decrypt a chapter's ciphertext into real text.
     *
     * [extraPages] is kept for the Content.Fetch path (not used by mknov — the
     * body is a single block of substituted text). [cacheKey] uniquifies the
     * font URL (mknov serves the same WOFF2 path per chapter, so a naive fetch
     * can return a stale/corrupt cached copy).
     */
    private suspend fun decryptHtml(html: String, extraPages: List<Page>, cacheKey: String): List<Page> {
        val fontUrl = extractFontUrl(html)
        if (fontUrl == null) return listOf(Text("تعذر تحميل خط الحماية."))
        val woff2 = try {
            client.get(absoluteUrl(fontUrl)) {
                parameter("h", cacheKey.toString())
            }.bodyAsBytes()
        } catch (e: Exception) {
            return listOf(Text("تعذر تحميل خط الحماية."))
        }
        if (woff2.size < 400 || !(woff2[0].toInt() == 'w'.code && woff2[1].toInt() == 'O'.code &&
                woff2[2].toInt() == 'F'.code && woff2[3].toInt() == '2'.code)
        ) {
            // "wOFF2" magic missing → the response isn't the font (cache-trapped
            // or changed origin). Show a message rather than decrypt garbage.
            return listOf(Text("تعذر تحميل خط الحماية."))
        }
        val sub = Woff2Cmap.substitutionMap(woff2)

        val doc = runCatching { Ksoup.parse(html) }.getOrNull()
            ?: return listOf(Text("تعذر قراءة محتوى الفصل."))
        val contentDiv = doc.selectFirst("div.whitespace-pre-wrap, div[style*=font-family]")
            ?: return listOf(Text("تعذر قراءة محتوى الفصل."))

        // Pull the div's *raw* inner HTML (not .text(), which collapses the
        // pre-wrap newlines into single spaces) and strip the tags while
        // preserving the \n\n paragraph breaks.
        val cipher = stripTags(contentDiv.html())
        val sb = StringBuilder(cipher.length)
        for (ch in cipher) {
            val cp = ch.code
            val real = sub[cp]
            when {
                real != null -> sb.appendCodePoint(real)
                !isNoise(cp) -> sb.append(ch)
                // else: decoy noise → skip.
            }
        }
        val plain = sb.toString()
        // Each \n+-separated, non-blank run is one paragraph → one page.
        val paragraphs = plain.split(Regex("""\n+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val pages = if (paragraphs.isNotEmpty()) paragraphs.map { Text(it) } else listOf(Text(plain))
        return extraPages + pages
    }

    /** Removes HTML tags, preserving the text and its `\n` paragraph breaks. */
    private fun stripTags(html: String): String {
        val sb = StringBuilder(html.length)
        var i = 0
        val n = html.length
        while (i < n) {
            val c = html[i]
            when {
                c == '<' -> {
                    val close = html.indexOf('>', i)
                    if (close < 0) {
                        sb.append(html, i, n)
                        break
                    }
                    val tag = html.substring(i + 1, close)
                    // <br> / <p> contribute a newline (block layout).
                    if (tag.startsWith("br") || tag.startsWith("p")) sb.append('\n')
                    i = close + 1
                    // Skip whole script/style blocks so their bodies (often
                    // Next.js flight payloads) never leak into the text.
                    val lower = tag.lowercase()
                    if (lower.startsWith("script") || lower.startsWith("style")) {
                        val endTag = "</" + lower.takeWhile { it.isLetter() } + ">"
                        val end = html.indexOf(endTag, i)
                        if (end >= 0) i = end + endTag.length
                    }
                }
                c == '&' -> {
                    val semi = html.indexOf(';', i)
                    if (semi in (i + 1)..(i + 10)) {
                        val ent = html.substring(i + 1, semi)
                        val dec = when (ent) {
                            "nbsp" -> " "
                            "amp" -> "&"
                            "lt" -> "<"
                            "gt" -> ">"
                            "quot" -> "\""
                            "apos" -> "'"
                            else -> null
                        }
                        if (dec != null) { sb.append(dec); i = semi + 1; continue }
                    }
                    sb.append(c); i++
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    /** True when [cp] is CJK/Kana/Hangul letter — the obfuscation's decoy noise. */
    private fun isNoise(cp: Int): Boolean = when (cp) {
        // CJK Unified Ideographs, CJK Extension A, Compat Ideographs
        in 0x4E00..0x9FFF, in 0x3400..0x4DBF, in 0xF900..0xFAFF,
        // Hangul Syllables, Hangul Jamo, Kangxi radicals
        in 0xAC00..0xD7AF, in 0x1100..0x11FF, in 0x2F00..0x2FDF,
        // Hiragana / Katakana (+ Halfwidth)
        in 0x3040..0x30FF, in 0xFF65..0xFF9F,
        -> true
        else -> false
    }

    /** Finds the first `@font-face` `src:url(...woff2)` for the chapter font. */
    private fun extractFontUrl(html: String): String? {
        val regex = Regex("""url\(\s*["']?([^"')]+?\.woff2[^"')]*)""", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun absoluteUrl(u: String): String =
        if (u.startsWith("http")) u else "$baseUrl$u"
}