package ireader.kdtnovels

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangasPageInfo
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.Ksoup
import tachiyomix.annotations.Extension

@Extension
abstract class KDTNovels(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://kdtnovels.com"
    override val id: Long get() = 80
    override val name: String get() = "KDT Novels"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Order by",
            arrayOf("Views", "Latest")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        
        val orderBy = if (sortIndex == 1) "latest" else "views"
        
        val url = if (!query.isNullOrBlank()) {
            "$baseUrl/page/$page/?s=${query.trim().replace(" ", "+")}&post_type=wp-manga"
        } else {
            "$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=$orderBy"
        }
        
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: Document): MangasPageInfo {
        val novels = document.select("div.c-tabs-item__content").mapNotNull { element ->
            val coverImg = element.select("div.tab-thumb img").first()
            val cover = coverImg?.attr("data-src")?.ifBlank { coverImg.attr("src") } ?: ""
            
            val titleLink = element.select("div.post-title > h3 > a").first()
            val name = titleLink?.text()?.trim() ?: return@mapNotNull null
            val href = titleLink.attr("href")
            
            if (name.isNotBlank() && href.isNotBlank()) {
                MangaInfo(
                    key = href.removePrefix(baseUrl).trim('/'),
                    title = name,
                    cover = cover
                )
            } else null
        }
        
        val hasNext = document.select(".nav-previous").isNotEmpty()
        return MangasPageInfo(novels, hasNext)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        if (detailFetch != null && detailFetch.html.isNotBlank()) {
            return detailParse(manga, detailFetch.html.asJsoup())
        }
        
        val url = if (manga.key.startsWith("http")) manga.key else "$baseUrl/${manga.key}"
        val document = client.get(requestBuilder(url)).asJsoup()
        return detailParse(manga, document)
    }

    private fun detailParse(manga: MangaInfo, document: Document): MangaInfo {
        val title = document.select(".manga-title").text().trim().ifBlank { manga.title }
        val cover = document.select("div.summary_image img").let { 
            it.attr("data-src").ifBlank { it.attr("src") }
        }.ifBlank { manga.cover }
        
        val genres = document.select("div.genres-content a").map { it.text().trim() }
        val summary = document.select("div.manga-excerpt p").joinToString("\n\n") { it.text().trim() }
        val statusText = document.select("div.manga-status span:nth-child(2)").text().trim()
        
        val status = when {
            statusText.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
            statusText.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }
        
        return manga.copy(
            title = title,
            cover = cover,
            genres = genres,
            description = summary,
            status = status
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return chaptersParse(chapterFetch.html.asJsoup()).reversed()
        }
        
        val url = if (manga.key.startsWith("http")) manga.key else "$baseUrl/${manga.key}"
        val ajaxUrl = "$url/ajax/chapters/"
        
        val response = client.post(requestBuilder(ajaxUrl)).bodyAsText()
        val document = Ksoup.parse(response)
        
        return parseChapterList(document).reversed()
    }

    private fun parseChapterList(document: Document): List<ChapterInfo> {
        return document.select("li.free-chap").mapNotNull { element ->
            val link = element.select("a").first() ?: return@mapNotNull null
            val name = link.text().trim()
            val href = link.attr("href")
            val releaseDate = element.select("span.chapter-release-date").text().trim()
            
            if (name.isNotBlank() && href.isNotBlank()) {
                ChapterInfo(
                    name = name,
                    key = href.removePrefix(baseUrl).trim('/'),
                    dateUpload = 0L
                )
            } else null
        }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/page/{page}/?s&post_type=wp-manga&m_orderby=latest",
                selector = "div.c-tabs-item__content",
                nameSelector = "div.post-title > h3 > a",
                linkSelector = "div.post-title > h3 > a",
                linkAtt = "href",
                coverSelector = "div.tab-thumb img",
                coverAtt = "data-src",
                addBaseUrlToLink = false,
                addBaseurlToCoverLink = false
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}&post_type=wp-manga",
                selector = "div.c-tabs-item__content",
                nameSelector = "div.post-title > h3 > a",
                linkSelector = "div.post-title > h3 > a",
                linkAtt = "href",
                coverSelector = "div.tab-thumb img",
                coverAtt = "data-src",
                addBaseUrlToLink = false,
                addBaseurlToCoverLink = false,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".manga-title",
            coverSelector = "div.summary_image img",
            coverAtt = "data-src",
            descriptionSelector = "div.manga-excerpt p",
            categorySelector = "div.genres-content a",
            statusSelector = "div.manga-status span:nth-child(2)",
            addBaseurlToCoverLink = false,
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
            selector = "li.free-chap",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = false,
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.reading-content"
        )
}
