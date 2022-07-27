package ireader.skynovel

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.*
import tachiyomix.annotations.Extension


@Extension
abstract class SkyNovel(private val deps: Dependencies) : SourceFactory(
        deps = deps,
) {

        override val lang: String
                get() = "en"
        override val baseUrl: String
                get() = "https://skynovel.org"
        override val id: Long
                get() = 19
        override val name: String
                get() = "SkyNovel"

        override fun getFilters(): FilterList = listOf(
                Filter.Title(),
                Filter.Sort(
                        "Sort By:", arrayOf(
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
                                endpoint = "/manga/page/{page}/?m_orderby=latest",
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
                                "Search",
                                endpoint = "/page/{page}/?s={query}&post_type=wp-manga",
                                selector = ".c-tabs-item .row",
                                nameSelector = "a",
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
                                endpoint = "/manga/page/{page}/?m_orderby=trending",
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
                                endpoint = "/manga/page/{page}/?m_orderby=new-manga",
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
                                "Most Views",
                                endpoint = "/manga/page/{page}/?m_orderby=views",
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
                                "Rating",
                                endpoint = "/manga/page/{page}/?m_orderby=rating",
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

                        )

        override val detailFetcher: Detail
                get() = SourceFactory.Detail(
                        nameSelector = ".post-title",
                        coverSelector = ".summary_image a img",
                        coverAtt = "src",
                        authorBookSelector = ".author-content a",
                        categorySelector = ".genres-content a",
                        descriptionSelector = ".g_txt_over p",
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
                        client.submitForm(url = "https://skynovel.org/wp-admin/admin-ajax.php", formParameters = Parameters.build {
                            append("action", "manga_get_chapters")
                            append("manga", bookId)
                        }).asJsoup(),
                )
        return chapters.reversed()
    }


}