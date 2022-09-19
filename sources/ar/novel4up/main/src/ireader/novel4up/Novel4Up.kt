package ireader.novel4up

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.sourcefactory.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class Novel4Up(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://novel4up.com"
    override val id: Long
        get() = 43
    override val name: String
        get() = "Novel4Up"

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

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novel/page/{page}/?m_orderby=latest",
                selector = ".page-item-detail .item-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".navigation-ajax"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}&post_type=wp-manga",
                selector = "div.tab-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".navigation-ajax",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "Trending",
                endpoint = "/novel/page/{page}/?m_orderby=trending",
                selector = ".page-item-detail .item-thumb",
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
                endpoint = "/novel/page/{page}/?m_orderby=new-manga",
                selector = ".page-item-detail .item-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".navigation-ajax"
            ),
            BaseExploreFetcher(
                "Most Views",
                endpoint = "/novel/page/{page}/?m_orderby=views",
                selector = ".page-item-detail .item-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".navigation-ajax"
            ),
            BaseExploreFetcher(
                "Rating",
                endpoint = "/novel/page/{page}/?m_orderby=rating",
                selector = ".page-item-detail .item-thumb",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "data-src",
                nextPageSelector = ".navigation-ajax"
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.post-title h1",
            coverSelector = ".summary_image img",
            coverAtt = "data-src",
            authorBookSelector = "div.author-content a",
            categorySelector = ".genres-content a",
            descriptionSelector = "div.description-summary div.summary__content p",
            statusSelector = "div.summary-content:contains(Completed), div.summary-content:contains(OnGoing)",
            onStatus = { status ->
                if (status.contains("OnGoing")) {
                    MangaInfo.ONGOING
                } else if (status.contains("Completed")) {
                    MangaInfo.COMPLETED
                } else {
                    MangaInfo.ON_HIATUS
                }
            },
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".wp-manga-chapter",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".cha-tit",
            pageContentSelector = ".text-left h3,p ,.cha-content .pr .dib p",
        )

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val html = client.get(requestBuilder(manga.key)).asJsoup()
        val bookId = html.select(".rating-post-id").attr("value")

        var chapters = chaptersParse(
            client.submitForm(
                url = "https://novel4up.com/wp-admin/admin-ajax.php",
                formParameters = Parameters.build {
                    append("action", "manga_get_chapters")
                    append("manga", bookId)
                }
            ).asJsoup(),
        )
        return chapters.reversed()
    }
}
