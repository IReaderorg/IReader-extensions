package ireader.arabnovels

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.json.Json
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

/**
 * ArabNovels — منصة الروايات العربية (arabnovels.com), Arabic novels.
 *
 * The site is a Next.js app whose pages are Server-Side Rendered, so novel lists,
 * details and chapter indexes are embedded in the HTML. Chapter body text is
 * embedded as a Next.js RSC (React Server Components) text ref, e.g.
 *   3a:T2eb3,<hr /><p>...</p>
 * referenced by `dangerouslySetInnerHTML={{__html: "$3a"}}`.
 *
 * URLs:
 *  - Novel list : /novels?q=QUERY (search), /novels?sort=popular, /novels?status=COMPLETED
 *  - Novel detail: /novels/{slug}/   (contains full chapter index: id, number, title)
 *  - Chapter read: /read/{slug}/{number}/  (body in RSC text ref)
 */
@Extension
@AutoSourceId(seed = "ArabNovels")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "رواية",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://arabnovels.com/novels/lord-of-truth/",
    chapterUrl = "https://arabnovels.com/read/lord-of-truth/1/",
    expectedTitle = "لورد الحقيقة",
    expectedAuthor = "zeus"
)
@TestExpectations(
    minLatestNovels = 1,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class ArabNovels(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://arabnovels.com"
    override val id: Long get() = ArabNovelsSourceId.ID
    override val name: String get() = "ArabNovels"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val detailFetcher: SourceFactory.Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "img[src*='/media/covers/']",
            coverAtt = "src",
            addBaseurlToCoverLink = true,
            authorBookSelector = "a[href^='/u/']",
            onAuthor = { it.trim() },
            descriptionSelector = "meta[property='og:description']"
        )

    override val chapterFetcher: SourceFactory.Chapters
        get() = SourceFactory.Chapters(
            selector = "a[href*='/read/']",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            nameSelector = "span",
            reverseChapterList = false
        )

    override val contentFetcher: SourceFactory.Content
        get() = SourceFactory.Content(
            pageContentSelector = "#chapter-body p"
        )

    override val exploreFetchers: List<SourceFactory.BaseExploreFetcher>
        get() = listOf(
            SourceFactory.BaseExploreFetcher(
                "جميع الروايات",
                endpoint = "/novels",
                selector = "article.group",
                nameSelector = "h3",
                coverSelector = "img",
                coverAtt = "src",
                addBaseurlToCoverLink = true,
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true
            ),
            SourceFactory.BaseExploreFetcher(
                "الأكثر قراءة",
                endpoint = "/novels?sort=popular",
                selector = "article.group",
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
    // DETAIL (novel page includes the full chapter index)
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

            val cover = doc.selectFirst("img[src*='/media/covers/']")?.attr("src")?.let {
                if (it.startsWith("http")) it else baseUrl + it
            } ?: manga.cover

            val author = doc.selectFirst("a[href^='/u/']")?.text()?.trim() ?: ""

            val description = doc.selectFirst("meta[property='og:description']")
                ?.attr("content")?.trim() ?: ""

            val statusText = doc.select("span, div")
                .map { it.text().trim() }
                .firstOrNull { it in listOf("مستمرة", "مكتملة", "متوقفة") }
            val status = when (statusText) {
                "مكتملة" -> COMPLETED
                else -> ONGOING
            }

            manga.copy(
                title = title,
                cover = cover,
                description = description,
                author = author,
                status = status
            )
        } catch (e: Exception) {
            Log.error { "ArabNovels detail error: ${e.message}" }
            manga
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CHAPTER LIST (from the RSC chapter index embedded in the novel page)
    // ──────────────────────────────────────────────────────────────

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return try {
            val slug = manga.key.removePrefix(baseUrl).removeSuffix("/").substringAfterLast("/")
            val chapters = mutableListOf<ChapterInfo>()

            // Prefer the reader page — it contains the full chapter index in the
            // RSC payload (`"index":[{"number":N,...}]`). This is the most reliable
            // source because the novel page only lists a subset.
            val readerUrl = "$baseUrl/read/$slug/1/"
            val readerHtml = client.get(requestBuilder(readerUrl)).bodyAsText()
            val readerRsc = decodeRsc(readerHtml)

            // Extract every `"number":N` from the reader RSC index array
            val numRegex = Regex(""""number":([0-9]+(?:\.[0-9]+)?)""")
            val numbers = numRegex.findAll(readerRsc)
                .map { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
                .filter { it > 0 }
                .distinct()
                .sorted()

            for (number in numbers) {
                chapters.add(
                    ChapterInfo(
                        name = formatChapterName(number),
                        key = "$baseUrl/read/$slug/$number/"
                    )
                )
            }

            // Fallback: scrape chapter links from rendered HTML of the novel page
            if (chapters.isEmpty()) {
                val html = client.get(requestBuilder(manga.key)).bodyAsText()
                val doc: Document = Ksoup.parse(html)
                doc.select("a[href*='/read/']").forEach { anchor ->
                    val href = anchor.attr("href").trim()
                    if (href.isBlank()) return@forEach
                    val link = when {
                        href.startsWith("http") -> href
                        href.startsWith("/") -> baseUrl + href
                        else -> baseUrl + "/" + href
                    }
                    val number = Regex("/read/[^/]+/([0-9]+(?:\\.[0-9]+)?)").find(link)
                        ?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    if (number > 0) {
                        chapters.add(ChapterInfo(name = formatChapterName(number), key = link))
                    }
                }
            }

            chapters
                .distinctBy { it.key }
                .sortedWith(compareBy { chapterNumberOfName(it.name) })
        } catch (e: Exception) {
            Log.error { "ArabNovels chapter list error: ${e.message}" }
            emptyList()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CONTENT (chapter body embedded as an RSC text ref)
    // ──────────────────────────────────────────────────────────────

    override suspend fun getContents(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): List<Page> {
        return try {
            val html = client.get(requestBuilder(chapter.key)).bodyAsText()
            val rsc = decodeRsc(html)

            // Find the chapter-body div's __html ref key (e.g. $3a)
            val refMatch = Regex(
                """(?s)id="chapter-body".*?"__html":"\$([0-9a-f]+)""""
            ).find(rsc)
                ?: Regex("""__html":"\$([0-9a-f]+)"""").find(rsc)

            val refKey = refMatch?.groupValues?.getOrNull(1)

            val contentHtml = if (refKey != null) {
                // RSC text ref: <key>:T<hexlen>,<content>
                val m = Regex("""\n$refKey:T([0-9a-fA-F]+),""").find(rsc)
                    ?: Regex("""$refKey:T([0-9a-fA-F]+),""").find(rsc)
                if (m != null) {
                    val hexLen = m.groupValues[1]
                    val len = Integer.parseInt(hexLen, 16)
                    val start = m.range.last + 1
                    rsc.substring(start, minOf(start + len, rsc.length))
                } else {
                    null
                }
            } else {
                null
            }

            val pages = mutableListOf<Page>()
            if (contentHtml != null) {
                val doc = Ksoup.parse(contentHtml)
                doc.select("p").forEach { p ->
                    val text = p.text().trim()
                    if (text.isNotBlank() && text.length > 3) {
                        pages.add(Text(text))
                    }
                }
                if (pages.isEmpty()) {
                    val text = doc.body()?.text()?.trim()
                    if (!text.isNullOrBlank() && text.length > 10) {
                        pages.add(Text(text))
                    }
                }
            }

            if (pages.isEmpty()) {
                pages.add(Text("تعذّر جلب محتوى هذا الفصل."))
            }

            pages
        } catch (e: Exception) {
            Log.error { "ArabNovels content error: ${e.message}" }
            listOf(Text("خطأ في تحميل محتوى الفصل: ${e.message}"))
        }
    }

    // ──────────────────────────────────────────────────────────────
    // RSC decoding helpers
    // ──────────────────────────────────────────────────────────────

    /** Decode all Next.js RSC `self.__next_f.push([1,"..."])` payloads into one string. */
    private fun decodeRsc(html: String): String {
        val pushRegex = Regex("""self\.__next_f\.push\(\[1,"((?:[^"\\]|\\.)*)"\]\)""")
        return pushRegex.findAll(html).joinToString("") { match ->
            val escaped = match.groupValues[1]
            try {
                json.decodeFromString<String>("\"$escaped\"")
            } catch (_: Exception) {
                escaped
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\/", "/")
            }
        }
    }

    /** Formats a chapter number into "الفصل N" (or "الفصل N.N"). */
    private fun formatChapterName(number: Double): String {
        val intPart = number.toInt()
        return if (number == intPart.toDouble()) {
            "الفصل $intPart"
        } else {
            "الفصل $number"
        }
    }

    /** Extracts the chapter number from a name like "الفصل 12.5". */
    private fun chapterNumberOfName(name: String): Double {
        return Regex("[0-9]+(?:\\.[0-9]+)?")
            .find(name)?.value?.toDoubleOrNull() ?: 0.0
    }
}