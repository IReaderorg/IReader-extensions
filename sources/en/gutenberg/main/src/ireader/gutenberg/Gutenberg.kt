package ireader.gutenberg

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.Extension

/**
 * Project Gutenberg — 70,000+ public-domain classic ebooks.
 *
 * Catalog/search via gutendex.com (JSON). Reading via gutenberg.org's canonical
 * single-HTML rendition, split at the dominant heading level.
 *
 * Chapter key format: "<bookUrl>|<headingIndex>|<tag>" where headingIndex is the
 * zero-based position within `doc.select(tag)`. The value -1 with tag "full"
 * means render the whole document (for single-chapter works).
 */
@Extension
abstract class Gutenberg(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String = "en"
    override val baseUrl: String = "https://www.gutenberg.org"
    override val id: Long = 8923715634821007L
    override val name: String = "Project Gutenberg"

    private val api = "https://gutendex.com/books"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher> = emptyList()

    // ───────────────────────── Catalog / search ─────────────────────────

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo =
        fetchCatalog("$api/?languages=en&sort=popular&page=$page")

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.filterIsInstance<Filter.Title>().firstOrNull()?.value.orEmpty()
        val url = if (query.isBlank()) "$api/?languages=en&sort=popular&page=$page"
        else "$api/?languages=en&search=${encodeQuery(query)}&page=$page"
        return fetchCatalog(url)
    }

    private suspend fun fetchCatalog(url: String): MangasPageInfo {
        val body = client.get(requestBuilder(url)).bodyAsText()
        val root = Json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
        val next = root["next"]?.jsonPrimitive?.contentOrNull
        val books = results.mapNotNull { it.jsonObject.toMangaInfoOrNull() }
        return MangasPageInfo(books, hasNextPage = !next.isNullOrBlank() && !next.equals("null", true))
    }

    private fun kotlinx.serialization.json.JsonObject.toMangaInfoOrNull(): MangaInfo? {
        val id = this["id"]?.jsonPrimitive?.intOrNull ?: return null
        val title = this["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val author = this["authors"]?.jsonArray.orEmpty().mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }.joinToString(", ")
        val cover = this["formats"]?.jsonObject?.get("image/jpeg")?.jsonPrimitive?.contentOrNull.orEmpty()
        val summary = this["summaries"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
        val subjects = this["subjects"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        val description = summary ?: subjects.take(4).joinToString("; ")
        return MangaInfo(
            key = "$baseUrl/ebooks/$id",
            title = title,
            author = author,
            cover = cover,
            description = description,
            genres = subjects,
            status = MangaInfo.COMPLETED,
        )
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val id = manga.key.substringAfterLast("/")
        val body = client.get(requestBuilder("$api/$id/")).bodyAsText()
        val obj = Json.parseToJsonElement(body).jsonObject
        return obj.toMangaInfoOrNull() ?: manga
    }

    // ───────────────────────── Chapters + content ─────────────────────────

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val id = manga.key.substringAfterLast("/").ifEmpty { return emptyList() }
        val doc = fetchBookHtml(id)
        return extractChapters(doc, manga.key, manga.title)
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val parts = chapter.key.split("|")
        val bookUrl = parts.getOrElse(0) { "" }
        val headingIndex = parts.getOrNull(1)?.toIntOrNull() ?: -1
        val tag = parts.getOrElse(2) { "h2" }
        val id = bookUrl.substringAfterLast("/").ifEmpty { return emptyList() }
        val doc = fetchBookHtml(id)
        return extractChapterContent(doc, tag, headingIndex)
    }

    private suspend fun fetchBookHtml(id: String): Document {
        val primary = "$baseUrl/cache/epub/$id/pg$id-images.html"
        val fallback = "$baseUrl/cache/epub/$id/pg$id.html"
        return try {
            client.get(requestBuilder(primary)).asJsoup()
        } catch (_: Exception) {
            client.get(requestBuilder(fallback)).asJsoup()
        }
    }

    private fun extractChapters(doc: Document, bookKey: String, bookTitle: String): List<ChapterInfo> {
        val tag = pickDominantHeadingTag(doc)
        val headings = doc.select(tag)

        val out = mutableListOf<ChapterInfo>()
        var currentPart = ""
        var seq = 0

        for (i in headings.indices) {
            val h = headings[i]
            val text = h.text().trim()
            if (text.isBlank()) continue
            if (isBoilerplateHeading(text)) continue
            if (equalsTitle(text, bookTitle)) continue

            // Section divider like "PART I", "BOOK II" — update context, don't emit.
            if (looksLikeSectionDivider(text)) {
                currentPart = text
                continue
            }

            val displayName = if (currentPart.isNotEmpty()) "$currentPart — $text" else text
            seq += 1
            out.add(
                ChapterInfo(
                    key = "$bookKey|$i|$tag",
                    name = displayName.take(200),
                    number = seq.toFloat(),
                )
            )
        }

        if (out.isEmpty()) {
            return listOf(ChapterInfo(key = "$bookKey|-1|full", name = "Full text", number = 1f))
        }
        return out
    }

    private fun pickDominantHeadingTag(doc: Document): String {
        val candidates = listOf("h2", "h3", "h1")
        val counts = candidates.map { t ->
            t to doc.select(t).count { !isBoilerplateHeading(it.text()) }
        }
        val best = counts.maxByOrNull { it.second } ?: ("h2" to 0)
        return if (best.second >= 2) best.first else "h2"
    }

    private fun extractChapterContent(doc: Document, tag: String, headingIndex: Int): List<Page> {
        if (tag == "full" || headingIndex < 0) {
            return doc.select("p, blockquote, pre").mapNotNull { el ->
                val t = el.text().trim()
                if (t.isBlank()) null else Text(t)
            }.ifEmpty { listOf(Text(doc.body().text().trim())) }
        }

        val headings = doc.select(tag)
        if (headingIndex !in headings.indices) return emptyList()
        val start = headings[headingIndex]
        val end = headings.getOrNull(headingIndex + 1)
        val contentElements = collectBetween(doc, start, end)

        val out = mutableListOf<Page>()
        val heading = start.text().trim()
        if (heading.isNotEmpty()) out.add(Text(heading))
        for (el in contentElements) {
            val t = el.text().trim()
            if (t.isBlank()) continue
            if (t.contains("END OF THE PROJECT GUTENBERG", ignoreCase = true)) break
            out.add(Text(t))
        }
        return out
    }

    /** Pre-order walk: collect content elements between two headings in document order. */
    private fun collectBetween(doc: Document, start: Element, end: Element?): List<Element> {
        val contentTags = setOf("p", "blockquote", "pre", "li", "h3", "h4", "h5", "h6")
        val all = doc.getAllElements()
        val startIdx = (0 until all.size).firstOrNull { all[it] === start } ?: return emptyList()
        val endIdx = if (end != null) {
            (startIdx + 1 until all.size).firstOrNull { all[it] === end } ?: all.size
        } else all.size
        val result = mutableListOf<Element>()
        for (i in (startIdx + 1) until endIdx) {
            val el = all[i]
            if (el.tagName().lowercase() in contentTags) result.add(el)
        }
        return result
    }

    // ───────────────────────── Heuristics ─────────────────────────

    private fun isBoilerplateHeading(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank()) return true
        val up = t.uppercase()
        return "PROJECT GUTENBERG" in up ||
            up.startsWith("*** START") || up.startsWith("*** END") ||
            up == "CONTENTS" || up == "TABLE OF CONTENTS" ||
            up.startsWith("TRANSCRIBER") ||
            up.startsWith("BY ") || up.startsWith("TRANSLATED BY ")
    }

    private fun equalsTitle(text: String, bookTitle: String): Boolean {
        if (bookTitle.isBlank()) return false
        val a = text.trim().lowercase()
        val b = bookTitle.trim().lowercase()
        return a == b || a == b.substringBefore(':').trim() || a == b.substringBefore(';').trim()
    }

    /** Matches "PART I", "BOOK II", "VOLUME 3", etc. */
    private fun looksLikeSectionDivider(text: String): Boolean {
        val t = text.trim().uppercase()
        return Regex("""^(PART|BOOK|VOLUME|SECTION|EPISODE)\s+[IVXLCDM\d]+\.?$""").matches(t)
    }

    // ───────────────────────── Utils ─────────────────────────

    private fun encodeQuery(q: String): String = buildString {
        for (c in q) when {
            c.isLetterOrDigit() || c in "-._~" -> append(c)
            c == ' ' -> append('+')
            else -> for (b in c.toString().encodeToByteArray()) {
                append('%'); append("%02X".format(b.toInt() and 0xFF))
            }
        }
    }
}
