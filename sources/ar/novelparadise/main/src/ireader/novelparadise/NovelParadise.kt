package ireader.novelparadise

import com.fleeksoft.ksoup.Ksoup
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
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension

@Extension
@AutoSourceId(seed = "NovelParadise")
abstract class NovelParadise(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://novelsparadise.site"
    override val id: Long
        get() = NovelParadiseSourceId.ID
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

    fun fetcherCreator(name: String, order: String): BaseExploreFetcher {
        return BaseExploreFetcher(
            name,
            endpoint = "/series/?page={page}&status=&type=&order=$order",
            selector = ".maindet",
            nameSelector = ".mdinfo h2 a",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = ".mdinfo h2 a",
            linkAtt = "href",
            maxPage = 50,
        )
    }

    fun searchFetcher(): BaseExploreFetcher {
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
            searchFetcher()
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = ".sertothumb img",
            coverAtt = "src",
            authorBookSelector = ".serl:nth-child(3) .serval",
            categorySelector = ".sertogenre a",
            descriptionSelector = ".sersysn p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".eplisterfull li a",
            nameSelector = ".epl-num",
            linkSelector = "a",
            linkAtt = "href",
        )

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                val result = parseContentFromHtml(cmd.html)
                if (result.isNotEmpty()) return result
            }
        }

        val html = try {
            val browserResult = deps.httpClients.browser.fetch(
                url = chapter.key,
                selector = ".entry-content, .reading-content, .chapter-content, article",
                timeout = 60000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                browserResult.responseBody
            } else {
                null
            }
        } catch (_: Exception) { null }

        if (html != null) {
            val result = parseContentFromHtml(html)
            if (result.isNotEmpty()) return result
        }

        return try {
            val response = client.get(requestBuilder(chapter.key))
            val body = response.bodyAsText()
            val result = parseContentFromHtml(body)
            if (result.isNotEmpty()) return result

            listOf(Text("محتوى الفصل غير متاح. يرجى استخدام محرر المحتوى لتجاوز حماية Cloudflare."))
        } catch (_: Exception) {
            listOf(Text("محتوى الفصل غير متاح. يرجى استخدام محرر المحتوى لتجاوز حماية Cloudflare."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        if (html.contains("challenge-platform") || html.contains("cf_chl_opt") ||
            html.contains("Just a moment") || html.contains("Enable JavaScript and cookies")) {
            return emptyList()
        }

        val doc = Ksoup.parse(html)

        try {
            val contentDiv = doc.selectFirst(".entry-content")
            if (contentDiv != null) {
                val paragraphs = contentDiv.select("p")
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() && it.length > 1 }
                    .filter { !it.contains("successfully verified", ignoreCase = true) }
                    .filter { !it.contains("في انتظار الاستجابة", ignoreCase = true) }
                if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }
            }
        } catch (_: Exception) { }

        try {
            val contentDiv = doc.selectFirst(".reading-content, .chapter-content, .text-left")
            if (contentDiv != null) {
                val paragraphs = contentDiv.select("p")
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() && it.length > 1 }
                if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }
            }
        } catch (_: Exception) { }

        try {
            val allParagraphs = doc.select("article p, .entry-content p, .reading-content p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 10 }
                .filter { !it.contains("challenge-platform", ignoreCase = true) }
                .filter { !it.contains("Enable JavaScript", ignoreCase = true) }
            if (allParagraphs.isNotEmpty()) return allParagraphs.map { Text(it) }
        } catch (_: Exception) { }

        return emptyList()
    }
}
