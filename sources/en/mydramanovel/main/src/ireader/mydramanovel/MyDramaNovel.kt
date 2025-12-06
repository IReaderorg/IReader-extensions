package ireader.mydramanovel

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import ireader.core.source.model.Listing
import ireader.core.source.model.Page
import tachiyomix.annotations.Extension

@Extension
abstract class MyDramaNovel(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://mydramanovel.com"
    override val id: Long get() = 93
    override val name: String get() = "MyDramaNovel"

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
                "Popular",
                endpoint = "/novels/",
                selector = ".wpb_wrapper a[href*='mydramanovel.com/']",
                nameSelector = "a",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 1,
                addBaseUrlToLink = false
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "meta[property='og:image']",
            coverAtt = "content",
            descriptionSelector = ".tdb-block-inner p",
            authorBookSelector = ".tdb-block-inner p:contains(Author)",
            categorySelector = ".tdb-entry-category",
            statusSelector = ".tdb-block-inner"
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".tdb-block-inner a[href*='chapter']",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false,
            addBaseUrlToLink = false
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".tdb-title-text",
            pageContentSelector = ".tdb_single_content .tdb-block-inner p"
        )

    // Override getMangaList to properly parse the novels page
    override suspend fun getMangaList(
        sort: Listing?,
        page: Int
    ): MangasPageInfo {
        return try {
            val response = client.get("$baseUrl/novels/") {
                headers {
                    append("Accept", "text/html")
                    append("User-Agent", getUserAgent())
                }
            }
            val html = response.bodyAsText()
            val doc = html.asJsoup()

            // Parse novel links from the novels page
            // Format: "Novel Name123" where 123 is chapter count
            val novelLinks = doc.select("a[href*='mydramanovel.com/']")
                .filter { element ->
                    val href = element.attr("href")
                    href.contains("mydramanovel.com/") &&
                    !href.contains("/novels/") &&
                    !href.contains("/about/") &&
                    !href.contains("/contact/") &&
                    !href.contains("/privacy-policy/") &&
                    !href.contains("/tag/") &&
                    !href.contains("/chapter") &&
                    !href.contains("#") &&
                    href.count { it == '/' } >= 3
                }
                .distinctBy { it.attr("href").removeSuffix("/") }

            val novels = novelLinks.mapNotNull { element ->
                val href = element.attr("href")
                val text = element.text().trim()
                
                // Extract name (remove trailing numbers which are chapter counts)
                val name = text.replace(Regex("\\d+$"), "").trim()
                
                if (name.isNotBlank() && href.isNotBlank()) {
                    MangaInfo(
                        key = href.removeSuffix("/"),
                        title = name,
                        cover = ""
                    )
                } else null
            }

            MangasPageInfo(novels, false)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(
        filters: FilterList,
        page: Int
    ): MangasPageInfo {
        val query = filters.filterIsInstance<Filter.Title>().firstOrNull()?.value ?: ""
        
        return try {
            val url = if (query.isNotBlank()) {
                "$baseUrl/?s=$query"
            } else {
                "$baseUrl/novels/"
            }

            val response = client.get(url) {
                headers {
                    append("Accept", "text/html")
                    append("User-Agent", getUserAgent())
                }
            }
            val html = response.bodyAsText()
            val doc = html.asJsoup()

            if (query.isNotBlank()) {
                // Search results
                val results = doc.select(".td-module-title a, .entry-title a")
                    .filter { element ->
                        val href = element.attr("href")
                        !href.contains("/chapter")
                    }
                    .distinctBy { it.attr("href").removeSuffix("/") }
                    .map { element ->
                        MangaInfo(
                            key = element.attr("href").removeSuffix("/"),
                            title = element.text().trim(),
                            cover = ""
                        )
                    }
                MangasPageInfo(results, false)
            } else {
                // Regular listing
                getMangaList(null, page)
            }
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }


    // Override getMangaDetails to properly parse novel details
    override suspend fun getMangaDetails(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): MangaInfo {
        return try {
            val response = client.get(manga.key) {
                headers {
                    append("Accept", "text/html")
                    append("User-Agent", getUserAgent())
                }
            }
            val html = response.bodyAsText()
            val doc = html.asJsoup()

            // Get title from h1
            val title = doc.selectFirst("h1")?.text()?.trim() ?: manga.title

            // Get cover from og:image meta tag
            val cover = doc.selectFirst("meta[property='og:image']")?.attr("content") ?: ""

            // Get description - look for "Story Synopsis" section
            val descriptionElements = doc.select(".tdb_single_content .tdb-block-inner p")
            val description = descriptionElements
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 20 }
                .take(5)
                .joinToString("\n\n")

            // Get author from the page content
            val authorMatch = Regex("Author:\\s*([^\\n]+)").find(html)
            val author = authorMatch?.groupValues?.get(1)?.trim() ?: ""

            MangaInfo(
                key = manga.key,
                title = title,
                cover = cover,
                description = description,
                author = author,
                status = MangaInfo.UNKNOWN
            )
        } catch (e: Exception) {
            manga
        }
    }

    // Override getChapterList to properly parse chapters
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return try {
            val response = client.get(manga.key) {
                headers {
                    append("Accept", "text/html")
                    append("User-Agent", getUserAgent())
                }
            }
            val html = response.bodyAsText()
            val doc = html.asJsoup()

            // Find all chapter links
            val chapterLinks = doc.select("a[href*='${manga.key.substringAfterLast("/")}']")
                .filter { element ->
                    val href = element.attr("href")
                    href.contains("chapter") || 
                    href.contains("prologue") ||
                    href.contains("vol") ||
                    href.contains("part")
                }
                .distinctBy { it.attr("href").removeSuffix("/") }

            chapterLinks.mapIndexed { index, element ->
                val href = element.attr("href")
                val name = element.text().trim().ifBlank { 
                    element.selectFirst("h3, h4, span")?.text()?.trim() ?: "Chapter ${index + 1}"
                }

                ChapterInfo(
                    key = href.removeSuffix("/"),
                    name = name,
                    number = (index + 1).toFloat()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Override getPageList to properly parse chapter content
    override suspend fun getPageList(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): List<Page> {
        return try {
            val response = client.get(chapter.key) {
                headers {
                    append("Accept", "text/html")
                    append("Referer", baseUrl)
                    append("User-Agent", getUserAgent())
                }
            }
            val html = response.bodyAsText()
            val doc = html.asJsoup()

            // Get content from the single content block
            val contentBlock = doc.selectFirst(".tdb_single_content .tdb-block-inner")
            
            val paragraphs = contentBlock?.select("p")
                ?.map { it.text().trim() }
                ?.filter { text ->
                    text.isNotBlank() &&
                    text.length > 2 &&
                    !text.contains("adsbygoogle", ignoreCase = true) &&
                    !text.contains("Advertisement", ignoreCase = true)
                }
                ?: emptyList()

            if (paragraphs.isNotEmpty()) {
                paragraphs.toPage()
            } else {
                listOf("Content not available").toPage()
            }
        } catch (e: Exception) {
            listOf("Error loading content: ${e.message}").toPage()
        }
    }

    override fun getUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
