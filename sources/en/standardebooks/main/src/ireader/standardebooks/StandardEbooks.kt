package ireader.standardebooks

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.get
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
import tachiyomix.annotations.Extension

/**
 * Standard Ebooks — beautifully typeset public-domain classics.
 *
 * Their OPDS feed is gated behind a donation wall, so this scrapes the public
 * `/ebooks` pages instead. The per-book single-page reading view at
 * `/ebooks/{author}/{title}/text/single-page` wraps each chapter in a
 * `<section epub:type="chapter …">` with its own `<h2>` — much cleaner than
 * Gutenberg's flat heading structure. We key chapters off that marker.
 *
 * Chapter key format: `<bookUrl>|<sectionIndex>` where sectionIndex is the
 * zero-based position within the `<section[epub\:type*="chapter"]>` match on
 * the single-page document.
 */
@Extension
abstract class StandardEbooks(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String = "en"
    override val baseUrl: String = "https://standardebooks.org"
    override val id: Long = 3721845692038715349L
    override val name: String = "Standard Ebooks"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // All listings/search go through custom HTML scraping.
    override val exploreFetchers: List<BaseExploreFetcher> = emptyList()

    // ───────────────────────── Catalog / search ─────────────────────────

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo =
        fetchCatalog("$baseUrl/ebooks?page=$page&per-page=24")

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.filterIsInstance<Filter.Title>().firstOrNull()?.value.orEmpty()
        val url = if (query.isBlank()) {
            "$baseUrl/ebooks?page=$page&per-page=24"
        } else {
            "$baseUrl/ebooks?query=${encodeQuery(query)}&page=$page&per-page=24"
        }
        return fetchCatalog(url)
    }

    private suspend fun fetchCatalog(url: String): MangasPageInfo {
        val doc = client.get(requestBuilder(url)).asJsoup()
        val cards = doc.select("ol li.ebook, ol.ebooks-list li, ol li a[href^=\"/ebooks/\"]")
        val seen = mutableSetOf<String>()
        val books = cards.mapNotNull { card ->
            val link = card.takeIf { it.tagName() == "a" }
                ?: card.selectFirst("a[href^=\"/ebooks/\"]")
                ?: return@mapNotNull null
            val href = link.attr("href")
            if (href.isBlank() || href.count { it == '/' } != 3) return@mapNotNull null // want /ebooks/author/title only
            if (!seen.add(href)) return@mapNotNull null

            val title = card.selectFirst("p[property=\"schema:name\"], .title, h3")?.text()?.trim()
                ?: link.attr("title").ifBlank { null }
                ?: href.substringAfterLast('/').replace('-', ' ').replaceFirstChar { it.uppercase() }
            val author = card.selectFirst("[property=\"schema:name\"] + p, .author, .contributors a")?.text()?.trim().orEmpty()
            val cover = card.selectFirst("img")?.let { img ->
                img.attr("srcset").substringBefore(' ').ifBlank { img.attr("src") }
            }?.let { abs(it) }.orEmpty()

            MangaInfo(
                key = "$baseUrl$href",
                title = title,
                author = author,
                cover = cover,
                description = "",
                status = MangaInfo.COMPLETED,
            )
        }
        // Standard Ebooks' pagination: pages exist as long as "next" link exists.
        val hasNext = doc.selectFirst("a[rel=\"next\"], nav.pagination a:contains(Next)") != null
        return MangasPageInfo(books, hasNextPage = hasNext)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val doc = client.get(requestBuilder(manga.key)).asJsoup()
        val title = doc.selectFirst("h1[property=\"schema:name\"], h1")?.text()?.trim() ?: manga.title
        val author = doc.select("[property=\"schema:author\"] [property=\"schema:name\"], .author a")
            .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(", ")
            .ifBlank { manga.author }
        val cover = doc.selectFirst("img.cover, picture source, figure img")?.let { el ->
            el.attr("srcset").substringBefore(' ').ifBlank { el.attr("src") }
        }?.let { abs(it) } ?: manga.cover
        val description = doc.selectFirst("section#description, .description, [property=\"schema:description\"]")
            ?.text()?.trim().orEmpty()
        val tags = doc.select("[property=\"schema:keywords\"] a, .tags a, section#tags a")
            .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
            .distinct()
        return manga.copy(
            title = title,
            author = author,
            cover = cover,
            description = description.ifBlank { manga.description },
            genres = if (tags.isNotEmpty()) tags else manga.genres,
        )
    }

    // ───────────────────────── Chapters + content ─────────────────────────

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val doc = fetchBookSinglePage(manga.key)
        val sections = realChapterSections(doc)
        if (sections.isEmpty()) {
            return listOf(ChapterInfo(key = "${manga.key}|-1", name = "Full text", number = 1f))
        }
        return sections.mapIndexed { i, section ->
            val heading = section.selectFirst("h1, h2, h3, h4")?.text()?.trim().orEmpty()
            val name = heading.ifBlank { "Chapter ${i + 1}" }.take(200)
            ChapterInfo(
                key = "${manga.key}|$i",
                name = name,
                number = (i + 1).toFloat(),
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val parts = chapter.key.split("|")
        val bookUrl = parts.getOrElse(0) { "" }
        val sectionIndex = parts.getOrNull(1)?.toIntOrNull() ?: -1
        if (bookUrl.isEmpty()) return emptyList()
        val doc = fetchBookSinglePage(bookUrl)
        return if (sectionIndex < 0) {
            // Whole-document fallback for single-chapter works.
            doc.select("p, blockquote, pre").mapNotNull { el ->
                val t = el.text().trim()
                if (t.isBlank()) null else Text(t)
            }
        } else {
            val sections = realChapterSections(doc)
            val section = sections.getOrNull(sectionIndex) ?: return emptyList()
            extractSectionContent(section)
        }
    }

    private suspend fun fetchBookSinglePage(bookUrl: String): Document {
        val singlePage = if (bookUrl.endsWith("/text/single-page")) bookUrl
        else "${bookUrl.trimEnd('/')}/text/single-page"
        return client.get(requestBuilder(singlePage)).asJsoup()
    }

    /**
     * All `<section>` elements marked as bodymatter chapters. Uses a substring match
     * on `epub:type` rather than an exact match because Standard Ebooks typically
     * combines markers like "chapter bodymatter z3998:fiction".
     */
    private fun realChapterSections(doc: Document): List<Element> =
        doc.select("section").filter { section ->
            val epubType = section.attr("epub:type")
            epubType.contains("chapter") && !epubType.contains("frontmatter") && !epubType.contains("backmatter")
        }

    private fun extractSectionContent(section: Element): List<Page> {
        val out = mutableListOf<Page>()
        val heading = section.selectFirst("h1, h2, h3, h4")?.text()?.trim().orEmpty()
        if (heading.isNotEmpty()) out.add(Text(heading))
        // Select content-bearing descendants in document order; skip the heading we
        // already emitted and any nested chapter sections.
        section.select("p, blockquote, pre, li").forEach { el ->
            val t = el.text().trim()
            if (t.isNotBlank()) out.add(Text(t))
        }
        return out
    }

    // ───────────────────────── Utils ─────────────────────────

    /** Resolve a possibly-relative URL against the site's base. */
    private fun abs(href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> "$baseUrl$href"
        else -> "$baseUrl/$href"
    }

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
