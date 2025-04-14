package ireader.bestlightnovel

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class BestLightNovel(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://bestlightnovel.com"
    override val id: Long
        get() = 89 // Unique ID
    override val name: String
        get() = "BestLightNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Type:",
            arrayOf(
                "Recently updated",
                "Newest",
                "Top view"
            )
        ),
        Filter.Select(
            "Status",
            arrayOf(
                "ALL",
                "Completed",
                "Ongoing"
            )
        ),
        Filter.Select(
            "Category",
            arrayOf(
                "ALL",
                "Action",
                "Adventure",
                "Animals",
                "Arts",
                "Biographies",
                "Business",
                "Chinese",
                "Comedy",
                "Computers",
                "Crafts, Hobbies",
                "Drama",
                "Education",
                "English",
                "Entertainment",
                "Fantasy",
                "Fiction",
                "Gender Bender",
                "Harem",
                "Historical",
                "History",
                "Home",
                "Horror",
                "Humor",
                "Investing",
                "Josei",
                "Korean",
                "Literature",
                "Lolicon",
                "Martial Arts",
                "Mature",
                "Mecha",
                "Memoirs",
                "Mystery",
                "Original",
                "Other Books",
                "Philosophy",
                "Photography",
                "Politics",
                "Professional",
                "Psychological",
                "Reference",
                "Reincarnation",
                "Religion",
                "Romance",
                "School Life",
                "School Stories",
                "Sci-Fi",
                "Seinen",
                "Short Stories",
                "Shotacon"
            )
        )
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    private val statusValues = arrayOf(
        "all",
        "completed",
        "ongoing"
    )

    private val typeValues = arrayOf(
        "latest",
        "newest",
        "topview"
    )

    private val categoryValues = arrayOf(
        "all",
        "1", // Action
        "2", // Adventure
        "65", // Animals
        "40", // Arts
        "41", // Biographies
        "42", // Business
        "3", // Chinese
        "4", // Comedy
        "43", // Computers
        "45", // Crafts, Hobbies
        "5", // Drama
        "46", // Education
        "6", // English
        "47", // Entertainment
        "7", // Fantasy
        "48", // Fiction
        "8", // Gender Bender
        "9", // Harem
        "10", // Historical
        "49", // History
        "50", // Home
        "11", // Horror
        "51", // Humor
        "52", // Investing
        "12", // Josei
        "13", // Korean
        "53", // Literature
        "14", // Lolicon
        "15", // Martial Arts
        "16", // Mature
        "17", // Mecha
        "54", // Memoirs
        "18", // Mystery
        "19", // Original
        "66", // Other Books
        "55", // Philosophy
        "56", // Photography
        "57", // Politics
        "58", // Professional
        "20", // Psychological
        "59", // Reference
        "21", // Reincarnation
        "60", // Religion
        "22", // Romance
        "23", // School Life
        "67", // School Stories
        "24", // Sci-Fi
        "25", // Seinen
        "68", // Short Stories
        "26" // Shotacon
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Recently Updated",
                endpoint = "/novel_list?type=latest&category=all&state=all&page={page}",
                selector = ".update_item.list_category",
                nameSelector = "h3 > a",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Newest",
                endpoint = "/novel_list?type=newest&category=all&state=all&page={page}",
                selector = ".update_item.list_category",
                nameSelector = "h3 > a",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Top View",
                endpoint = "/novel_list?type=topview&category=all&state=all&page={page}",
                selector = ".update_item.list_category",
                nameSelector = "h3 > a",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 100
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search_novels/{query}?page={page}",
                selector = ".update_item.list_category",
                nameSelector = "h3 > a",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                type = SourceFactory.Type.Search,
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".truyen_info_right h1",
            coverSelector = ".info_image img",
            coverAtt = "src",
            authorBookSelector = "ul.truyen_info_right > li:contains(Author) a",
            categorySelector = "ul.truyen_info_right > li:contains(GENRES) a",
            descriptionSelector = "#noidungm",
            statusSelector = "ul.truyen_info_right > li:contains(STATUS) a",
            onStatus = { status ->
                when {
                    status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list div.row",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#vung_doc",
        )
} 