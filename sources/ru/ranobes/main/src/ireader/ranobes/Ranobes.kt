package ireader.ranobes

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
@AutoSourceId(seed = "Ranobes")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "лабиринт",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://ranobes.com/ranobe/615982-the-labyrinth-of-the-monsters.html",
    chapterUrl = "https://ranobes.com/chapters/the-labyrinth-of-the-monsters/615983-1.html",
    expectedTitle = "Лабиринт монстров",
    expectedMinChapters = 10
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class Ranobes(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ru"
    override val baseUrl: String get() = "https://ranobes.com"
    override val id: Long get() = RanobesSourceId.ID
    override val name: String get() = "Ranobes"

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
            val url = if (page <= 1) "$baseUrl/ranobe/" else "$baseUrl/ranobe/page/$page/"
            val response = client.get(requestBuilder(url))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)
            val mangaList = doc.select("a[href*='/ranobe/']").mapNotNull { el ->
                val href = el.attr("href")
                val title = el.attr("title").trim().ifBlank { el.text().trim() }
                if (href.isBlank() || title.isBlank() || title == "Ранобэ") return@mapNotNull null
                if (!href.contains(".html")) return@mapNotNull null
                val cover = el.selectFirst("img")?.attr("src")?.let { src ->
                    if (src.startsWith("http")) src else "$baseUrl$src"
                } ?: ""
                MangaInfo(key = href, title = title, cover = cover)
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
                val response = client.get(requestBuilder("$baseUrl/index.php?do=search&subaction=search&search_start=0&full_search=1&result_from=1&story=$searchQuery"))
                val body = response.bodyAsText()
                val doc = Ksoup.parse(body)
                val mangaList = doc.select("a[href*='/ranobe/']").mapNotNull { el ->
                    val href = el.attr("href")
                    val title = el.attr("title").trim().ifBlank { el.text().trim() }
                    if (href.isBlank() || title.isBlank() || title == "Ранобэ") return@mapNotNull null
                    if (!href.contains(".html")) return@mapNotNull null
                    MangaInfo(key = href, title = title, cover = "")
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
            val response = client.get(requestBuilder(manga.key))
            response.bodyAsText()
        } catch (e: Exception) { "" }

        return parseDetailsFromHtml(html, manga)
    }

    private fun parseDetailsFromHtml(html: String, manga: MangaInfo): MangaInfo {
        val doc = Ksoup.parse(html)
        val scrapedTitle = doc.selectFirst("h1, .novel-title")?.text()
        val title = if (!scrapedTitle.isNullOrBlank()) scrapedTitle else manga.title
        val cover = doc.selectFirst(".novel-cover img, .full-text img, meta[property=og:image]")?.let { el ->
            val src = el.attr("src").ifBlank { el.attr("content") }
            if (src.startsWith("http")) src else "$baseUrl$src"
        } ?: manga.cover
        val description = doc.selectFirst(".cont-text, .description, .full-text")?.text() ?: ""
        val author = doc.selectFirst(".author, .novel-info .author")?.text() ?: ""
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
                    selector = "a[href*='/chapters/']",
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
        doc.select("a[href*='/chapters/']").forEach { link ->
            val href = link.attr("href")
            val linkText = link.text().trim()
            if (linkText.isBlank() || linkText.contains("Оглавление") || linkText.contains("Начать чтение")) return@forEach
            if (!href.contains(".html")) return@forEach
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
            val html = response.bodyAsText()
            parseContentFromHtml(html)
        } catch (e: Exception) {
            listOf(Text("Chapter content not available."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)
        val contentDiv = doc.selectFirst(".text, .chapter-content, .entry-content")
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
