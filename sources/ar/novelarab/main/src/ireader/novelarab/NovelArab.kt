package ireader.novelarab

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
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
import ireader.core.source.model.ImageUrl
import ireader.core.source.model.Page
import ireader.core.source.model.PageUrl
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.Ksoup
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations

@Extension
@AutoSourceId(seed = "NovelArab")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "مانهوا",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://novelarab.com/manga/shadow-slave/",
    chapterUrl = "https://novelarab.com/manga/shadow-slave/%d8%a7%d9%84%d9%81%d8%b5%d9%84-1-%d8%a7%d9%84%d9%83%d8%a7%d8%a8%d9%88%d8%b3-%d9%8a%d8%a8%d8%af%d8%a3/",
    expectedTitle = "عبد الظل | Shadow slave",
    expectedMinChapters = 10
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class NovelArab(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://novelarab.com"
    override val id: Long get() = NovelArabSourceId.ID
    override val name: String get() = "NovelArab"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular")),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val sortPath = when (sort?.name) {
                "Popular" -> "ranking"
                else -> "new"
            }
            val response = client.get(requestBuilder("$baseUrl/$sortPath/page/$page"))
            val doc = Ksoup.parse(response.bodyAsText())
            MangasPageInfo(parseMangaList(doc), true)
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val searchQuery = filters.findInstance<Filter.Title>()?.value?.takeIf { it.isNotBlank() }
        if (searchQuery != null) {
            return try {
                val response = client.get(requestBuilder("$baseUrl/?s=$searchQuery&post_type=wp-manga"))
                val doc = Ksoup.parse(response.bodyAsText())
                MangasPageInfo(parseMangaList(doc), false)
            } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
        }

        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index
        val sortPath = when (sortIndex) {
            1 -> "ranking"
            else -> "new"
        }
        return try {
            val response = client.get(requestBuilder("$baseUrl/$sortPath/page/$page"))
            val doc = Ksoup.parse(response.bodyAsText())
            MangasPageInfo(parseMangaList(doc), true)
        } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
    }

    private fun parseMangaList(doc: com.fleeksoft.ksoup.nodes.Document): List<MangaInfo> {
        // Try page-item-detail (browse pages) first
        val browseItems = doc.select(".page-item-detail")
        if (browseItems.isNotEmpty()) {
            return browseItems.mapNotNull { item ->
                val titleEl = item.selectFirst(".post-title a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                if (href.isBlank() || title.isBlank()) return@mapNotNull null
                val cover = item.selectFirst(".item-thumb img")?.let { img ->
                    img.attr("src").ifBlank { img.attr("data-src") }
                } ?: ""
                MangaInfo(key = href, title = title, cover = cover)
            }.distinctBy { it.key }
        }
        // Fall back to c-tabs-item__content (search results)
        return doc.select(".c-tabs-item__content").mapNotNull { item ->
            // Find the first link with non-blank text
            val titleEl = item.select("a[href*='/manga/']").firstOrNull { it.text().trim().isNotBlank() }
                ?: return@mapNotNull null
            val title = titleEl.text().trim()
            val href = titleEl.attr("href")
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            val cover = item.selectFirst("img")?.let { img ->
                img.attr("src").ifBlank { img.attr("data-src") }
            } ?: ""
            MangaInfo(key = href, title = title, cover = cover)
        }.distinctBy { it.key }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetailsFromHtml(cmd.html, manga)
        }

        val html = try {
            val browserResult = deps.httpClients.browser.fetch(
                url = manga.key,
                selector = ".manga-title, .excerpt-content",
                timeout = 30000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                browserResult.responseBody
            } else {
                val response = client.get(requestBuilder(manga.key))
                response.bodyAsText()
            }
        } catch (e: Exception) {
            val response = client.get(requestBuilder(manga.key))
            response.bodyAsText()
        }

        return parseDetailsFromHtml(html, manga)
    }

    private fun parseDetailsFromHtml(html: String, manga: MangaInfo): MangaInfo {
        val doc = Ksoup.parse(html)
        val scrapedTitle = doc.selectFirst(".manga-title h2, .post-title h1")?.text()
        val title = if (!scrapedTitle.isNullOrBlank()) scrapedTitle else manga.title
        val cover = doc.selectFirst(".summary_image img")?.attr("src") ?: manga.cover
        val description = doc.selectFirst(".excerpt-content")?.let { el ->
            el.select("p").joinToString("\n") { it.text() }.trim()
        } ?: ""
        val author = doc.selectFirst(".manga-author")?.text()?.trim()?.ifBlank { "" } ?: ""
        val genres = doc.select(".genres-content a").map { it.text().trim() }
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

        return try {
            val browserResult = deps.httpClients.browser.fetch(
                url = manga.key,
                selector = ".wp-manga-chapter a, li.version-chap a, a.c-btn",
                timeout = 50000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                parseChaptersFromHtml(browserResult.responseBody)
            } else {
                val response = client.get(requestBuilder(manga.key))
                parseChaptersFromHtml(response.bodyAsText())
            }
        } catch (e: Exception) {
            try {
                val response = client.get(requestBuilder(manga.key))
                parseChaptersFromHtml(response.bodyAsText())
            } catch (e2: Exception) { emptyList() }
        }
    }

    private fun parseChaptersFromHtml(html: String): List<ChapterInfo> {
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()

        doc.select("a").forEach { link ->
            val href = link.attr("href")
            val linkText = link.text().trim()
            if (linkText.isBlank()) return@forEach
            if (!linkText.contains("الفصل") && !linkText.contains("chapter", ignoreCase = true)) return@forEach
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
            val browserResult = deps.httpClients.browser.fetch(
                url = chapter.key,
                selector = ".reading-content",
                timeout = 30000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                parseContentFromHtml(browserResult.responseBody)
            } else {
                val response = client.get(requestBuilder(chapter.key))
                parseContentFromHtml(response.bodyAsText())
            }
        } catch (e: Exception) {
            try {
                val response = client.get(requestBuilder(chapter.key))
                parseContentFromHtml(response.bodyAsText())
            } catch (e2: Exception) {
                listOf(Text("Chapter content not available."))
            }
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)
        val contentDiv = doc.selectFirst(".reading-content") ?: return emptyList()

        val images = contentDiv.select("img.wp-manga-chapter-img, img.wp-manga-reading-image")
        if (images.isNotEmpty()) {
            return images.mapNotNull { img ->
                val src = img.attr("src").trim()
                if (src.isNotBlank()) ImageUrl(src) else null
            }
        }

        val paragraphs = contentDiv.select("p").map { it.text() }.filter { it.isNotBlank() }
        if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }

        return emptyList()
    }
}
