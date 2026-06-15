package ireader.novebo

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
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.Ksoup
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestFixture
import tachiyomix.annotations.TestExpectations

@Extension
@AutoSourceId(seed = "Novebo")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "ezeli",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://novebo.com/book/ezeli-tutanak",
    chapterUrl = "https://novebo.com/chapter/ezeli-tutanak/denizkizi/bolum-1",
    expectedTitle = "Ezeli Tutanak",
    expectedMinChapters = 10
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class Novebo(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "tu"
    override val baseUrl: String get() = "https://novebo.com"
    override val id: Long get() = NoveboSourceId.ID
    override val name: String get() = "Novebo"

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
            val response = client.get(requestBuilder("$baseUrl/all-books?page=$page"))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)
            val mangaList = doc.select("a[href*='/book/']").mapNotNull { el ->
                val title = el.attr("title").trim().ifBlank { el.text().trim() }
                val href = el.attr("href")
                if (href.isBlank() || title.isBlank()) return@mapNotNull null
                val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                val cover = el.selectFirst("img")?.attr("src")?.let { src ->
                    if (src.startsWith("http")) src else "$baseUrl$src"
                } ?: ""
                MangaInfo(key = fullUrl, title = title, cover = cover)
            }.distinctBy { it.key }
            MangasPageInfo(mangaList, mangaList.isNotEmpty())
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val searchQuery = titleFilter?.value?.takeIf { it.isNotBlank() }
        if (searchQuery != null) {
            return try {
                val response = client.get(requestBuilder("$baseUrl/search?result=$searchQuery"))
                val body = response.bodyAsText()
                val doc = Ksoup.parse(body)
                val mangaList = doc.select("a[href*='/book/']").mapNotNull { el ->
                    val title = el.attr("title").trim().ifBlank { el.text().trim() }
                    val href = el.attr("href")
                    if (href.isBlank() || title.isBlank()) return@mapNotNull null
                    val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                    val cover = el.selectFirst("img")?.attr("src")?.let { src ->
                        if (src.startsWith("http")) src else "$baseUrl$src"
                    } ?: ""
                    MangaInfo(key = fullUrl, title = title, cover = cover)
                }.distinctBy { it.key }
                MangasPageInfo(mangaList, mangaList.isNotEmpty())
            } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
        }

        return getMangaList(null, page)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetailsFromHtml(cmd.html, manga)
        }

        val html = try {
            val browserResult = deps.httpClients.browser.fetch(
                url = manga.key,
                selector = ".book-ycard, meta[name=description]",
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
        val scrapedTitle = doc.selectFirst("h1, .book-title")?.text()
        val title = if (!scrapedTitle.isNullOrBlank()) scrapedTitle else manga.title
        val cover = doc.selectFirst("img.uk-border-rounded")?.attr("src")?.let { src ->
            if (src.startsWith("http")) src else "$baseUrl$src"
        } ?: manga.cover
        val description = doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
        val author = doc.selectFirst(".author, .book-author")?.text() ?: ""
        return manga.copy(title = title, cover = cover, description = description, author = author)
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseChaptersFromHtml(cmd.html)
        }

        return try {
            val html = try {
                val browserResult = deps.httpClients.browser.fetch(
                    url = manga.key,
                    selector = "a[href*='/chapter/']",
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
            parseChaptersFromHtml(html)
        } catch (e: Exception) { emptyList() }
    }

    private fun parseChaptersFromHtml(html: String): List<ChapterInfo> {
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()
        doc.select("a[href*='/chapter/']").forEach { link ->
            val href = link.attr("href")
            val linkText = link.text().trim()
            if (linkText.isBlank() || linkText.contains("Okumaya Başla", ignoreCase = true)) return@forEach
            if (href.contains("bolum-").not()) return@forEach
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
            val html = try {
                val browserResult = deps.httpClients.browser.fetch(
                    url = chapter.key,
                    selector = ".ec-content, .chapter-content",
                    timeout = 30000
                )
                if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                    browserResult.responseBody
                } else {
                    val response = client.get(requestBuilder(chapter.key))
                    response.bodyAsText()
                }
            } catch (e: Exception) {
                val response = client.get(requestBuilder(chapter.key))
                response.bodyAsText()
            }
            parseContentFromHtml(html)
        } catch (e: Exception) {
            listOf(Text("Chapter content not available."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)
        val contentDiv = doc.selectFirst(".ec-content, .chapter-content, .text-content")
        if (contentDiv != null) {
            val paragraphs = contentDiv.select("p").map { it.text() }.filter { it.isNotBlank() }
            if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }
            val text = contentDiv.text()
            if (text.isNotBlank()) return text.split("\n").filter { it.isNotBlank() }.map { Text(it) }
        }
        val bodyText = doc.body()?.text() ?: ""
        return bodyText.split("\n").filter { it.isNotBlank() }.map { Text(it) }
    }
}
