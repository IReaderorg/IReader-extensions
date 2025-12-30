package ireader.testserver

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.*

/**
 * A test source that connects to the local mock server.
 * This source demonstrates how to implement all the required methods
 * and can be used to test the test server UI without external dependencies.
 */
abstract class MockNovelSource(deps: Dependencies) : SourceFactory(deps = deps) {
    
    override val lang: String get() = "en"
    override val baseUrl: String get() = "http://localhost:8080"
    override val id: Long get() = 999999999L
    override val name: String get() = "Mock Novel Site"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular", "Rating")),
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
                endpoint = "/mock/explore?page={page}",
                selector = ".novel-item",
                nameSelector = ".novel-title",
                linkSelector = ".novel-link",
                linkAtt = "href",
                coverSelector = ".novel-cover",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false, // Cover URLs are already absolute
                maxPage = 3
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/mock/search?q={query}&page={page}",
                selector = ".search-item",
                nameSelector = ".novel-title",
                linkSelector = ".novel-link",
                linkAtt = "href",
                coverSelector = ".novel-cover",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".novel-title",
            coverSelector = ".novel-cover",
            coverAtt = "src",
            descriptionSelector = ".description-content",
            authorBookSelector = ".author-name",
            categorySelector = ".genre-tag",
            statusSelector = ".novel-status",
            addBaseurlToCoverLink = false,
            onStatus = { status ->
                when {
                    status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    status.contains("Hiatus", ignoreCase = true) -> MangaInfo.ON_HIATUS
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-item",
            nameSelector = ".chapter-title",
            linkSelector = ".chapter-link",
            linkAtt = "href",
            numberSelector = ".chapter-number",
            uploadDateSelector = ".chapter-date",
            addBaseUrlToLink = true,
            reverseChapterList = false // Chapters are already in correct order
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title",
            pageContentSelector = ".chapter-content p"
        )
}

