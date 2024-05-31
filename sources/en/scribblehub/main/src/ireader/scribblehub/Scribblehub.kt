package ireader.scribblehub

import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import tachiyomix.annotations.Extension

@Extension
abstract class Scribblehub(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.scribblehub.com"
    override val id: Long
        get() = 30
    override val name: String
        get() = "ScribbleHub"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Popular", "Latest"))
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Content.Fetch(),
            Command.Chapter.Fetch(),
        )
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/series-ranking/?sort=1&order=4&pg={page}",
                selector = "div.search_main_box",
                nameSelector = "div.search_title > a",
                coverSelector = "div.search_img > img",
                coverAtt = "src",
                linkSelector = "div.search_title > a",
                nextPageSelector = "body",
                linkAtt = "href",
            ),
            BaseExploreFetcher(
                "Latest",
                endpoint = "/?pg={page}",
                selector = "tr.toc_w",
                nameSelector = "a.fp_title",
                nameAtt = "title",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a.fp_title",
                linkAtt = "href",
                nextPageSelector = "body"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/?s={query}&post_type=fictionposts",
                selector = "div.search_main_box",
                nameSelector = "div.search_title > a",
                coverSelector = "div.search_img > img",
                coverAtt = "src",
                linkSelector = "div.search_title > a",
                linkAtt = "href",
                nextPageSelector = "body",
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.fic_title",
            coverSelector = "div.fic_image > img",
            coverAtt = "src",
            descriptionSelector = "div.wi_fic_desc",
            authorBookSelector = "span.auth_name_fic",
            categorySelector = "span.wi_fic_genre span",
            statusSelector = "span.rnd_stats",
            onStatus = {
                when (it) {
                    "Ongoing" -> MangaInfo.ONGOING
                    else -> MangaInfo.COMPLETED
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ol.toc_ol li a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "div.chapter-title",
            pageContentSelector = "div.chp_raw p",
        )

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val bookId = manga.key.substringAfter("/series/").substringBefore("/")
        val chapters = chaptersParse(
            client.submitForm(
                url = "https://www.scribblehub.com/wp-admin/admin-ajax.php",
                formParameters = Parameters.build {
                    append("action", "wi_getreleases_pagination")
                    append("pagenum", "-1")
                    append("mypostid", bookId)
                }
            ).asJsoup(),
        )
        return chapters.reversed()
    }
}
