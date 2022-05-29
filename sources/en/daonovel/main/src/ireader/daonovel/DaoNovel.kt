package ireader.daonovel

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class DaoNovel(deps: Dependencies) : SourceFactory(
        lang = "en",
        baseUrl = "https://daonovel.com",
        id = 20,
        name = "Dao Novel",
        deps = deps,
        filterList = listOf(
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
        ),
        commandList = listOf(
                Command.Detail.Fetch(),
                Command.Content.Fetch(),
                Command.Chapter.Fetch(),
        ),
        exploreFetchers = listOf(
                BaseExploreFetcher(
                        "Latest",
                        endpoint = "/novel-list/page/{page}/?_x_tr_sl=auto&_x_tr_tl=fa&_x_tr_hl=en-US&_x_tr_pto=op,wapp",
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
                        endpoint = "/novel-list/page/{page}/?m_orderby=trending&_x_tr_sl=auto&_x_tr_tl=fa&_x_tr_hl=en-US&_x_tr_pto=op,wapp",
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
                        endpoint = "/novel-list/page/{page}/?m_orderby=new-manga&_x_tr_sl=auto&_x_tr_tl=fa&_x_tr_hl=en-US&_x_tr_pto=op,wapp",
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
                        endpoint = "/novel-list/page/{page}/?m_orderby=views&_x_tr_sl=auto&_x_tr_tl=fa&_x_tr_hl=en-US&_x_tr_pto=op,wapp",
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
                        endpoint = "/novel-list/page/{page}/?m_orderby=rating&_x_tr_sl=auto&_x_tr_tl=fa&_x_tr_hl=en-US&_x_tr_pto=op,wapp",
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

                ),
        detailFetcher = SourceFactory.Detail(
                nameSelector = ".post-title h1",
                coverSelector = ".summary_image a img",
                coverAtt = "src",
                descriptionSelector = ".description-summary p",
        ),
        chapterFetcher = SourceFactory.Chapters(
                selector = "li.wp-manga-chapter a",
                nameSelector = "a",
                linkSelector = "a",
                linkAtt = "href",
                reverseChapterList = true,
        ),
        contentFetcher = SourceFactory.Content(
                pageTitleSelector = ".cha-tit",
                pageContentSelector = ".text-left h3,p ,.cha-content .pr .dib p",
        ),
) {


        override val baseUrl: String
                get() = "https://daonovel-com.translate.goog"
        
}