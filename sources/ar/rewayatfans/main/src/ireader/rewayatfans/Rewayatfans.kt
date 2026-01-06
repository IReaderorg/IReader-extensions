package ireader.rewayatfans

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class RewayatFans(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://rewayatfans.com"
    override val id: Long
        get() = 42
    override val name: String
        get() = "روايات فانز"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "الترتيب حسب:",
            arrayOf(
                "latest",
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "latest",
                endpoint = "/قائمة-الروايات/{page}/",
                selector = "wp-block-image a",
                nameSelector = "strong",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 5
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".has-tertiary-background-color font",
            coverSelector = ".size-full img",
            coverAtt = "src",
            descriptionSelector = "",  // Handled in getMangaDetails
        )

    // Custom description parsing: get <p> tags between two <h2> elements
    private fun parseDescriptionBetweenH2(document: Document): String {
        val entryContent = document.selectFirst("div.entry-content") ?: return ""
        val paragraphs = mutableListOf<String>()

        var foundFirstH2 = false
        for (element in entryContent.children()) {
            if (element.tagName() == "h2") {
                if (foundFirstH2) break  // Stop at second h2
                foundFirstH2 = true
                continue
            }
            if (foundFirstH2 && element.tagName() == "p") {
                val text = element.text().trim()
                if (text.isNotBlank()) {
                    paragraphs.add(text)
                }
            }
        }

        return paragraphs.joinToString("\n\n")
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        // Check for WebView HTML first
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        if (detailFetch != null && detailFetch.html.isNotBlank()) {
            val document = detailFetch.html.asJsoup()
            val baseInfo = detailParse(document, )
            val description = parseDescriptionBetweenH2(document)
            return baseInfo.copy(description = description)
        }

        val document = client.get(requestBuilder(manga.key)).asJsoup()
        val baseInfo = detailParse(document)
        val description = parseDescriptionBetweenH2(document)
        return baseInfo.copy(description = description)
    }


    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".has-huge-font-size a",
            nameSelector = "font",
            linkSelector = "a",
            linkAtt = "href"
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".entry-content .wp-block-spacer ~ p",
        )
}
