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

                ),
        detailFetcher = SourceFactory.Detail(
                nameSelector = ".post-title h1",
                coverSelector = ".summary_image a img",
                coverAtt = "src",
                descriptionSelector = ".description-summary p",
                status = mapOf(
                        "OnGoing" to MangaInfo.ONGOING,
                        "Completed" to MangaInfo.COMPLETED,
                )
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
                pageContentSelector = "div.reading-content h3,div.reading-content p",
        ),
) {

        override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
                if (commands.isEmpty()) {
                        return chaptersParse(
                                client.post(requestBuilder(manga.key + "ajax/chapters/")).asJsoup(),
                        ).reversed()
                }
                return super.getChapterList(manga, commands)
        }

        
}