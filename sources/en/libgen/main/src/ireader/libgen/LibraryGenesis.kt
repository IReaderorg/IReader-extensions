package ireader.libgen

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.readBytes
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
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomix.annotations.Extension

/**
 * Library Genesis — scraped HTML search + **in-extension EPUB reader**.
 *
 * Flow for EPUB items:
 *   1. Catalog scrape from libgen.li yields books with format = EPUB/PDF/MOBI/…
 *   2. On chapter list: download the EPUB (via the session-keyed `get.php` URL
 *      scraped from `ads.php`), parse its ZIP, read OPF manifest+spine, emit one
 *      ChapterInfo per spine entry with the NCX title where available.
 *   3. On page list: pull the specific spine XHTML out of the cached unzipped
 *      bytes and extract paragraphs.
 *
 * Non-EPUB items (PDF/MOBI/DJVU) fall back to a single "Download link" chapter
 * whose content surfaces the `get.php` URL for external download + import —
 * those formats can't be reconstructed into IReader's chapter-stream model
 * without OCR (PDF) or format-specific parsers we're not shipping here.
 *
 * EPUB parsing uses only `java.util.zip` + ksoup's XML parser — both already
 * available in the extension runtime. Parsed books are cached in memory by
 * MD5; size-bounded so re-reading the same book is fast but the cache doesn't
 * grow unbounded.
 */
