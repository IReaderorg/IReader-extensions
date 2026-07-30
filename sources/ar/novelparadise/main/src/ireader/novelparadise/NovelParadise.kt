package ireader.novelparadise

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
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class NovelParadise(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://novelsparadise.site"
    override val id: Long
        get() = 50
    override val name: String
        get() = "NovelParadise"

    override fun getFilters(): FilterList = listOf(
        Filter.Title()
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    fun fetcherCreator(name: String, endpoint: String): BaseExploreFetcher {
        return BaseExploreFetcher(
            name,
            endpoint = "/series/?page={page}&status=&type=&order=$name",
            selector = ".maindet",
            nameSelector = ".mdinfo h2 a",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = ".mdinfo h2 a",
            linkAtt = "href",
            maxPage = 50,
        )
    }

    fun search(): BaseExploreFetcher {
        return BaseExploreFetcher(
            "Search",
            endpoint = "/page/{page}/?s={query}",
            selector = ".maindet",
            nameSelector = ".mdinfo h2 a",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = ".mdinfo h2 a",
            linkAtt = "href",
            maxPage = 50,
            type = SourceFactory.Type.Search
        )
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            fetcherCreator("Last Update", "update"),
            search()
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = ".sertothumb img",
            coverAtt = "src",
            authorBookSelector = ".serl:nth-child(4) .serval",
            categorySelector = ".sertogenre a",
            descriptionSelector = ".sersys p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".eplisterfull ul li",
            nameSelector = ".epl-num",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1.entry-title",
            pageContentSelector = ".entry-content",
        )

    // Override to bypass 403 protection using browser
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        // Check for pre-fetched HTML from WebView
        commands.filterIsInstance<Command.Content.Fetch>().firstOrNull()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                return pageContentParse(Ksoup.parse(cmd.html))
            }
        }

        return try {
            val html = try {
                val browserResult = deps.httpClients.browser.fetch(
                    url = chapter.key,
                    selector = ".entry-content, .reading-content, .chapter-content",
                    timeout = 30000
                )
                if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                    browserResult.responseBody
                } else {
                    val response = client.get(requestBuilder(chapter.key))
                    response.bodyAsText()
                }
            } catch (e: Exception) {
                Log.error { "NovelParadise: Browser fetch failed: ${e.message}" }
                val response = client.get(requestBuilder(chapter.key))
                response.bodyAsText()
            }
            pageContentParse(Ksoup.parse(html))
        } catch (e: Exception) {
            Log.error { "NovelParadise: Error fetching content: ${e.message}" }
            listOf(Text("المحتوى غير متوفر. جرب فتح الفصل مرة أخرى."))
        }
    }

    override fun pageContentParse(document: Document): List<Page> {
        // Remove noise
        document.select("script, style, noscript, iframe, nav, footer, header, .sidebar, .comments, .ads, [class*=ad-], .announ").remove()

        val content = mutableListOf<String>()

        // Title
        val title = document.selectFirst("h1.entry-title, h1.chapter-heading")?.text()?.trim()
        if (!title.isNullOrBlank()) content.add(title)

        // Try paragraphs from content containers
        val selectors = listOf(
            ".entry-content p",
            ".chapter-content p",
            ".reading-content p",
            "#chapter-content p",
            "article .entry-content p",
        )
        for (selector in selectors) {
            val paragraphs = document.select(selector)
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 3 }
                .distinct()
            if (paragraphs.size >= 2) {
                content.addAll(paragraphs)
                return content.map { Text(it) }
            }
        }

        // Fallback: get all text from containers
        val containers = listOf(".entry-content", ".chapter-content", ".reading-content", "article .entry-content", "article")
        for (selector in containers) {
            val container = document.selectFirst(selector) ?: continue
            container.select("script, style, noscript, .ads").remove()
            val text = container.text().trim()
            if (text.isNotBlank() && text.length > 50) {
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.length > 3 }.distinct()
                if (lines.isNotEmpty()) {
                    content.addAll(lines)
                    return content.map { Text(it) }
                }
            }
        }

        if (content.isNotEmpty()) return content.map { Text(it) }
        return listOf(Text("جاري تحميل المحتوى... حاول مرة أخرى"))
    }
}
