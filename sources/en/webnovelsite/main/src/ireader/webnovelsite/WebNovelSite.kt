package ireader.webnovelsite

import io.ktor.client.request.post
import ireader.sourcefactory.SourceFactory
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.Command
import org.ireader.core_api.source.model.CommandList
import org.ireader.core_api.source.model.Filter
import org.ireader.core_api.source.model.FilterList
import org.ireader.core_api.source.model.MangaInfo
import tachiyomix.annotations.Extension

@Extension
abstract class WebNovelSite(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://webnovel.site"
    override val id: Long
        get() = 44
    override val name: String
        get() = "Web Novel Site"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf(
                "Latest",
                "Popular",
                "New",
                "Most Views",
                "Rating",
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
                endpoint = "/novel-list/page/{page}/",
                selector = ".page-content-listing .c-image-hover a",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}&post_type=wp-manga",
                selector = ".c-tabs-item__content",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "Trending",
                endpoint = "/novel-list/page/{page}/?m_orderby=trending",
                selector = ".page-content-listing .c-image-hover a",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),
            BaseExploreFetcher(
                "New",
                endpoint = "/novel-list/page/{page}/?m_orderby=new-manga",
                selector = ".page-content-listing .c-image-hover a",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),
            BaseExploreFetcher(
                "Most Views",
                endpoint = "/novel-list/page/{page}/?m_orderby=views",
                selector = ".page-content-listing .c-image-hover a",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),
            BaseExploreFetcher(
                "Rating",
                endpoint = "/novel-list/page/{page}/?m_orderby=rating",
                selector = ".page-content-listing .c-image-hover a",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts"
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".post-title h1",
            coverSelector = ".summary_image a img",
            coverAtt = "src",
            descriptionSelector = ".description-summary p",
            onStatus = { status ->
                if (status.contains("Completed")) {
                    MangaInfo.COMPLETED
                } else {
                    MangaInfo.ONGOING
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li.wp-manga-chapter a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".cha-tit",
            pageContentSelector = "div.reading-content h3,div.reading-content p",
        )

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if (commands.isEmpty()) {
            return chaptersParse(
                client.post(requestBuilder(manga.key + "ajax/chapters/")).asJsoup(),
            ).reversed()
        }
        return super.getChapterList(manga, commands)
    }
}
