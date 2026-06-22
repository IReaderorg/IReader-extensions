package ireader.rewayatfansnet

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.get
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import ireader.core.source.model.ChapterInfo
import tachiyomix.annotations.Extension

@Extension
abstract class RewayatFansNet(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://rewayahfans.net"
    override val id: Long
        get() = 4202
    override val name: String
        get() = "روايات فانز (نت)"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Novels",
                endpoint = "/",
                selector = ".wp-block-media-text",
                nameSelector = ".wp-block-media-text__content",
                linkSelector = ".wp-block-media-text__media a",
                linkAtt = "href",
                coverSelector = ".wp-block-media-text__media img",
                coverAtt = "src",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}",
                selector = "figure.wp-block-image",
                nameSelector = ".wp-element-caption a, .wp-element-caption strong a",
                linkSelector = ".wp-element-caption a, .wp-element-caption strong a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 5,
                type = Type.Search
            ),
        )

    override fun parseMangaFromElement(
        element: Element,
        fetcher: BaseExploreFetcher
    ): MangaInfo {
        val title = selectorReturnerStringType(element, fetcher.nameSelector, fetcher.nameAtt).trim()
        val url = selectorReturnerStringType(element, fetcher.linkSelector, fetcher.linkAtt).trim()
        val img = element.select(fetcher.coverSelector ?: "img").firstOrNull()
        val cover = img?.let {
            val src = it.attr("src").trim()
            val dataSrc = it.attr("data-src").trim()
            val dataLazySrc = it.attr("data-lazy-src").trim()
            when {
                dataSrc.isNotBlank() && !dataSrc.startsWith("data:") -> dataSrc
                dataLazySrc.isNotBlank() && !dataLazySrc.startsWith("data:") -> dataLazySrc
                src.isNotBlank() && !src.startsWith("data:") -> src
                else -> ""
            }
        } ?: ""
        return MangaInfo(key = url, title = title, cover = cover)
    }

    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = "figure.wp-block-image",
            coverSelector = "img",
            coverAtt = "src",
            descriptionSelector = "",
        )

    private fun parseDescriptionBetweenH2(document: Document): String {
        val entryContent = document.selectFirst("div.entry-content") ?: return ""
        val paragraphs = mutableListOf<String>()
        var collecting = false
        for (element in entryContent.children()) {
            if (element.hasClass("has-large-font-size")) {
                collecting = true
                continue
            }
            if (element.hasClass("crowdsignal-vote-wrapper")) break
            if (collecting && element.tagName() == "p") {
                val text = element.text().trim()
                if (text.isNotBlank()) paragraphs.add(text)
            }
        }
        return paragraphs.joinToString("\n\n")
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        if (detailFetch != null && detailFetch.html.isNotBlank()) {
            val document = detailFetch.html.asJsoup()
            val baseInfo = detailParse(document)
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
        val selector = chapterFetcher.selector ?: return emptyList()
        return document.select(selector).mapNotNull { element ->
            runCatching { chapterFromElement(element) }
                .getOrNull()
                ?.takeIf { it.isValid() }
        }
    }
}
