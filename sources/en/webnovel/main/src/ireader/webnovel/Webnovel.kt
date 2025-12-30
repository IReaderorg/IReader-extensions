package ireader.webnovel

import io.ktor.client.request.get
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class Webnovel(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://www.webnovel.com"
    override val id: Long get() = 85
    override val name: String get() = "Webnovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort by",
            arrayOf("Popular", "Recommended", "Most Collections", "Rating", "Time Updated")
        ),
        Filter.Select(
            "Status",
            arrayOf("All", "Ongoing", "Completed")
        ),
        Filter.Select(
            "Gender",
            arrayOf("Male", "Female")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    private val sortValues = arrayOf("1", "2", "3", "4", "5")
    private val statusValues = arrayOf("0", "1", "2")

    private fun customRequestBuilder(url: String): HttpRequestBuilder {
        return requestBuilder(url).apply {
            headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        
        if (!query.isNullOrBlank()) {
            return searchNovels(query, page)
        }
        
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val selectFilters = filters.filterIsInstance<Filter.Select>()
        val statusIndex = selectFilters.getOrNull(0)?.value ?: 0
        val genderIndex = selectFilters.getOrNull(1)?.value ?: 0
        
        val orderBy = sortValues.getOrElse(sortIndex) { "1" }
        val status = statusValues.getOrElse(statusIndex) { "0" }
        val gender = if (genderIndex == 0) "1" else "2"
        
        val url = "$baseUrl/stories/novel?gender=$gender&bookStatus=$status&orderBy=$orderBy&pageIndex=$page"
        
        val document = client.get(customRequestBuilder(url)).asJsoup()
        return parseNovelList(document, true)
    }

    private suspend fun searchNovels(query: String, page: Int): MangasPageInfo {
        val url = "$baseUrl/search?keywords=${query.trim().replace(" ", "+")}&pageIndex=$page"
        val document = client.get(customRequestBuilder(url)).asJsoup()
        return parseNovelList(document, false)
    }

    private fun parseNovelList(document: Document, isCategory: Boolean): MangasPageInfo {
        val selector = if (isCategory) ".j_category_wrapper li" else ".j_list_container li"
        val coverAttr = if (isCategory) "data-original" else "src"
        
        val novels = document.select(selector).mapNotNull { element ->
            val thumb = element.select(".g_thumb")
            val name = thumb.attr("title").ifBlank { return@mapNotNull null }
            val href = thumb.attr("href").ifBlank { return@mapNotNull null }
            val cover = element.select(".g_thumb > img").attr(coverAttr)
            
            MangaInfo(
                key = href,
                title = name,
                cover = if (cover.startsWith("//")) "https:$cover" else cover
            )
        }
        
        val hasNext = document.select(".j_page_next").isNotEmpty() || novels.size >= 20
        return MangasPageInfo(novels, hasNext)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            val url = if (manga.key.startsWith("http")) manga.key else "$baseUrl${manga.key}"
            client.get(customRequestBuilder(url)).asJsoup()
        }
        
        val name = document.select(".g_thumb > img").attr("alt").ifBlank { manga.title }
        val cover = document.select(".g_thumb > img").attr("src").let {
            if (it.startsWith("//")) "https:$it" else it
        }.ifBlank { manga.cover }
        
        val genres = document.select(".det-hd-detail > .det-hd-tag").attr("title")
        val summary = document.select(".j_synopsis > p").text().trim()
        
        val author = document.select(".det-info .c_s").filter { 
            it.text().trim() == "Author:" 
        }.firstOrNull()?.nextElementSibling()?.text()?.trim() ?: ""
        
        val statusText = document.select(".det-hd-detail svg").filter {
            it.attr("title") == "Status"
        }.firstOrNull()?.nextElementSibling()?.text()?.trim() ?: ""
        
        val status = when {
            statusText.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
            statusText.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }
        
        return manga.copy(
            title = name,
            cover = cover,
            genres = genres.split(",").map { it.trim() }.filter { it.isNotBlank() },
            description = summary,
            author = author,
            status = status
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        
        val novelPath = if (manga.key.startsWith("/")) manga.key else "/${manga.key}"
        val catalogUrl = "$baseUrl$novelPath/catalog"
        
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(customRequestBuilder(catalogUrl)).asJsoup()
        }
        
        val chapters = mutableListOf<ChapterInfo>()
        
        document.select(".volume-item").forEach { volumeElement ->
            val volumeText = volumeElement.text().trim()
            val volumeMatch = Regex("Volume\\s(\\d+)").find(volumeText)
            val volumeName = volumeMatch?.let { "Volume ${it.groupValues[1]}" } ?: "Unknown Volume"
            
            volumeElement.select("li").forEach { chapterElement ->
                val link = chapterElement.select("a")
                val chapterTitle = link.attr("title")?.trim() ?: "No Title"
                val chapterPath = link.attr("href")
                val isLocked = chapterElement.select("svg").isNotEmpty()
                
                if (chapterPath.isNotBlank()) {
                    val name = "$volumeName: $chapterTitle" + if (isLocked) " 🔒" else ""
                    chapters.add(ChapterInfo(
                        name = name,
                        key = chapterPath,
                        number = chapters.size.toFloat(),
                        dateUpload = 0L
                    ))
                }
            }
        }
        
        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            val url = if (chapter.key.startsWith("http")) chapter.key else "$baseUrl${chapter.key}"
            client.get(customRequestBuilder(url)).asJsoup()
        }
        
        // Remove comment elements
        document.select(".para-comment").remove()
        
        val title = document.select(".cha-tit").html()
        val content = document.select(".cha-words").html()
        
        return listOf(Text("$title$content"))
    }

    override val exploreFetchers: List<BaseExploreFetcher> get() = emptyList()
    override val detailFetcher: Detail get() = SourceFactory.Detail()
    override val chapterFetcher: Chapters get() = SourceFactory.Chapters()
    override val contentFetcher: Content get() = SourceFactory.Content()
}
