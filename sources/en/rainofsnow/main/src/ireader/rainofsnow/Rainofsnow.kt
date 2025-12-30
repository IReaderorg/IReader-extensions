package ireader.rainofsnow

import io.ktor.client.request.get
import io.ktor.client.request.headers
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangasPageInfo
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class Rainofsnow(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://rainofsnow.com"
    override val id: Long get() = 78
    override val name: String get() = "Rainofsnow"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Select(
            "Genre",
            arrayOf(
                "All", "Action", "Adventure", "Angst", "Chinese", "Comedy",
                "Drama", "Fantasy", "Japanese", "Korean", "Mature", "Mystery",
                "Original Novel", "Psychological", "Romance", "Sci-fi",
                "Slice of Life", "Supernatural", "Tragedy"
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    private val genreValues = arrayOf(
        "", "?n_orderby=16", "?n_orderby=11", "?n_orderby=776", "?n_orderby=342",
        "?n_orderby=13", "?n_orderby=3", "?n_orderby=7", "?n_orderby=343",
        "?n_orderby=341", "?n_orderby=778", "?n_orderby=12", "?n_orderby=339",
        "?n_orderby=769", "?n_orderby=5", "?n_orderby=14", "?n_orderby=779",
        "?n_orderby=780", "?n_orderby=777"
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novels/page/{page}/",
                selector = ".minbox",
                nameSelector = "h3",
                linkSelector = "h3 > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/?s={query}",
                selector = ".minbox",
                nameSelector = "h3",
                linkSelector = "h3 > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "data-src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".text h2",
            coverSelector = ".imagboca1 img",
            coverAtt = "data-src",
            descriptionSelector = "#synop",
            authorBookSelector = "span:contains(Author) + a",
            categorySelector = "span:contains(Genre) + a",
            addBaseurlToCoverLink = false
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#chapter .march1 li",
            nameSelector = ".chapter",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = false,
            reverseChapterList = false
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".content > h2",
            pageContentSelector = ".content"
        )
}
