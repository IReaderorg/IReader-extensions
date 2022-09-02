package ireader.allnovelfull

import io.ktor.client.request.get
import org.ireader.core_api.source.Dependencies
import ireader.sourcefactory.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.Command
import org.ireader.core_api.source.model.CommandList
import org.ireader.core_api.source.model.Filter
import org.ireader.core_api.source.model.FilterList
import org.ireader.core_api.source.model.MangaInfo
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
            addBaseurlToCoverLink = true,
            coverAtt = "src",
            authorBookSelector = "div.col-xs-12.col-sm-4.col-md-4.info-holder > div.info > div:nth-child(1) > a",
            categorySelector = "div.col-xs-12.col-sm-4.col-md-4.info-holder > div.info > div:nth-child(2) > a",
            descriptionSelector = "div.desc-text",
            statusSelector = "div.info > div > h3",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "select > option",
            nameSelector = "option",
            linkSelector = "option",
            linkAtt = "value",
            reverseChapterList = true,
            addBaseUrlToLink = true

        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#chapter-content p",
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
