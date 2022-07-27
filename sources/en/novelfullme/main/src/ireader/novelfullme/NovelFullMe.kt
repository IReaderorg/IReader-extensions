package ireader.novelfullme

import io.ktor.client.request.*
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class NovelFullMe(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://novelfull.me"
    override val id: Long
        get() = 34
    override val name: String
        get() = "NovelFull.me"

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
                endpoint = "/popular?page={page}",
                selector = ".book-item",
                nameSelector = ".title",
                coverSelector = "img",
                coverAtt = "data-src",
                onCover = { text, key ->
                    "https:$text"
                },
                linkSelector = ".title a",
                linkAtt = "href",
                addBaseUrlToLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?q={query}",
                selector = ".book-item",
                nameSelector = ".title",
                coverSelector = "img",
                coverAtt = "data-src",
                onCover = { text, key ->
                    "https:$text"
                },
                linkSelector = ".title a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                type = SourceFactory.Type.Search
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".name h1",
            coverSelector = ".img-cover img",
            coverAtt = "data-src",
            onCover = { text ->
                "https:$text"
            },
            authorBookSelector = "body > div.layout > div.main-container.book-details > div > div.row.no-gutters > div.col-lg-8 > div.book-info > div.detail > div.meta.box.mt-1.p-10 > p:nth-child(1) > a > span",
            categorySelector = "body > div.layout > div.main-container.book-details > div > div.row.no-gutters > div.col-lg-8 > div.book-info > div.detail > div.meta.box.mt-1.p-10 > p:nth-child(3)",
            descriptionSelector = "body > div.layout > div.main-container.book-details > div > div.row.no-gutters > div.col-lg-8 > div.mt-1 > div.section.box.mt-1.summary > div.section-body > p.content",
            statusSelector = "body > div.layout > div.main-container.book-details > div > div.row.no-gutters > div.col-lg-8 > div.book-info > div.detail > div.meta.box.mt-1.p-10 > p:nth-child(2) > a > span",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li",
            nameSelector = ".chapter-title",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true
            )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "#chapter__content > h1",
            pageContentSelector = ".chapter__content p",
        )


    override suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        val url = manga.key.replace(
            baseUrl,
            "https://novelfull.me/api/novels"
        ) + "/chapters?source=detail"
        return client.get(requestBuilder(url)).asJsoup()
    }




}