package ireader.skynovelmodel

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import ireader.core.source.SourceFactory
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import tachiyomix.annotations.Extension


abstract class SkyNovelModel(private val deps: Dependencies) : SourceFactory(
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

    open val mainEndpoint = "manga"
    fun fetcherCreator(name:String, endpoint:String) :BaseExploreFetcher{
        return BaseExploreFetcher(
            name,
            endpoint = "/$mainEndpoint/page/{page}/?m_orderby=$endpoint",
            selector = ".page-item-detail .item-thumb",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            coverSelector = "a img",
            coverAtt = "src",
            nextPageSelector = ".nav-previous",
            nextPageValue = "Older Posts"
        )
    }
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            fetcherCreator("Latest","latest"),
            fetcherCreator("Trending","trending"),
            fetcherCreator("New","new-manga"),
            fetcherCreator("Most Views","views"),
            fetcherCreator("Rating","rating"),
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

        )

    open val descriptionSelector = ".g_txt_over p"
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".post-title",
            coverSelector = ".summary_image a img",
            coverAtt = "src",
            authorBookSelector = ".author-content a",
            categorySelector = ".genres-content a",
            descriptionSelector = descriptionSelector,
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".wp-manga-chapter",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    open val contentSelector = ".text-left h3,p ,.cha-content .pr .dib p"

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".cha-tit",
            pageContentSelector = contentSelector,
        )

//    override suspend fun getChapterList(
//        manga: MangaInfo,
//        commands: List<Command<*>>
//    ): List<ChapterInfo> {
//        val html = client.get(requestBuilder(manga.key)).asJsoup()
//        val bookId = html.select(".rating-post-id").attr("value")
//
//        var chapters = chaptersParse(
//            client.submitForm(
//                url = "$baseUrl/wp-admin/admin-ajax.php",
//                formParameters = Parameters.build {
//                    append("action", "manga_get_chapters")
//                    append("manga", bookId)
//                }
//            ).asJsoup(),
//        )
//        return chapters.reversed()
//    }
}
