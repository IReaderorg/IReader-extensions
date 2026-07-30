package ireader.kolnovel

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

@Extension
@AutoSourceId(seed = "KolNovel")
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

    private fun parseNovelList(doc: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val mangaList = doc.select("article.maindet .mdinfo h2 a").mapNotNull { el ->
            val title = el.text().trim()
            val href = el.attr("href")
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            val slug = href.substringAfter("/series/").substringBefore("/").substringBefore("?")
            if (slug.isBlank()) return@mapNotNull null
            val article = el.closest("article.maindet")
            val cover = article?.selectFirst(".mdthumb img")?.attr("src") ?: ""
            MangaInfo(key = "$baseUrl/series/$slug/", title = title, cover = cover)
        }.distinctBy { it.key }
        return MangasPageInfo(mangaList, mangaList.isNotEmpty())
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/series/?order=update&page=$page"))
            val doc = Ksoup.parse(response.bodyAsText())
            parseNovelList(doc)
        } catch (e: Exception) {
            Log.error { "KolNovel: Error fetching manga list: ${e.message}" }
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
                val doc = Ksoup.parse(response.bodyAsText())
                val mangaList = doc.select("article.maindet .mdinfo h2 a, .bsx a").mapNotNull { el ->
                    val title = el.text().trim()
                    val href = el.attr("href")
                    if (href.isBlank() || title.isBlank()) return@mapNotNull null
                    val slug = href.substringAfter("/series/").substringBefore("/").substringBefore("?")
                    if (slug.isBlank()) return@mapNotNull null
                    MangaInfo(key = "$baseUrl/series/$slug/", title = title, cover = "")
                }.distinctBy { it.key }
                MangasPageInfo(mangaList, mangaList.isNotEmpty())
            } catch (e: Exception) { MangasPageInfo(emptyList(), false) }
        }

        val sortPath = sortFilter?.value?.index?.let {
            if (it == 1) "order=popular" else "order=update"
        } ?: "order=update"

        return try {
            val response = client.get(requestBuilder("$baseUrl/series/?$sortPath&page=$page"))
            val doc = Ksoup.parse(response.bodyAsText())
            parseNovelList(doc)
        } catch (e: Exception) {
            Log.error { "KolNovel: Error: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseDetailsFromHtml(cmd.html, manga)
        }
        val html = try {
            val browserResult = deps.httpClients.browser.fetch(
                url = manga.key,
                selector = "h1.entry-title, .sersys",
                timeout = 30000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) browserResult.responseBody
            else client.get(requestBuilder(manga.key)).bodyAsText()
        } catch (e: Exception) { client.get(requestBuilder(manga.key)).bodyAsText() }
        return parseDetailsFromHtml(html, manga)
    }

    private fun parseDetailsFromHtml(html: String, manga: MangaInfo): MangaInfo {
        val doc = Ksoup.parse(html)
        val scrapedTitle = doc.selectFirst("h1.entry-title, .post-title h1")?.text()
        val title = if (!scrapedTitle.isNullOrBlank() && !scrapedTitle.contains("Loading", true)) scrapedTitle else manga.title
        val cover = doc.selectFirst(".sertothumb img, meta[property=og:image]")?.attr("src") ?: manga.cover
        val description = doc.selectFirst(".sersys, .entry-content")?.text() ?: ""
        val author = doc.selectFirst(".serl:nth-child(4) .serval")?.text() ?: ""
        return manga.copy(title = title, cover = cover, description = description, author = author)
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseChaptersFromHtml(cmd.html)
        }
        return try {
            val response = client.get(requestBuilder(manga.key))
            parseChaptersFromHtml(response.bodyAsText())
        } catch (e: Exception) {
            Log.error { "KolNovel: Error fetching chapters: ${e.message}" }
            emptyList()
        }
    }

    private fun parseChaptersFromHtml(html: String): List<ChapterInfo> {
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()
        // KolNovel uses .eplister (not .eplisterfull)
        doc.select(".eplister ul li a").forEach { link ->
            val href = link.attr("href")
            val title = link.selectFirst(".epl-title")?.text()?.trim()
            val num = link.selectFirst(".epl-num")?.text()?.trim() ?: ""
            val name = if (title.isNullOrBlank()) num else "$num - $title"
            if (name.isBlank()) return@forEach
            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            chapters.add(ChapterInfo(name = name, key = fullUrl))
        }
        // Fallback
        if (chapters.isEmpty()) {
            doc.select("a[href*='/shaag'], a[href*='/chapter/']").forEach { link ->
                val href = link.attr("href")
                val name = link.text().trim()
                if (name.isBlank() || name.contains("Start Reading", true)) return@forEach
                val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                chapters.add(ChapterInfo(name = name, key = fullUrl))
            }
        }
        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseContentFromHtml(cmd.html)
        }
        return try {
            val html = try {
                val browserResult = deps.httpClients.browser.fetch(
                    url = chapter.key,
                    selector = ".entry-content, .reading-content",
                    timeout = 30000
                )
                if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) browserResult.responseBody
                else client.get(requestBuilder(chapter.key)).bodyAsText()
            } catch (e: Exception) { client.get(requestBuilder(chapter.key)).bodyAsText() }
            parseContentFromHtml(html)
        } catch (e: Exception) {
            Log.error { "KolNovel: Error fetching content: ${e.message}" }
            listOf(Text("المحتوى غير متوفر حالياً."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)
        doc.select("script, style, noscript, iframe, nav, footer, header, .sidebar, .comments, .navigation, .ads, [class*=ad-], .announ").remove()
        val contentDiv = doc.selectFirst(".entry-content, .reading-content, .chapter-content")
        if (contentDiv != null) {
            contentDiv.select("script, style, noscript, iframe, .code-block, .wp-block-spacer, .ads, [class*=ad-]").remove()
            val paragraphs = contentDiv.select("p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 3 }
            if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }
            val text = contentDiv.text().trim()
            if (text.isNotBlank()) {
                return text.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.length > 3 }.map { Text(it) }
            }
        }
        return listOf(Text("المحتوى غير متوفر حالياً."))
    }
}
