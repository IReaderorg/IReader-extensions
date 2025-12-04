package ireader.novelbuddy

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import kotlinx.serialization.json.Json
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class NovelBuddy(private val deps: Dependencies) : SourceFactory(deps) {
    override val lang: String get() = "en"
    // override val baseUrl: String get() = "https://novelbuddy.io"
    override val baseUrl: String get() = "https://novelbuddy.com" //funciona el url cover
    override val id: Long get() = 1002L
    override val name: String get() = "NovelBuddy"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Order by:",
            arrayOf(
                "Views",
                "Updated At",
                "Created At",
                "Name",
                "Rating"
            )
        ),
        Filter.Select(
            "Status",
            arrayOf(
                "All",
                "Ongoing",
                "Completed"
            )
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val detailFetcher = Detail(
        nameSelector = ".name h1",
        coverSelector = ".img-cover img",
        coverAtt = "data-src",
        addBaseurlToCoverLink = false,
        descriptionSelector = ".section-body.summary .content",
        authorBookSelector = ".meta.box p:contains(Authors) a span",
        categorySelector = ".meta.box p:contains(Genres) a",
        statusSelector = ".meta.box p:contains(Status) a",
        onStatus = { status ->
            when {
                status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                status.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                else -> MangaInfo.UNKNOWN
            }
        }
    )

    override suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        // First, get the novel page to extract the bookId
        val novelDocument = client.get(requestBuilder(manga.key)).asJsoup()

        // Extract the bookId from the script
        val script = novelDocument.select("script").toString()
        val bookIdRegex = Regex("bookId = (\\d+);")
        val bookId = bookIdRegex.find(script)?.groupValues?.get(1) ?: ""

        if (bookId.isNotEmpty()) {
            // Fetch the chapters API endpoint
            val chapterListUrl = "$baseUrl/api/manga/$bookId/chapters?source=detail"
            val chapterDocument = client.get(requestBuilder(chapterListUrl)).asJsoup()
            return chapterDocument
        }

        return novelDocument
    }

    override val chapterFetcher = Chapters(
        selector = "li",
        nameSelector = ".chapter-title",
        linkSelector = "a",
        linkAtt = "href",
        addBaseUrlToLink = true,
        reverseChapterList = true,
        uploadDateSelector = ".chapter-update"
    )

    override val contentFetcher = Content(
        pageContentSelector = ".chapter__content",
        onContent = { content ->
            // Clean up the content by removing unnecessary elements
            content.filter { !it.contains("google_translate_element") && !it.contains("listen-chapter") }
        }
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                key = "Popular",
                endpoint = "/search?sort=views&page={page}",
                selector = ".book-item",
                nameSelector = ".title",
                coverSelector = "img",
                coverAtt = "data-src",
                onCover = { url, _ -> "https:$url" },
                linkSelector = ".title a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                maxPage = 100
            ),
            BaseExploreFetcher(
                key = "Latest",
                endpoint = "/search?sort=updated_at&page={page}",
                selector = ".book-item",
                nameSelector = ".title",
                coverSelector = "img",
                coverAtt = "data-src",
                onCover = { url, _ -> "https:$url" },
                linkSelector = ".title a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                maxPage = 100
            ),
            BaseExploreFetcher(
                key = "Search",
                endpoint = "/search?q={query}&page={page}",
                selector = ".book-item",
                nameSelector = ".title",
                coverSelector = "img",
                coverAtt = "data-src",
                onCover = { url, _ -> "https:$url" },
                linkSelector = ".title a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                type = SourceFactory.Type.Search
            )
        )
}
