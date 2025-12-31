package ireader.dreambigtl

import io.ktor.client.request.get
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class DreamBigTL(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.dreambigtl.com"
    override val id: Long
        get() = 90 // Choose a unique ID
    override val name: String
        get() = "Dream Big Translations"

    override fun getFilters(): FilterList = listOf(
        Filter.Title()
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
                "New Novels",
                endpoint = "/search/label/New%20Novels",
                selector = ".blog-posts.index-post-wrap .blog-post.hentry.index-post",
                nameSelector = ".entry-title a",
                coverSelector = ".entry-image",
                coverAtt = "data-image",
                linkSelector = ".entry-title a",
                linkAtt = "href"
            ),
            BaseExploreFetcher(
                "Ongoing Novels",
                endpoint = "/search/label/Ongoing%20Novels",
                selector = ".blog-posts.index-post-wrap .blog-post.hentry.index-post",
                nameSelector = ".entry-title a",
                coverSelector = ".entry-image",
                coverAtt = "data-image",
                linkSelector = ".entry-title a",
                linkAtt = "href"
            ),
            BaseExploreFetcher(
                "Completed Novels",
                endpoint = "/search/label/Completed%20Novels",
                selector = ".blog-posts.index-post-wrap .blog-post.hentry.index-post",
                nameSelector = ".entry-title a",
                coverSelector = ".entry-image",
                coverAtt = "data-image",
                linkSelector = ".entry-title a",
                linkAtt = "href"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?q={query}",
                selector = ".blog-posts.index-post-wrap .blog-post.hentry.index-post",
                nameSelector = ".entry-title a",
                coverSelector = ".entry-image",
                coverAtt = "data-image",
                linkSelector = ".entry-title a",
                linkAtt = "href",
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title",
            coverSelector = ".post-body img",
            descriptionSelector = ".post-body p:first-child",
            statusSelector = ".post-body",
            onStatus = { status ->
                // Determine status based on URL path
                when {
                    status.contains("completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    else -> MangaInfo.ONGOING
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-panel ul li a",
            nameSelector = "",
            linkSelector = "",
            linkAtt = "href",
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1.entry-title",
            pageContentSelector = ".post-body"
        )
    
    /**
     * Custom chapter parsing to handle Free Tier panel and List of Chapters
     */
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        // Check for WebView HTML first
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromDocument(chapterFetch.html.asJsoup())
        }
        
        val document = client.get(requestBuilder(manga.key)).asJsoup()
        return parseChaptersFromDocument(document)
    }
    
    private fun parseChaptersFromDocument(document: Document): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        
        // Try to parse "Free Tier" chapters first
        document.select(".chapter-panel").forEach { panel ->
            val panelTitle = panel.selectFirst("summary")?.text()?.trim() ?: ""
            if (panelTitle == "Free Tier") {
                panel.select("ul li a").forEach { element ->
                    val name = element.text().trim()
                    val url = element.attr("href")
                    if (name.isNotBlank() && url.isNotBlank()) {
                        chapters.add(ChapterInfo(name = name, key = url))
                    }
                }
            }
        }
        
        // If no Free Tier chapters, try "List of Chapters"
        if (chapters.isEmpty()) {
            document.select("h2:contains(List of Chapters), span:contains(List of Chapters)").forEach { header ->
                val chapterList = header.nextElementSibling()
                if (chapterList?.tagName() == "ul") {
                    chapterList.select("li a").forEach { element ->
                        val name = element.text().trim()
                        val url = element.attr("href")
                        if (name.isNotBlank() && url.isNotBlank()) {
                            chapters.add(ChapterInfo(name = name, key = url))
                        }
                    }
                }
            }
        }
        
        // Fallback: try any chapter-panel ul li a
        if (chapters.isEmpty()) {
            document.select(".chapter-panel ul li a").forEach { element ->
                val name = element.text().trim()
                val url = element.attr("href")
                if (name.isNotBlank() && url.isNotBlank()) {
                    chapters.add(ChapterInfo(name = name, key = url))
                }
            }
        }
        
        return chapters.reversed()
    }
} 