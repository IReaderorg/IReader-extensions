package ireader.novelbin

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

@Extension
@AutoSourceId(seed = "NovelBin")
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "cultivation",
    minSearchResults = 1
)
abstract class NovelBin(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://novelbin.com"
    override val id: Long get() = NovelBinSourceId.ID
    override val name: String get() = "NovelBin"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular", "Rating", "Name")),
        Filter.Select("Genre", arrayOf(
            "All", "Action", "Adult", "Adventure", "Anime & comics", "Comedy", "Drama",
            "Eastern", "Ecchi", "Fantasy", "Game", "Harem", "Historical", "Horror",
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
            val response = client.get(requestBuilder("$baseUrl/sort/latest?page=$page"))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)
            val mangaList = doc.select(".row").mapNotNull { item ->
                val titleEl = item.selectFirst("h3.novel-title a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                // URLs use /b/{slug} pattern
                val slug = href.substringAfter("/b/")
                if (slug.isBlank()) return@mapNotNull null
                // Cover uses data-src for lazy loading
                val cover = item.selectFirst("img.cover")?.attr("data-src")
                    ?: item.selectFirst("img.cover")?.attr("src") ?: ""
                MangaInfo(key = "$baseUrl/b/$slug", title = title, cover = cover)
            }
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
                val response = client.get(requestBuilder("$baseUrl/search?keyword=$searchQuery&page=$page"))
                val body = response.bodyAsText()
                val doc = Ksoup.parse(body)
                val mangaList = doc.select(".row").mapNotNull { item ->
                    val titleEl = item.selectFirst("h3.novel-title a") ?: return@mapNotNull null
                    val title = titleEl.text().trim()
                    val slug = titleEl.attr("href").substringAfter("/b/")
                    if (slug.isBlank()) return@mapNotNull null
                    val cover = item.selectFirst("img.cover")?.attr("data-src")
                        ?: item.selectFirst("img.cover")?.attr("src") ?: ""
                    MangaInfo(key = "$baseUrl/b/$slug", title = title, cover = cover)
                }
                MangasPageInfo(mangaList, mangaList.isNotEmpty())
            } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
        }

        val queryParams = mutableListOf("page=$page")
        sortFilter?.value?.index?.let { index ->
            when (index) {
                1 -> queryParams.add("sort=popular")
                2 -> queryParams.add("sort=rating")
                3 -> queryParams.add("sort=name")
            }
        }
        genreFilter?.value?.let { index ->
            if (index > 0) {
                val genres = listOf(
                    "action", "adult", "adventure", "anime-comics", "comedy", "drama",
                    "eastern", "ecchi", "fantasy", "game", "harem", "historical", "horror",
                    "isekai", "martial-arts", "mature", "mystery", "psychological", "romance",
                    "school-life", "sci-fi", "seinen", "slice-of-life", "supernatural",
                    "tragedy", "wuxia", "xianxia", "xuanhuan"
                )
                if (index <= genres.size) queryParams.add("genre=${genres[index - 1]}")
            }
        }

        return try {
            val url = "$baseUrl/sort/latest?${queryParams.joinToString("&")}"
            val response = client.get(requestBuilder(url))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)
            val mangaList = doc.select(".row").mapNotNull { item ->
                val titleEl = item.selectFirst("h3.novel-title a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val slug = titleEl.attr("href").substringAfter("/b/")
                if (slug.isBlank()) return@mapNotNull null
                val cover = item.selectFirst("img.cover")?.attr("data-src")
                    ?: item.selectFirst("img.cover")?.attr("src") ?: ""
                MangaInfo(key = "$baseUrl/b/$slug", title = title, cover = cover)
            }
            MangasPageInfo(mangaList, mangaList.isNotEmpty())
        } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetailsFromHtml(cmd.html, manga)
        }
        
        // Use browser for detail page (JS-heavy for descriptions)
        val html = try {
            val browserResult = deps.httpClients.browser.fetch(
                url = manga.key,
                selector = ".novel-description-block, #novel-description-content",
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
        val scrapedTitle = doc.selectFirst("h3.title, meta[property=og:title]")?.text()
        val title = if (!scrapedTitle.isNullOrBlank() && !scrapedTitle.contains("Loading", ignoreCase = true)) scrapedTitle else manga.title
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: manga.cover
        val description = doc.selectFirst(".novel-description-block, #novel-description-content, meta[property=og:description]")?.text() ?: ""
        val author = doc.selectFirst(".novel-info .author a, meta[property=og:novel:author]")?.text() ?: ""
        return manga.copy(title = title, cover = cover, description = description, author = author)
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val slug = manga.key.substringAfterLast("/b/")
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseChaptersFromHtml(cmd.html, slug)
        }
        return try {
            val response = client.get(requestBuilder(manga.key))
            val body = response.bodyAsText()
            parseChaptersFromHtml(body, slug)
        } catch (e: Exception) { emptyList() }
    }

    private fun parseChaptersFromHtml(html: String, slug: String): List<ChapterInfo> {
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()
        // NovelBin chapter links: /b/{slug}/chapter-{number}-{title}
        doc.select("#list-chapter a[href*='/chapter-'], .chapter-item a[href*='/chapter-']").forEach { link ->
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
        return try {
            val response = client.get(requestBuilder(chapter.key))
            val body = response.bodyAsText()
            parseContentFromHtml(body)
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
