package ireader.novelonline

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class NovelOnline(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://novelsonline.net"
    override val id: Long
        get() = 88 // Choose a unique ID
    override val name: String
        get() = "NovelOnline"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort by:",
            arrayOf(
                "Top Rated",
                "Most Viewed",
            )
        ),
        Filter.Select(
            "Category",
            arrayOf(
                "None",
                "Action",
                "Adventure",
                "Celebrity",
                "Comedy",
                "Drama",
                "Ecchi",
                "Fantasy",
                "Gender Bender",
                "Harem",
                "Historical",
                "Horror",
                "Josei",
                "Martial Arts",
                "Mature",
                "Mecha",
                "Mystery",
                "Psychological",
                "Romance",
                "School Life",
                "Sci-fi",
                "Seinen",
                "Shotacon",
                "Shoujo",
                "Shoujo Ai",
                "Shounen",
                "Shounen Ai",
                "Slice of Life",
                "Sports",
                "Supernatural",
                "Tragedy",
                "Wuxia",
                "Xianxia",
                "Xuanhuan",
                "Yaoi",
                "Yuri",
            )
        ),
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    private val categoryValues = arrayOf(
        "",
        "action",
        "adventure",
        "celebrity",
        "comedy",
        "drama",
        "ecchi",
        "fantasy",
        "gender-bender",
        "harem",
        "historical",
        "horror",
        "josei",
        "martial-arts",
        "mature",
        "mecha",
        "mystery",
        "psychological",
        "romance",
        "school-life",
        "sci-fi",
        "seinen",
        "shotacon",
        "shoujo",
        "shoujo-ai",
        "shounen",
        "shounen-ai",
        "slice-of-life",
        "sports",
        "supernatural",
        "tragedy",
        "wuxia",
        "xianxia",
        "xuanhuan",
        "yaoi",
        "yuri",
    )

    private val sortValues = arrayOf(
        "top_rated",
        "view"
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Top Rated",
                endpoint = "/top-novel/{page}?change_type=top_rated",
                selector = ".top-novel-block",
                nameSelector = "h2",
                coverSelector = ".top-novel-cover img",
                coverAtt = "src",
                linkSelector = "h2 a",
                linkAtt = "href",
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Most Viewed",
                endpoint = "/top-novel/{page}?change_type=view",
                selector = ".top-novel-block",
                nameSelector = "h2",
                coverSelector = ".top-novel-cover img",
                coverAtt = "src",
                linkSelector = "h2 a",
                linkAtt = "href",
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/sResults.php",
                selector = "li",
                nameSelector = "a",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                type = SourceFactory.Type.Search,
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = ".novel-cover a > img",
            coverAtt = "src",
            authorBookSelector = ".novel-detail-item:contains(Author) .novel-detail-body li",
            categorySelector = ".novel-detail-item:contains(Genre) .novel-detail-body li",
            descriptionSelector = ".novel-detail-item:contains(Description) .novel-detail-body",
            statusSelector = ".novel-detail-item:contains(Status) .novel-detail-body",
            onStatus = { status ->
                when {
                    status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ul.chapter-chs > li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#contentall",
        )
} 