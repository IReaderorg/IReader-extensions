package ireader.allnovelfull

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class AllNovelFull(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://allnovelfull.com"
    override val id: Long
        get() = 36
    override val name: String
        get() = "AllNovelFull"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
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
                "Trending",
                endpoint = "/most-popular?page={page}",
                selector = ".list-truyen .row",
                nameSelector = "h3.truyen-title > a",
                coverSelector = "img.cover",
                coverAtt = "src",
                addBaseurlToCoverLink = true,
                linkSelector = "h3.truyen-title > a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                maxPage = 61

            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?keyword={query}",
                selector = "div.col-truyen-main > div.list-truyen > .row",
                nameSelector = "h3.truyen-title > a",
                coverSelector = "img",
                coverAtt = "src",
                addBaseurlToCoverLink = true,
                linkSelector = "h3.truyen-title > a",
                linkAtt = "href",
                type = SourceFactory.Type.Search
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.book > img",
            nameAtt = "alt",
            coverSelector = "div.book > img",
            coverAtt = "src",
            authorBookSelector = "div.info > div > h3",
            categorySelector = "div.info > div",
            descriptionSelector = "div.desc-text",
            statusSelector = "div.info > div > h3",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "select > option",
            nameSelector = "option",
            linkSelector = "option",
            linkAtt = "value",

            )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title",
            pageTitleAtt = "title",
            pageContentSelector = "#chapter-content",
        )


    override suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        val novelId = client.get(requestBuilder(manga.key)).asJsoup().select("#rating").attr("data-novel-id")
        val url = "$baseUrl/ajax/chapter-option?novelId=$novelId"
        return client.get(requestBuilder(url)).asJsoup()
    }




}