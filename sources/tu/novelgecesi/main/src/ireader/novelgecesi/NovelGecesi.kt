package ireader.novelgecesi

import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class NovelGecesi(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "tu"
    override val baseUrl: String
        get() = "https://www.novelgecesi.com"
    override val id: Long
        get() = 91
    override val name: String
        get() = "Novel Gecesi"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sıralama:",
            arrayOf(
                "Popüler",
                "En Yeni",
                "En Eski",
                "Puan",
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
                "Popüler",
                endpoint = "/seriler?sort=popular&page={page}",
                selector = "div.series-card",
                nameSelector = "h3.series-title a",
                linkSelector = "div.series-image a",
                linkAtt = "href",
                coverSelector = "div.series-image img",
                coverAtt = "src",
                maxPage = 50,
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "En Yeni",
                endpoint = "/seriler?sort=newest&page={page}",
                selector = "div.series-card",
                nameSelector = "h3.series-title a",
                linkSelector = "div.series-image a",
                linkAtt = "href",
                coverSelector = "div.series-image img",
                coverAtt = "src",
                maxPage = 50,
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "Puan",
                endpoint = "/seriler?sort=rating&page={page}",
                selector = "div.series-card",
                nameSelector = "h3.series-title a",
                linkSelector = "div.series-image a",
                linkAtt = "href",
                coverSelector = "div.series-image img",
                coverAtt = "src",
                maxPage = 50,
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/?q={query}",
                selector = "div.series-card",
                nameSelector = "h3.series-title a",
                linkSelector = "div.series-image a",
                linkAtt = "href",
                coverSelector = "div.series-image img",
                coverAtt = "src",
                maxPage = 50,
                addBaseUrlToLink = true,
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.series-title",
            coverSelector = "div.series-cover img",
            coverAtt = "src",
            descriptionSelector = "#series-description",
            authorBookSelector = "span.series-author",
            categorySelector = "span.badge-secondary, a.genre-tag",
            statusSelector = "span.series-status",
            onStatus = { status ->
                when {
                    status.contains("Devam", ignoreCase = true) -> MangaInfo.ONGOING
                    status.contains("Tamamlandı", ignoreCase = true) -> MangaInfo.COMPLETED
                    status.contains("Askıda", ignoreCase = true) -> MangaInfo.ON_HIATUS
                    status.contains("Bırakıldı", ignoreCase = true) -> MangaInfo.CANCELLED
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.episode-item",
            nameSelector = "h3.episode-title",
            linkSelector = "a.episode-link",
            linkAtt = "href",
            reverseChapterList = false,
            addBaseUrlToLink = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1",
            pageContentSelector = "#novel-content .content-text",
            onContent = { contents ->
                contents.firstOrNull()?.let { text ->
                    text.split(Regex("(?<=[\\.!?])\\s*(?=[A-ZÇĞİÖŞÜ\\[])"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                } ?: emptyList()
            }
        )

    override fun getUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