@Extension
abstract class LibraryGenesis(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String = "en"
    override val baseUrl: String = "https://libgen.li"
    override val id: Long = 8146321774592308271L
    override val name: String = "Library Genesis"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher> = emptyList()

    // libgen.li hotlink-protects /covers/* — requests without a Referer matching the
    // site return 200 + Content-Length: 0. IReader's image loader doesn't set Referer
    // by default, so we override the per-source cover request to inject it. Without
    // this, covers appear blank in the UI even though the URL is correct.
    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> =
        client to HttpRequestBuilder().apply {
            url(url)
            header("Referer", baseUrl)
        }

    // ───────────────────────── Catalog / search ─────────────────────────

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo =
        fetchCatalog(searchUrl("", page))

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.filterIsInstance<Filter.Title>().firstOrNull()?.value.orEmpty()
        return fetchCatalog(searchUrl(query, page))
    }

    private fun searchUrl(query: String, page: Int): String {
        // Empty query returns a landing page without `id="tablelibgen"`; use the
        // "recently added" mode so browsing without a query still yields book rows.
        val req = if (query.isBlank()) "fmode:last" else encodeQuery(query)
        return "$baseUrl/index.php?req=$req&columns%5B%5D=t&columns%5B%5D=a&objects%5B%5D=f&topics%5B%5D=l&res=25&page=$page"
    }

    private suspend fun fetchCatalog(url: String): MangasPageInfo {
        val doc = client.get(requestBuilder(url)).asJsoup()
        val rows = doc.select("table#tablelibgen tbody tr")
        val stubs = rows.mapNotNull { row -> row.toMangaInfoOrNull() }
        // Hydrate cover thumbnails in parallel. LibGen search rows don't embed covers
        // inline — they live on each book's ads.php detail page as /covers/{shard}/{md5}.jpg,
        // and the shard isn't derivable from md5 alone. Fetching detail pages for every
        // row sequentially would take 25× per-request latency; bound parallelism at 6
        // so we don't slam the site too hard on a single search.
        val books = coverSemaphore.let { sem ->
            coroutineScope {
                stubs.map { manga ->
                    async {
                        val md5 = md5From(manga.key) ?: return@async manga
                        val cachedCover = coverCache[md5]
                        if (cachedCover != null) return@async manga.copy(cover = cachedCover)
                        val resolved = try {
                            sem.withPermit { fetchCoverUrl(md5) }
                        } catch (_: Exception) { null }
                        if (resolved != null) {
                            coverCache[md5] = resolved
                            manga.copy(cover = resolved)
                        } else {
                            manga
                        }
                    }
                }.awaitAll()
            }
        }
        val hasNext = doc.selectFirst("a:matchesOwn(^Next$), .pagination a:matchesOwn(^›$)") != null
        return MangasPageInfo(books, hasNextPage = hasNext && books.isNotEmpty())
    }

    // Small cover-URL cache so scrolling back to a page doesn't re-fetch ads.php.
    // Bounded at ~200 entries; evict oldest on overflow.
    private val coverCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>?): Boolean = size > 200
    }
    private val coverSemaphore = Semaphore(permits = 6)

    private suspend fun fetchCoverUrl(md5: String): String? {
        val doc = client.get(requestBuilder("$baseUrl/ads.php?md5=$md5")).asJsoup()
        val href = doc.selectFirst("img[src*=\"covers\"]")?.attr("src") ?: return null
        return absoluteUrl(href)
    }

    private fun Element.toMangaInfoOrNull(): MangaInfo? {
        val cells = select("td")
        if (cells.size < 9) return null
        // Column layout on libgen.li as of 2026:
        //   [0] Title (<a> to edition.php)   [1] Author   [2] Publisher
        //   [3] Year                         [4] Language [5] Pages
        //   [6] Size                         [7] Ext      [8] Mirrors (<a> to /ads.php?md5=…)
        val titleCell = cells[0]
        val title = (titleCell.selectFirst("a")?.text() ?: titleCell.text()).trim()
            .ifBlank { return null }
        val md5 = select("a[href*=\"ads.php?md5=\"]").firstOrNull()
            ?.attr("href")
            ?.substringAfter("md5=")
            ?.substringBefore('&')
            ?.takeIf { it.length == 32 }
            ?: return null
        val author = cells.getOrNull(1)?.text()?.trim().orEmpty()
        val year = cells.getOrNull(3)?.text()?.trim().orEmpty()
        val language = cells.getOrNull(4)?.text()?.trim().orEmpty()
        val size = cells.getOrNull(6)?.text()?.trim().orEmpty()
        val ext = cells.getOrNull(7)?.text()?.trim().orEmpty().lowercase()

        val description = buildString {
            if (year.isNotBlank()) append("Year: $year · ")
            if (language.isNotBlank()) append("Language: $language · ")
            if (size.isNotBlank()) append("Size: $size · ")
            if (ext.isNotBlank()) append("Format: ${ext.uppercase()}")
        }.trimEnd(' ', '·')

        return MangaInfo(
            // Key embeds the md5 AND the detected format so chapter-list logic can
            // decide EPUB-in-app parsing vs. download-link fallback without another
            // round-trip to the detail page.
            key = "$baseUrl/ads.php?md5=$md5#ext=$ext",
            title = title,
            author = author,
            description = description,
            genres = buildList {
                if (ext.isNotBlank()) add(ext.uppercase())
                if (language.isNotBlank()) add(language)
            },
            status = MangaInfo.COMPLETED,
        )
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val doc = client.get(requestBuilder(stripFragment(manga.key))).asJsoup()
        val fields = doc.select("table tr").associate { row ->
            val k = row.selectFirst("td")?.text()?.trim()?.trimEnd(':').orEmpty()
            val v = row.select("td").getOrNull(1)?.text()?.trim().orEmpty()
            k to v
        }
        val title = fields["Title"].orEmpty().ifBlank { manga.title }
        val author = fields["Author(s)"].orEmpty().ifBlank { manga.author }
        val description = (fields["Description"].orEmpty() + " " + manga.description).trim()
        val cover = doc.selectFirst("img[src*=\"covers\"]")?.attr("src")?.let { absoluteUrl(it) } ?: manga.cover
        return manga.copy(
            title = title,
            author = author,
            description = description,
            cover = cover,
        )
    }

    // ───────────────────────── Chapters + content ─────────────────────────

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val md5 = md5From(manga.key) ?: return emptyList()
        val ext = extFrom(manga.key)
        if (ext != "epub") {
            // Non-EPUB: single download-link chapter, same as earlier fallback.
            return listOf(
                ChapterInfo(
                    key = "link|$md5",
                    name = "Download ${ext.uppercase().ifBlank { "file" }}",
                    number = 1f,
                )
            )
        }
        // Plain try/catch — we can't use runCatching / Result here because the extension
        // is compiled against one Kotlin stdlib and loaded under another; `Result` is a
        // value class whose internal constructor_impl signature has drifted across Kotlin
        // releases and hits NoSuchMethodError at runtime. Same for every other catch in
        // this file.
        val epub = try {
            fetchAndParseEpub(md5)
        } catch (e: Exception) {
            null
        } ?: return listOf(
            ChapterInfo(
                key = "link|$md5",
                name = "Download EPUB (in-app parse failed)",
                number = 1f,
            )
        )
        return epub.spine.mapIndexed { i, entry ->
            ChapterInfo(
                key = "epub|$md5|$i",
                name = entry.title,
                number = (i + 1).toFloat(),
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val parts = chapter.key.split("|")
        return when (parts.firstOrNull()) {
            "epub" -> {
                val md5 = parts.getOrNull(1) ?: return emptyList()
                val idx = parts.getOrNull(2)?.toIntOrNull() ?: return emptyList()
                val epub = try {
                    fetchAndParseEpub(md5)
                } catch (e: Exception) {
                    null
                } ?: return listOf(Text("Failed to download or parse EPUB."))
                val entry = epub.spine.getOrNull(idx) ?: return emptyList()
                val html = epub.htmlEntries[entry.href] ?: return emptyList()
                extractXhtmlPages(html, entry.title)
            }
            "link" -> {
                val md5 = parts.getOrNull(1) ?: return emptyList()
                resolveDownloadLinkPages(md5)
            }
            else -> emptyList()
        }
    }

    // ───────────────────────── EPUB parsing ─────────────────────────

    private data class SpineEntry(val idref: String, val href: String, val title: String)
    private data class CachedEpub(
        val spine: List<SpineEntry>,
        val htmlEntries: Map<String, String>,
        val bytesLen: Int,
    )

    // Bounded cache: keep at most the 4 most recently accessed EPUBs in memory.
    // EPUBs can easily be 5–50 MB unzipped in XHTML form; 4 entries bounds worst-case
    // footprint. Eviction is LRU via LinkedHashMap(accessOrder = true).
    private val epubCache = object : LinkedHashMap<String, CachedEpub>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedEpub>?): Boolean = size > 4
    }
    private val cacheLock = Any()

    private suspend fun fetchAndParseEpub(md5: String): CachedEpub? {
        synchronized(cacheLock) { epubCache[md5] }?.let { return it }

        // 1. Scrape ads.php for the session-keyed get.php URL.
        val adsDoc = client.get(requestBuilder("$baseUrl/ads.php?md5=$md5")).asJsoup()
        val getHref = adsDoc.selectFirst("a[href^=\"get.php?md5=\"]")?.attr("href") ?: return null
        val getUrl = absoluteUrl(getHref)

        // 2. Download the EPUB bytes.
        val bytes = client.get(requestBuilder(getUrl)).readBytes()
        // Sanity-check zip magic so we don't parse HTML error pages as EPUBs.
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) return null

        // 3. Unzip into memory.
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // 4. container.xml → OPF path.
        val containerBytes = entries["META-INF/container.xml"] ?: return null
        val container = Ksoup.parseXml(String(containerBytes, Charsets.UTF_8), "")
        val opfPath = container.selectFirst("rootfile")?.attr("full-path")
            ?.takeIf(String::isNotBlank) ?: return null
        val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

        // 5. Parse OPF: manifest id→href, spine idref order, toc-ncx ref if any.
        val opfBytes = entries[opfPath] ?: return null
        val opf = Ksoup.parseXml(String(opfBytes, Charsets.UTF_8), "")
        val manifest = opf.select("manifest item").associate { it.attr("id") to it.attr("href") }
        val spineIds = opf.select("spine itemref").map { it.attr("idref") }.filter(String::isNotBlank)

        // 6. Optional NCX for chapter titles. Fallback to href basename if absent.
        val tocIdref = opf.selectFirst("spine")?.attr("toc")
        val tocHref = tocIdref?.let { manifest[it] }
        val ncxTitles: Map<String, String> = if (tocHref != null) {
            val ncxPath = resolveEpubPath(opfDir, tocHref)
            entries[ncxPath]?.let { ncxBytes ->
                val ncx = Ksoup.parseXml(String(ncxBytes, Charsets.UTF_8), "")
                ncx.select("navMap navPoint").associate { point ->
                    val src = point.selectFirst("content")?.attr("src")?.substringBefore('#').orEmpty()
                    val text = point.selectFirst("navLabel text")?.text().orEmpty()
                    resolveEpubPath(opfDir, src) to text
                }
            }.orEmpty()
        } else emptyMap()

        // 7. Build spine entries, pulling HTML bodies into the cache.
        val htmlEntries = HashMap<String, String>()
        val spine = spineIds.mapIndexedNotNull { idx, idref ->
            val href = manifest[idref] ?: return@mapIndexedNotNull null
            val fullPath = resolveEpubPath(opfDir, href)
            val data = entries[fullPath] ?: return@mapIndexedNotNull null
            htmlEntries[fullPath] = String(data, Charsets.UTF_8)
            val niceTitle = ncxTitles[fullPath]?.trim()?.takeIf(String::isNotBlank)
                ?: "Chapter ${idx + 1}"
            SpineEntry(idref, fullPath, niceTitle)
        }

        val cached = CachedEpub(spine, htmlEntries, bytes.size)
        synchronized(cacheLock) { epubCache[md5] = cached }
        return cached
    }

    /** Resolve a manifest/ncx href relative to the OPF's directory to a zip-entry path. */
    private fun resolveEpubPath(opfDir: String, href: String): String {
        val cleaned = href.substringBefore('#').trim()
        if (cleaned.isEmpty()) return ""
        if (opfDir.isEmpty()) return cleaned
        // Handle ../ up-navigation.
        val parts = "$opfDir/$cleaned".split('/').toMutableList()
        val resolved = mutableListOf<String>()
        for (p in parts) {
            when (p) {
                "", "." -> {}
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(p)
            }
        }
        return resolved.joinToString("/")
    }

    private fun extractXhtmlPages(html: String, fallbackTitle: String): List<Page> {
        val doc = Ksoup.parse(html)
        val body = doc.body()
        val out = mutableListOf<Page>()
        // Emit a lead-in heading so the reader gets a recognisable chapter break.
        val h = body.selectFirst("h1, h2, h3, h4")?.text()?.trim().orEmpty()
        val lead = h.ifBlank { fallbackTitle }
        if (lead.isNotBlank()) out.add(Text(lead))
        // Paragraph-ish elements in document order.
        for (el in body.select("p, blockquote, pre, li, h1, h2, h3, h4, h5, h6")) {
            // Skip the lead heading we already emitted.
            if (el.text().trim() == lead) continue
            val t = el.text().trim()
            if (t.isNotBlank()) out.add(Text(t))
        }
        if (out.size <= 1) {
            // Some EPUBs wrap content in <div> and have no <p>. Fall back to the raw
            // body text split on double newlines.
            body.text().trim().split(Regex("\\n{2,}")).forEach { para ->
                val t = para.trim()
                if (t.isNotBlank()) out.add(Text(t))
            }
        }
        return out
    }

    // ───────────────────────── Non-EPUB fallback ─────────────────────────

    private suspend fun resolveDownloadLinkPages(md5: String): List<Page> {
        val doc = try {
            client.get(requestBuilder("$baseUrl/ads.php?md5=$md5")).asJsoup()
        } catch (_: Exception) { null }
        val downloadUrl = doc?.selectFirst("a[href^=\"get.php?md5=\"]")
            ?.attr("href")?.let { absoluteUrl(it) }
        val out = mutableListOf<Page>()
        out.add(Text("External download required"))
        out.add(Text("This item is not an EPUB (IReader's reader needs EPUB to reconstruct chapters). Download it externally, then use Library → Import in IReader."))
        if (downloadUrl != null) {
            out.add(Text("Direct download URL (session-keyed, short-lived):"))
            out.add(Text(downloadUrl))
        }
        out.add(Text("Mirrors you can open in a browser:"))
        out.add(Text("$baseUrl/ads.php?md5=$md5"))
        out.add(Text("https://en.annas-archive.gl/md5/$md5"))
        out.add(Text("https://libgen.pw/book/$md5"))
        return out
    }

    // ───────────────────────── Utils ─────────────────────────

    private fun md5From(key: String): String? {
        val stripped = stripFragment(key)
        val md5 = stripped.substringAfter("md5=", missingDelimiterValue = "").substringBefore('&')
        return md5.takeIf { it.length == 32 }
    }

    private fun extFrom(key: String): String {
        val frag = key.substringAfter('#', missingDelimiterValue = "")
        return frag.substringAfter("ext=", missingDelimiterValue = "").substringBefore('&').lowercase()
    }

    private fun stripFragment(url: String): String = url.substringBefore('#')

    private fun absoluteUrl(href: String): String = when {
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
