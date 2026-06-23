package ireader.kolnovel

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
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations

@Extension
@AutoSourceId(seed = "KolNovel")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "مانهوا",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://kolnovel.com/series/48hours-a-day/",
    chapterUrl = "https://kolnovel.com/shaag2448hours-a-dayz435ggye-100093/",
    expectedTitle = "48 ساعة باليوم",
    expectedMinChapters = 100
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false
)
abstract class KolNovel(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://kolnovel.com"
    override val id: Long get() = KolNovelSourceId.ID
    override val name: String get() = "KolNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular")),
        Filter.Select("Type", arrayOf("All", "Japanese", "Korean", "English", "Chinese")),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/series/?order=update&page=$page"))
            val body = response.bodyAsText()
            parseMangaList(body)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val sortFilter = filters.findInstance<Filter.Sort>()

        val searchQuery = titleFilter?.value?.takeIf { it.isNotBlank() }
        if (searchQuery != null) {
            return try {
                val response = client.get(requestBuilder("$baseUrl/?s=$searchQuery"))
                val body = response.bodyAsText()
                parseMangaList(body)
            } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
        }

        val sortParam = when (sortFilter?.value?.index) {
            1 -> "order=popular"
            else -> "order=update"
        }

        return try {
            val response = client.get(requestBuilder("$baseUrl/series/?$sortParam&page=$page"))
            val body = response.bodyAsText()
            parseMangaList(body)
        } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
    }

    private fun parseMangaList(body: String): MangasPageInfo {
        val doc = Ksoup.parse(body)
        val mangaList = mutableListOf<MangaInfo>()

        doc.select("article.maindet").forEach { article ->
            val titleEl = article.selectFirst("h2[itemprop=headline] a, .mdinfo h2 a")
            if (titleEl != null) {
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                if (href.isNotBlank() && title.isNotBlank()) {
                    val slug = href.substringAfter("/series/").substringBefore("/").substringBefore("?")
                    if (slug.isNotBlank()) {
                        val cover = article.selectFirst(".mdthumb img")?.attr("src") ?: ""
                        mangaList.add(MangaInfo(key = "$baseUrl/series/$slug/", title = title, cover = cover))
                    }
                }
            }
        }

        if (mangaList.isEmpty()) {
            doc.select(".post-title a, .novel-title a, h3 a").mapNotNull { el ->
                val title = el.text().trim()
                val href = el.attr("href")
                if (href.isBlank() || title.isBlank()) return@mapNotNull null
                val slug = href.substringAfter("/series/").substringBefore("/").substringBefore("?")
                if (slug.isBlank()) return@mapNotNull null
                val cover = el.closest(".post, .novel-item, .card")?.selectFirst("img")?.attr("src") ?: ""
                MangaInfo(key = "$baseUrl/series/$slug/", title = title, cover = cover)
            }.distinctBy { it.key }.forEach { mangaList.add(it) }
        }

        return MangasPageInfo(mangaList.distinctBy { it.key }, mangaList.isNotEmpty())
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetailsFromHtml(cmd.html, manga)
        }

        val html = try {
            val response = client.get(requestBuilder(manga.key))
            response.bodyAsText()
        } catch (e: Exception) { return manga }

        return parseDetailsFromHtml(html, manga)
    }

    private fun parseDetailsFromHtml(html: String, manga: MangaInfo): MangaInfo {
        val doc = Ksoup.parse(html)
        val scrapedTitle = doc.selectFirst(".post-title h1, h1")?.text()
        val title = if (!scrapedTitle.isNullOrBlank() && !scrapedTitle.contains("Loading", ignoreCase = true)) scrapedTitle else manga.title
        val cover = doc.selectFirst(".summary_image img, meta[property=og:image]")?.attr("src") ?: manga.cover
        val description = doc.selectFirst(".summary__content, .description-summary")?.text() ?: ""
        val author = doc.selectFirst(".author a, .summary-content .author")?.text() ?: ""
        val genres = doc.select(".genres-content a, .wp-manga-genres a").map { it.text().trim() }
        val status = doc.selectFirst(".post-status .summary-content")?.text()?.trim() ?: ""
        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = genres,
            status = when {
                status.contains("Completed", ignoreCase = true) || status.contains("مكتمل", ignoreCase = true) -> MangaInfo.COMPLETED
                status.contains("Ongoing", ignoreCase = true) || status.contains("مستمر", ignoreCase = true) -> MangaInfo.ONGOING
                status.contains("Hiatus", ignoreCase = true) || status.contains("متوقف", ignoreCase = true) -> MangaInfo.ON_HIATUS
                else -> MangaInfo.UNKNOWN
            }
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseChaptersFromHtml(cmd.html)
        }

        return try {
            val response = client.get(requestBuilder(manga.key))
            val body = response.bodyAsText()
            parseChaptersFromHtml(body)
        } catch (e: Exception) { emptyList() }
    }

    private fun parseChaptersFromHtml(html: String): List<ChapterInfo> {
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()

        doc.select("li[data-ID] a[href*='/shaag'], li[data-ID] a[href*='/chapter/']").forEach { link ->
            val href = link.attr("href")
            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            if (chapters.any { it.key == fullUrl }) return@forEach

            val eplNum = link.selectFirst(".epl-num")?.text()?.trim() ?: ""
            val eplTitle = link.selectFirst(".epl-title")?.text()?.trim() ?: ""
            val name = if (eplNum.isNotBlank() && eplTitle.isNotBlank()) "$eplNum $eplTitle"
                else if (eplTitle.isNotBlank()) eplTitle
                else link.text().trim()

            if (name.isBlank()) return@forEach
            val number = parseChapterNumber("$eplNum $eplTitle")
            chapters.add(ChapterInfo(name = name, key = fullUrl, number = number))
        }

        if (chapters.isEmpty()) {
            doc.select("a[href*='/shaag'], a[href*='/chapter/']").forEach { link ->
                val href = link.attr("href")
                val linkText = link.text().trim()
                if (linkText.isBlank() || linkText.contains("Start Reading", ignoreCase = true)) return@forEach
                val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                if (chapters.any { it.key == fullUrl }) return@forEach
                val number = parseChapterNumber(linkText)
                chapters.add(ChapterInfo(name = linkText, key = fullUrl, number = number))
            }
        }

        return chapters.reversed()
    }

    private fun parseChapterNumber(name: String): Float {
        val patterns = listOf(
            Regex("""الفصل\s+(\d+(?:\.\d+)?)"""),
            Regex("""فصل\s+(\d+(?:\.\d+)?)"""),
            Regex("""Chapter\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(name)
            if (match != null) {
                return match.groupValues[1].toFloatOrNull() ?: -1f
            }
        }
        return -1f
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
            listOf(Text("محتوى الفصل غير متاح."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)

        val contentDiv = doc.selectFirst(".reading-content, .text-left, .chapter-content, .entry-content")
        if (contentDiv != null) {
            val paragraphs = contentDiv.select("p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 1 }
                .filter { !it.startsWith("PDF") && !it.startsWith("اخبار") }
                .filter { !it.contains("&tsp;", ignoreCase = true) && !it.contains("ادعم الرواية", ignoreCase = true) }
                .filter { it != "\u00a0" && it != "&nbsp;" }
            if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }

            val text = contentDiv.text()
            if (text.isNotBlank()) {
                return text.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length > 5 }
                    .filter { it != "\u00a0" }
                    .map { Text(it) }
            }
        }

        val paragraphs = doc.select(".reading-content p, .chapter-content p, .entry-content p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && it.length > 5 && it != "\u00a0" }
        if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }

        val bodyText = doc.body()?.text() ?: ""
        return bodyText.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 10 }
            .map { Text(it) }
    }
}
