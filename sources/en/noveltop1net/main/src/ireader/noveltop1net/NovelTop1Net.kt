package ireader.noveltop1net

import io.ktor.client.request.get
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class NovelTop1Net(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://novelbin.com"
    override val id: Long
        get() = 38
    override val name: String
        get() = "NovelBin"

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
                endpoint = "/sort/top-view-novel?page={page}",
                selector = "div[class=\"list list-novel col-xs-12\"] > div[class=\"row\"]",
                nameSelector = "h3[class=\"novel-title\"] > a",
                nameAtt = "title",
                coverSelector = "img[class=\"cover\"]",
                coverAtt = "src",
                onCover = { text, key ->
                    text.replace("/novel_200_89/", "/novel/")
                },
                linkSelector = "h3[class=\"novel-title\"] > a",
                linkAtt = "href",
                maxPage = 100,
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?keyword={query}",
                selector = "div[class=\"list list-novel col-xs-12\"] > div[class=\"row\"]",
                nameSelector = "h3[class=\"novel-title\"] > a",
                nameAtt = "title",
                coverSelector = "img[class=\"cover\"]",
                coverAtt = "src",
                onCover = { text, key ->
                    text.replace("/novel_200_89/", "/novel/")
                },
                linkSelector = "h3[class=\"novel-title\"] > a",
                linkAtt = "href",
                maxPage = 100,
                type = SourceFactory.Type.Search
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "meta[property=\"og:novel:novel_name\"]",
            nameAtt = "content",
            coverSelector = "meta[itemprop=\"image\"]",
            coverAtt = "content",
            descriptionSelector = ".desc-text",
            authorBookSelector = "meta[property=\"og:novel:author\"]",
            authorBookAtt = "content",
            categorySelector = "meta[property=\"og:novel:genre\"]",
            onCategory = {
                (it.firstOrNull() ?: "").split(",")
            },
            categoryAtt = "content",
            statusSelector = "meta[property=\"og:novel:status\"]",
            statusAtt = "content"
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div[class=\"panel-body\"] li > a",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#chr-content p",
        )

    override suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        val novelId = client.get(requestBuilder(manga.key)).asJsoup().select("div[data-novel-id]")
            .attr("data-novel-id")
        val url = "$baseUrl/ajax/chapter-archive?novelId=$novelId"
        return client.get(url).asJsoup()
    }
}
