package ireader.readnovelfull

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
import tachiyomix.annotations.UrlValidation
import tachiyomix.annotations.SelectorSnapshot

@Extension
@AutoSourceId(seed = "ReadNovelFull")
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "longevity",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://readnovelfull.com/my-longevity-simulation.html",
    chapterUrl = "https://readnovelfull.com/my-longevity-simulation/chapter-1-where-do-immortals-come-from.html",
    expectedTitle = "My Longevity Simulation",
    expectedAuthor = "Angry Squid",
    expectedMinChapters = 100
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 50,
    supportsPagination = true,
    requiresLogin = false,
    requiresJs = false
)
@UrlValidation(
    novelPattern = "^https://readnovelfull\\.com/[a-z0-9-]+\\.html$",
    chapterPattern = "^https://readnovelfull\\.com/[a-z0-9-]+/chapter-\\d+-.*\\.html$",
    coverPattern = "^https://img\\.readnovelfull\\.com/.*\\.(jpg|png|webp)$"
)
@SelectorSnapshot(name = "novelTitle", selector = ".novel-title a", pageType = "list", expectedMinCount = 1)
@SelectorSnapshot(name = "coverImage", selector = ".cover, img", pageType = "list", attribute = "src", expectedPattern = "^https://img\\.readnovelfull\\.com/")
@SelectorSnapshot(name = "chapterList", selector = "#list-chapter a[href*='/chapter-']", pageType = "chapters", expectedMinCount = 10)
@SelectorSnapshot(name = "chapterContent", selector = "#chr-content, .chr-c", pageType = "content", expectedMinLength = 100)
@SelectorSnapshot(name = "detailTitle", selector = "h3.title", pageType = "detail")
@SelectorSnapshot(name = "detailAuthor", selector = "[itemprop='author'] meta[itemprop='name']", pageType = "detail", attribute = "content")
@SelectorSnapshot(name = "detailCover", selector = ".book img", pageType = "detail", attribute = "src", expectedPattern = "^https://img\\.readnovelfull\\.com/")
@SelectorSnapshot(name = "detailDescription", selector = ".desc-text", pageType = "detail", expectedMinLength = 50)
abstract class ReadNovelFull(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://readnovelfull.com"
    override val id: Long get() = ReadNovelFullSourceId.ID
    override val name: String get() = "ReadNovelFull"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular", "Rating", "Name")),
        Filter.Select("Genre", arrayOf(
            "All", "Action", "Adult", "Adventure", "Comedy", "Drama", "Eastern",
            "Ecchi", "Fanfiction", "Fantasy", "Game", "Harem", "Historical", "Horror",
            "Isekai", "Martial arts", "Mature", "Mystery", "Psychological", "Romance",
            "School life", "Sci-fi", "Seinen", "Slice of life", "Supernatural",
            "Tragedy", "Wuxia", "Xianxia", "Xuanhuan"
        )),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/novel-list/latest-release-novel?page=$page"))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)
            val mangaList = doc.select(".novel-title").mapNotNull { el ->
                val titleEl = el.selectFirst("a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                val slug = href.removeSuffix(".html").substringAfterLast("/")
                // Cover is in sibling col-xs-3 div, use .cover class specifically
                val cover = el.closest(".row")?.selectFirst("img.cover")?.attr("src") ?: ""
                MangaInfo(key = "$baseUrl/$slug.html", title = title, cover = cover)
            }.distinctBy { it.key }
            MangasPageInfo(mangaList, mangaList.isNotEmpty())
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val genreFilter = filters.filterIsInstance<Filter.Select>().firstOrNull()

        val searchQuery = titleFilter?.value?.takeIf { it.isNotBlank() }
        if (searchQuery != null) {
            return try {
                val response = client.get(requestBuilder("$baseUrl/novel-list/search?keyword=$searchQuery&page=$page"))
                val body = response.bodyAsText()
                val doc = Ksoup.parse(body)
                val mangaList = doc.select(".novel-title").mapNotNull { el ->
                    val titleEl = el.selectFirst("a") ?: return@mapNotNull null
                    val title = titleEl.text().trim()
                    val href = titleEl.attr("href")
                    val slug = href.removeSuffix(".html").substringAfterLast("/")
                    val cover = el.closest(".row")?.selectFirst(".cover, img")?.attr("src") ?: ""
                    MangaInfo(key = "$baseUrl/$slug.html", title = title, cover = cover)
                }.distinctBy { it.key }
                MangasPageInfo(mangaList, mangaList.isNotEmpty())
            } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
        }

        val sortPath = sortFilter?.value?.index?.let { index ->
            when (index) {
                1 -> "hot-novel"
                2 -> "completed-novel"
                3 -> "most-popular-novel"
                else -> "latest-release-novel"
            }
        } ?: "latest-release-novel"

        val queryParams = mutableListOf("page=$page")
        genreFilter?.value?.let { index ->
            if (index > 0) {
                val genres = listOf(
                    "action", "adult", "adventure", "comedy", "drama", "eastern",
                    "ecchi", "fanfiction", "fantasy", "game", "harem", "historical", "horror",
                    "isekai", "martial-arts", "mature", "mystery", "psychological", "romance",
                    "school-life", "sci-fi", "seinen", "slice-of-life", "supernatural",
                    "tragedy", "wuxia", "xianxia", "xuanhuan"
                )
                if (index <= genres.size) queryParams.add("genre=${genres[index - 1]}")
            }
        }

        return try {
            val url = "$baseUrl/novel-list/$sortPath?${queryParams.joinToString("&")}"
            val response = client.get(requestBuilder(url))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)
            val mangaList = doc.select(".novel-title").mapNotNull { el ->
                val titleEl = el.selectFirst("a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                val slug = href.removeSuffix(".html").substringAfterLast("/")
                val cover = el.closest(".row")?.selectFirst(".cover, img")?.attr("src") ?: ""
                MangaInfo(key = "$baseUrl/$slug.html", title = title, cover = cover)
            }.distinctBy { it.key }
            MangasPageInfo(mangaList, mangaList.isNotEmpty())
        } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetailsFromHtml(cmd.html, manga)
        }

        // Use browser for detail page (JS-heavy)
        val html = try {
            val browserResult = deps.httpClients.browser.fetch(
                url = manga.key,
                selector = "h3.title",
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
        val scrapedTitle = doc.selectFirst("h3.title")?.text()
        val title = if (!scrapedTitle.isNullOrBlank() && !scrapedTitle.contains("Loading", ignoreCase = true)) scrapedTitle else manga.title
        val cover = doc.selectFirst(".book img")?.attr("src") ?: manga.cover
        val description = doc.selectFirst(".desc-text")?.text() ?: ""
        val author = doc.selectFirst("[itemprop='author'] meta[itemprop='name']")?.attr("content") ?: ""
        return manga.copy(title = title, cover = cover, description = description, author = author)
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val slug = manga.key.removeSuffix(".html").substringAfterLast("/")
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseChaptersFromHtml(cmd.html, slug)
        }

        return try {
            // Fetch detail page to get novelId
            val response = client.get(requestBuilder(manga.key))
            val body = response.bodyAsText()

            // novelId is in data-novel-id attribute: data-novel-id="2572"
            val novelId = Regex("data-novel-id=\"(\\d+)\"").find(body)?.groupValues?.get(1)
            println(novelId.toString())
            if (novelId != null) {
                // Simple HTTP request to AJAX endpoint
                val chapterResponse = client.get(requestBuilder("$baseUrl/ajax/chapter-archive?novelId=$novelId"))
                val chapterBody = chapterResponse.bodyAsText()
                parseChaptersFromHtml(chapterBody, slug)
            } else {
                parseChaptersFromHtml(body, slug)
            }
        } catch (e: Exception) {
            Log.error { "Error fetching chapters: ${e.message}" }
            emptyList()
        }
    }

    private fun parseChaptersFromHtml(html: String, slug: String): List<ChapterInfo> {
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()
        // ReadNovelFull chapters: ul.list-chapter li a[href*='/chapter-']
        doc.select("ul.list-chapter li a[href*='/chapter-']").forEach { link ->
            val href = link.attr("href")
            val linkText = link.selectFirst(".nchr-text")?.text()?.trim() ?: link.text().trim()
            if (linkText.isBlank() || linkText.contains("Start Reading", ignoreCase = true)) return@forEach
            val chapterMatch = Regex("/chapter-(\\d+)-").find(href)
            if (chapterMatch != null) {
                val chapterNumber = chapterMatch.groupValues[1].toIntOrNull() ?: return@forEach
                val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                chapters.add(ChapterInfo(name = linkText.ifBlank { "Chapter $chapterNumber" }, key = fullUrl))
            }
        }
        return chapters.distinctBy { it.key }.sortedBy {
            Regex("/chapter-(\\d+)-").find(it.key)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseContentFromHtml(cmd.html)
        }

        // Use browser for chapter content (JS-heavy)
        return try {
            val html = try {
                val browserResult = deps.httpClients.browser.fetch(
                    url = chapter.key,
                    selector = "#chr-content, .chr-c",
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
        val contentDiv = doc.selectFirst("#chr-content, .chr-c, .chapter-content, .content")
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
