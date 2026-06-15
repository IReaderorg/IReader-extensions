package ireader.galaxynovels

import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "ملك",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://galaxynovels.com/novel/im-invincible-thanks-to-simulation/",
    chapterUrl = "https://galaxynovels.com/novel/im-invincible-thanks-to-simulation/chapter-1/%d8%a7%d9%84%d9%81%d8%b5%d9%84-1-%d8%a5%d9%8a%d9%82%d8%a7%d8%b8-%d9%86%d8%b8%d8%a7%d9%85-%d8%a7%d9%84%d9%85%d8%ad%d8%a7%d9%83%d8%a7%d8%a9/",
    expectedTitle = "أنا لا أقهر بفضل المحاكاة!",
    expectedAuthor = "المؤلف"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = true
)
abstract class GalaxyNovels(deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang: String = "ar"
    override val baseUrl: String = "https://galaxynovels.com"
    override val id: Long = 842746330
    override val name: String = "GalaxyNovels"

    override fun getFilters(): FilterList = listOf(Filter.Title())
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher> = listOf(
        BaseExploreFetcher(
            name = "المكتبة",
            endpoint = "/library/",
            selector = "article.wor-library-card",
            nameSelector = ".wor-library-card__title a",
            coverSelector = ".wor-library-card__cover",
            coverAtt = "src",
            addBaseurlToCoverLink = true,
            linkSelector = ".wor-library-card__title a",
            linkAtt = "href",
            addBaseUrlToLink = true,
        ),
        BaseExploreFetcher(
            name = "البحث",
            endpoint = "/?s={query}",
            selector = "article.wor-library-card",
            nameSelector = ".wor-library-card__title a",
            coverSelector = ".wor-library-card__cover",
            coverAtt = "src",
            addBaseurlToCoverLink = true,
            linkSelector = ".wor-library-card__title a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            type = SourceFactory.Type.Search
        ),
    )

    override val detailFetcher: Detail = SourceFactory.Detail(
        nameSelector = "h1.entry-title",
        coverSelector = ".wor-novel-header__cover img",
        coverAtt = "src",
        addBaseurlToCoverLink = true,
        authorBookSelector = ".wor-novel-author",
        descriptionSelector = ".wor-novel-desc",
        statusSelector = ".wor-novel-status",
        onStatus = { status ->
            val lowerStatus = status.lowercase()
            when {
                lowerStatus.contains("complete") || lowerStatus.contains("مكتملة") -> COMPLETED
                lowerStatus.contains("ongoing") || lowerStatus.contains("مستمرة") -> ONGOING
                else -> ONGOING
            }
        },
        categorySelector = ".wor-novel-genre",
    )

    override val chapterFetcher: Chapters = SourceFactory.Chapters(
        selector = ".wp-block-list li",
        nameSelector = "a",
        linkSelector = "a",
        linkAtt = "href",
        reverseChapterList = true,
        addBaseUrlToLink = true,
    )

    override val contentFetcher: Content = SourceFactory.Content(
        pageContentSelector = ".wor-post-content",
    )

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return chaptersParse(chapterFetch.html.asJsoup()).reversed()
        }
        return super.getChapterList(manga, commands)
    }
}
