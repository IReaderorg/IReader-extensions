package ireader.rewayatfansnet

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import ireader.core.source.dsl.filters
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Listing
import ireader.core.source.model.MangasPageInfo
import tachiyomix.annotations.Extension

@Extension
abstract class RewayatFans(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://rewayahfans.net"
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
                "Latest",
                endpoint = "/%d9%82%d8%a7%d8%a6%d9%85%d8%a9-%d8%a7%d9%84%d8%b1%d9%88%d8%a7%d9%8a%d8%a7%d8%aa/",
                selector = "figure.wp-block-image",
                nameSelector = ".wp-element-caption a",
                linkSelector = ".wp-element-caption a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 5,
                type = Type.Others
            ),
            BaseExploreFetcher(
                "Latest",
                endpoint = "/page/{page}/?s={query}",
                selector = "figure.wp-block-image",
                nameSelector = ".wp-element-caption a",
                linkSelector = ".wp-element-caption a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 5,
                type = Type.Search
            ),
        )
    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = "figure.wp-block-image ",
//            coverSelector = "img",
//            coverAtt = "src",
            descriptionSelector = "",  // Handled in getMangaDetails
        )

    // Custom description parsing: get <p> tags between .crowdsignal-vote-wrapper and .has-large-font-size
    private fun parseDescriptionBetweenH2(document: Document): String {
        val entryContent = document.selectFirst("div.entry-content") ?: return ""
        val paragraphs = mutableListOf<String>()

        var collecting = false
        for (element in entryContent.children()) {
            // Start collecting after .crowdsignal-vote-wrapper
            if (element.hasClass("has-large-font-size")) {
                collecting = true
                continue
            }
            // Stop collecting when hitting .has-large-font-size
            if (element.hasClass("crowdsignal-vote-wrapper")) {
                break
            }
            // Collect <p> tags while in the collecting zone
            if (collecting && element.tagName() == "p") {
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
        get() = Chapters(
            selector = ".has-medium-font-size a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = Content(
            pageContentSelector = ".entry-content .wp-block-spacer ~ p",
        )

    override fun chaptersParse(document: Document): List<ChapterInfo> {
        Log.error { document.html() }
        val selector = chapterFetcher.selector ?: return emptyList()

        return document.select(selector).mapNotNull { element ->
            runCatching { chapterFromElement(element) }
                .getOrNull()
                ?.takeIf { it.isValid() }
        }
    }
}
