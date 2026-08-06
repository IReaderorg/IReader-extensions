package ireader.rewayatfans

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
abstract class RewayatFans(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://rewayatfans.com"
    override val id: Long
        get() = 4203
    override val name: String
        get() = "روايات فانز"

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
                endpoint = "{page}",
                selector = ".entry-content > figure.wp-block-image",
                nameSelector = "figcaption strong a, figcaption a",
                linkSelector = "figcaption strong a, figcaption a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                onPage = { page -> if (page == "1") "/" else "/page/$page/" },
                maxPage = 10,
            ),
            BaseExploreFetcher(
                "جميع الروايات",
                endpoint = "/%d9%82%d8%a7%d8%a6%d9%85%d8%a9-%d8%a7%d9%84%d8%b1%d9%88%d8%a7%d9%8a%d8%a7%d8%aa{page}/",
                selector = ".entry-content figure.wp-block-image",
                nameSelector = "figcaption strong a, figcaption a",
                linkSelector = "figcaption strong a, figcaption a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                onPage = { page -> if (page == "1") "" else "/$page" },
                maxPage = 6,
            ),
            BaseExploreFetcher(
                "الروايات المكتملة",
                endpoint = "/%d8%a7%d9%84%d8%b1%d9%88%d8%a7%d9%8a%d8%a7%d8%aa-%d8%a7%d9%84%d9%85%d9%83%d8%aa%d9%85%d9%84%d8%a9/",
                selector = ".entry-content > figure.wp-block-image",
                nameSelector = "figcaption strong a, figcaption a",
                linkSelector = "figcaption strong a, figcaption a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 1,
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}",
                selector = ".entry-content > figure.wp-block-image",
                nameSelector = "figcaption strong a, figcaption a",
                linkSelector = "figcaption strong a, figcaption a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 5,
                type = SourceFactory.Type.Search
            ),
        )

    private fun normalizeCover(raw: String): String {
        if (raw.isBlank()) return raw
        val marker = "rewayatfans.com/wp-content/"
        val idx = raw.substringBefore('?').indexOf(marker)
        return if (idx >= 0) "https://" + raw.substringBefore('?').substring(idx) else raw
    }

    override fun parseMangaFromElement(
        element: Element,
        fetcher: BaseExploreFetcher
    ): MangaInfo {
        val title = selectorReturnerStringType(element, fetcher.nameSelector, fetcher.nameAtt).trim()
        val url = selectorReturnerStringType(element, fetcher.linkSelector, fetcher.linkAtt).trim()
        val img = element.select("img").firstOrNull()
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
            normalizeCover(raw)
        } ?: ""
        return MangaInfo(key = url, title = title, cover = cover)
    }

    override fun detailParse(document: Document): MangaInfo {
        val manga = super.detailParse(document)
        return if (manga.cover.isBlank()) manga else manga.copy(cover = normalizeCover(manga.cover))
    }

    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = "h1",
            coverSelector = ".entry-content > figure.wp-block-image img, .entry-content > img",
            coverAtt = "src",
            descriptionSelector = "",
        )

    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = ".has-huge-font-size a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = Content(
            pageContentSelector = ".entry-content p",
        )

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        if (detailFetch != null && detailFetch.html.isNotBlank()) {
            val document = detailFetch.html.asJsoup()
            return detailParse(document)
        }
        val document = client.get(requestBuilder(manga.key)).asJsoup()
        return detailParse(document)
    }

    override fun chaptersParse(document: Document): List<ChapterInfo> {
        val selector = chapterFetcher.selector ?: return emptyList()
        return document.select(selector).mapNotNull { element ->
            runCatching { chapterFromElement(element) }
                .getOrNull()
                ?.takeIf { it.isValid() }
        }
    }
}
