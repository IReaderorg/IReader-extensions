package ireader.rewayahfans

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.get
import io.ktor.http.decodeURLPart
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
abstract class RewayahFans(deps: Dependencies) : SourceFactory(
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
                "الأحدث",
                endpoint = "/",
                selector = "figure.wp-block-image, figure.wp-block-media-text__media",
                nameSelector = "figcaption a, figcaption strong a",
                linkSelector = "figcaption a, figcaption strong a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 1,
            ),
            BaseExploreFetcher(
                "جميع الروايات",
                endpoint = "/%d9%82%d8%a7%d8%a6%d9%85%d8%a9-%d8%a7%d9%84%d8%b1%d9%88%d8%a7%d9%8a%d8%a7%d8%aa/",
                selector = "figure.wp-block-image",
                nameSelector = "figcaption a, figcaption strong a",
                linkSelector = "figcaption a, figcaption strong a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 1,
            ),
            BaseExploreFetcher(
                "الروايات المكتملة",
                endpoint = "/%d8%a7%d9%84%d8%b1%d9%88%d8%a7%d9%8a%d8%a7%d8%aa-%d8%a7%d9%84%d9%85%d9%83%d8%aa%d9%85%d9%84%d8%a9/",
                selector = "figure.wp-block-image",
                nameSelector = "figcaption a, figcaption strong a",
                linkSelector = "figcaption a, figcaption strong a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 1,
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
        var title = selectorReturnerStringType(element, fetcher.nameSelector, fetcher.nameAtt).trim()
        var url = selectorReturnerStringType(element, fetcher.linkSelector, fetcher.linkAtt).trim()
        if (url.isBlank()) {
            url = element.select("a[href]").firstOrNull()?.attr("href")?.trim().orEmpty()
        }
        if (url.isBlank()) {
            url = element.select("img[data-permalink]").firstOrNull()?.attr("data-permalink")?.trim().orEmpty()
        }
        if (title.isBlank() && url.isNotBlank()) {
            val slug = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
            title = slug.replace("-", " ").decodeURLPart()
        }
        val img = element.select(fetcher.coverSelector ?: "img").firstOrNull()
        val cover = img?.let {
            val src = it.attr("src").trim()
            val dataSrc = it.attr("data-src").trim()
            val dataLazySrc = it.attr("data-lazy-src").trim()
            val raw = when {
                dataSrc.isNotBlank() && !dataSrc.startsWith("data:") -> dataSrc
                dataLazySrc.isNotBlank() && !dataLazySrc.startsWith("data:") -> dataLazySrc
                src.isNotBlank() && !src.startsWith("data:") -> src
                else -> ""
            }
            if (raw.isBlank()) "" else {
                val marker = "rewayahfans.net/wp-content/"
                val idx = raw.substringBefore('?').indexOf(marker)
                if (idx >= 0) "https://" + raw.substringBefore('?').substring(idx)
                else raw
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
