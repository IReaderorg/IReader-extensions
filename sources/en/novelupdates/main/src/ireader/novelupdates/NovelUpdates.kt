package ireader.novelupdates

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.*
import tachiyomix.annotations.Extension


@Extension
abstract class NovelUpdates(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.novelupdates.com"
    override val id: Long
        get() = 25
    override val name: String
        get() = "NovelUpdates"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),

        )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Content.Fetch(),
            Command.Chapter.Fetch()
        )
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Trending",
                endpoint = "/series-ranking/?rank=week&pg={page}",
                selector = "div.search_main_box_nu",
                nameSelector = ".search_title > a",
                linkSelector = ".search_title > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "div > div.l-content > div.digg_pagination > a.next_page",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}&post_type=seriesplans",
                selector = "div.search_main_box_nu",
                nameSelector = ".search_title > a",
                linkSelector = ".search_title > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "div > div.l-content > div.digg_pagination > a.next_page",
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".seriestitlenu",
            coverSelector = ".seriesimg > img",
            coverAtt = "src",
            authorBookSelector = "#showauthors #authtag",
            categorySelector = "#seriesgenre a",
            descriptionSelector = "#editdescription p",
            statusSelector = "#editstatus",
            onStatus = { status ->
                if (status.contains("Complete")) {
                    MangaInfo.COMPLETED
                } else {
                    MangaInfo.ONGOING
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li.sp_li_chp",
            nameSelector = "li",
            linkSelector = "a:nth-child(2)",
            linkAtt = "href",
            onLink = {
                "https:$it"
            }
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1,h2,h3,h4,h5,h6",
            pageContentSelector = "p",
        )

    override fun getUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36"
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val html = client.get(requestBuilder(manga.key)).asJsoup()
        val bookId = html.select("#mypostid").attr("value")

        val chapterHtml = client.submitForm(
            url = "https://www.novelupdates.com/wp-admin/admin-ajax.php",
            formParameters = Parameters.build {
                append("action", "nd_getchapters")
                append("mygrr", "0")
                append("mypostid", bookId)
            }) {
            headersBuilder()
        }.asJsoup()

        val chapters = chaptersParse(
            chapterHtml
        )

        return chapters.reversed()
    }


}