package ireader.markazriwayat

import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "sword",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://markazriwayat.com/novel/زوجتي-هي-حاكمة-السيف/",
    chapterUrl = "https://markazriwayat.com/novel/زوجتي-هي-حاكمة-السيف/الفصل-1/",
    expectedTitle = "زوجتي هي حاكمة السيف",
    expectedAuthor = "لورد غامض"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false
)
abstract class MarkazRiwayat(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"

    override val baseUrl: String
        get() = "https://markazriwayat.com"

    override val id: Long
        get() = 842746329  // Unique ID for MarkazRiwayat

    override val name: String
        get() = "MarkazRiwayat"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Recently Added",
                endpoint = "/new/",
                selector = "a.lib-card",
                nameSelector = ".lib-card__title",
                coverSelector = ".lib-card__img img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a.lib-card",
                linkAtt = "href",
                addBaseUrlToLink = true,
            ),
            BaseExploreFetcher(
                "Library",
                endpoint = "/library/",
                selector = "a.lib-card",
                nameSelector = ".lib-card__title",
                coverSelector = ".lib-card__img img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a.lib-card",
                linkAtt = "href",
                addBaseUrlToLink = true,
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/?s={query}",
                selector = "a.lib-card",
                nameSelector = ".lib-card__title",
                coverSelector = ".lib-card__img img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a.lib-card",
                linkAtt = "href",
                addBaseUrlToLink = true,
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.manga-title",
            coverSelector = ".manga-cover-wrap img",
            coverAtt = "data-src",
            addBaseurlToCoverLink = true,
            authorBookSelector = ".manga-author",
            descriptionSelector = ".manga-summary",
            statusSelector = ".manga-status-pill",
            onStatus = { status ->
                val lowerStatus = status.lowercase()
                when {
                    lowerStatus.contains("complete") || lowerStatus.contains("مكتملة") -> COMPLETED
                    lowerStatus.contains("ongoing") || lowerStatus.contains("جارية") -> ONGOING
                    else -> ONGOING
                }
            },
            categorySelector = ".pill-list .pill",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".ch-row",
            nameSelector = ".ch-title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,  // Newest first, so reverse for reading order
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".reading-content .text-right p",
        )
}
