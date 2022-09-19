package ireader.riwyat

import io.ktor.client.request.post
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.Page
import ireader.sourcefactory.SourceFactory
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class Riwyat(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://riwyat.com"
    override val id: Long
        get() = 23
    override val name: String
        get() = "Riwyat"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novel/page/{page}",
                selector = ".page-item-detail",
                nameSelector = ".h5 > a",
                linkSelector = ".h5 > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".wp-pagenavi .last",
                nextPageValue = "&raquo"
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

        )
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".manga-title h1",
            coverSelector = ".summary_image img",
            coverAtt = "src",
            descriptionSelector = "#tab-manga-about",
            statusSelector = ".genres",
            onStatus = { status ->
                if (status.contains("مستمرة")) {
                    MangaInfo.ONGOING
                } else {
                    MangaInfo.COMPLETED
                }
            },
        )
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li.wp-manga-chapter a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1#chapter-heading",
            pageContentSelector = "p",
        )

    override fun pageContentParse(document: Document): List<Page> {
        val par = document.select(contentFetcher.pageContentSelector!!).eachText().dropLast(15)
        val head = selectorReturnerStringType(
            document,
            selector = contentFetcher.pageTitleSelector,
            contentFetcher.pageTitleAtt
        )

        return listOf(head.toPage()) + par.map { it.toPage() }
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if (commands.isEmpty()) {
            return chaptersParse(
                client.post(requestBuilder(manga.key + "ajax/chapters/")).asJsoup(),
            ).reversed()
        }
        return super.getChapterList(manga, commands)
    }
}
