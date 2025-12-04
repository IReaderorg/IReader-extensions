package ireader.novelfire

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

/**
 * 🔥 NovelFire Source - Declarative with SourceFactory
 * 
 * Uses @AutoSourceId for automatic ID generation.
 * Uses SourceFactory's declarative fetchers for minimal boilerplate.
 */
@Extension
@AutoSourceId(seed = "Novel Fire")
abstract class Novelfire(deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://novelfire.net"
    override val id: Long get() = 7165539527173321330L
    override val name: String get() = "Novel Fire"

    // ═══════════════════════════════════════════════════════════════
    // 🔍 FILTERS
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular")),
    )

    // ═══════════════════════════════════════════════════════════════
    // ⚡ COMMANDS
    // ═══════════════════════════════════════════════════════════════
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // ═══════════════════════════════════════════════════════════════
    // 📚 EXPLORE FETCHERS (Declarative)
    // ═══════════════════════════════════════════════════════════════
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/search-adv?ctgcon=and&totalchapter=0&ratcon=min&rating=0&status=-1&sort=rank-top&page={{page}}",
                selector = ".novel-item",
                nameSelector = "h4",
                linkSelector = ".novel-title a",
                linkAtt = "href",
                coverSelector = ".novel-cover img",
                coverAtt = "data-src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Latest",
                endpoint = "/search-adv?ctgcon=and&totalchapter=0&ratcon=min&rating=0&status=-1&sort=date&tagcon=and&page={{page}}",
                selector = ".novel-item",
                nameSelector = "h4",
                linkSelector = ".novel-title a",
                linkAtt = "href",
                coverSelector = ".novel-cover img",
                coverAtt = "data-src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?keyword={{query}}&page={{page}}",
                selector = ".novel-list.chapters .novel-item",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".novel-cover > img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            ),
        )

    // ═══════════════════════════════════════════════════════════════
    // 📖 DETAIL FETCHER (Declarative)
    // ═══════════════════════════════════════════════════════════════
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".novel-title",
            coverSelector = ".cover > img",
            coverAtt = "data-src",
            descriptionSelector = ".summary .content",
            authorBookSelector = ".author .property-item > span",
            categorySelector = ".categories .property-item"
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CHAPTER FETCHER (Declarative)
    // ═══════════════════════════════════════════════════════════════
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true
        )

    // ═══════════════════════════════════════════════════════════════
    // 📄 CONTENT FETCHER (Declarative)
    // ═══════════════════════════════════════════════════════════════
    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#content"
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CUSTOM: Paginated Chapter Fetching
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val novelPath = manga.key.removePrefix(baseUrl).removePrefix("/")

        // Get total chapters to calculate pages
        val doc = client.get(requestBuilder(manga.key)).asJsoup()
        val totalChaptersText = doc.selectFirst(".header-stats .icon-book-open")?.parent()?.text()?.trim() ?: "0"
        val totalChapters = totalChaptersText.replace("[^\\d]".toRegex(), "").toIntOrNull() ?: 0
        val pages = (totalChapters + 99) / 100

        val allChapters = mutableListOf<ChapterInfo>()

        for (page in 1..pages) {
            val url = "$baseUrl/$novelPath/chapters?page=$page"
            try {
                val response = client.get(requestBuilder(url))
                val body = response.bodyAsText()
                if (body.contains("You are being rate limited")) throw Exception("Rate limited")
                
                Ksoup.parse(body).select(".chapter-list li").forEach { ele ->
                    val chapterName = ele.select("a").attr("title").takeIf { it.isNotBlank() } ?: "No Title Found"
                    val chapterPath = ele.select("a").attr("href")
                    if (chapterPath.isBlank()) return@forEach

                    val fullPath = if (chapterPath.startsWith("http")) chapterPath else "$baseUrl$chapterPath"
                    allChapters.add(ChapterInfo(name = chapterName, key = fullPath))
                }
            } catch (e: Exception) {
                Log.error { "Error fetching chapters for page $page: ${e.message}" }
            }
        }
        return allChapters
    }

    // ═══════════════════════════════════════════════════════════════
    // 📄 CUSTOM: Content Parsing (Remove bloat)
    // ═══════════════════════════════════════════════════════════════
    override fun pageContentParse(document: Document): List<Page> {
        document.select(".box-ads, .box-notification").remove()
        document.select("*").forEach { if (it.tagName().startsWith("nf")) it.remove() }
        return document.select("#content p").map { Text(it.text()) }
    }
}
