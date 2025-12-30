package ireader.ao3

import io.ktor.client.request.get
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
abstract class ArchiveOfOurOwn(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://archiveofourown.org"
    override val id: Long get() = 81
    override val name: String get() = "Archive Of Our Own"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort by",
            arrayOf("Hits", "Kudos", "Comments", "Bookmarks", "Word Count", "Date Updated", "Date Posted")
        ),
        Filter.Select(
            "Completion",
            arrayOf("All works", "Complete only", "In progress only")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    private val sortValues = arrayOf("hits", "kudos", "comments", "bookmarks", "word_count", "revised_at", "created_at")
    private val completionValues = arrayOf("", "T", "F")

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val completionIndex = filters.filterIsInstance<Filter.Select>().firstOrNull()?.value ?: 0
        
        val sortBy = sortValues.getOrElse(sortIndex) { "hits" }
        val completion = completionValues.getOrElse(completionIndex) { "" }
        
        val url = buildString {
            append("$baseUrl/works/search?page=$page")
            append("&work_search%5Blanguage_id%5D=en")
            append("&work_search%5Bsort_column%5D=$sortBy")
            append("&work_search%5Bsort_direction%5D=desc")
            if (completion.isNotEmpty()) {
                append("&work_search%5Bcomplete%5D=$completion")
            }
            if (!query.isNullOrBlank()) {
                append("&work_search%5Bquery%5D=${query.trim().replace(" ", "+")}")
            }
        }
        
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: Document): MangasPageInfo {
        val novels = document.select("li.work").mapNotNull { element ->
            val titleLink = element.select("h4.heading > a").first() ?: return@mapNotNull null
            val name = titleLink.text().trim()
            val href = titleLink.attr("href")
            
            if (name.isNotBlank() && href.isNotBlank()) {
                MangaInfo(
                    key = href.removePrefix("/"),
                    title = name,
                    cover = ""
                )
            } else null
        }
        
        val hasNext = document.select("li.next a").isNotEmpty()
        return MangasPageInfo(novels, hasNext)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        if (detailFetch != null && detailFetch.html.isNotBlank()) {
            return detailParse(manga, detailFetch.html.asJsoup())
        }
        
        val url = "$baseUrl/${manga.key}"
        val document = client.get(requestBuilder(url)).asJsoup()
        return detailParse(manga, document)
    }

    private fun detailParse(manga: MangaInfo, document: Document): MangaInfo {
        val title = document.select("h2.title").text().trim().ifBlank { manga.title }
        val author = document.select("a[rel=author]").joinToString(", ") { it.text().trim() }
        val genres = document.select("dd.freeform.tags li a.tag").map { it.text().trim() }
        
        val summary = buildString {
            val fandom = document.select("dd.fandom.tags li a.tag").joinToString(", ") { it.text().trim() }
            val rating = document.select("dd.rating.tags li a.tag").joinToString(", ") { it.text().trim() }
            val warning = document.select("dd.warning.tags li a.tag").joinToString(", ") { it.text().trim() }
            val summaryText = document.select("blockquote.userstuff").text().trim()
            val relation = document.select("dd.relationship.tags li a.tag").joinToString(", ") { it.text().trim() }
            val character = document.select("dd.character.tags li a.tag").joinToString(", ") { it.text().trim() }
            
            if (fandom.isNotBlank()) appendLine("Fandom: $fandom\n")
            if (rating.isNotBlank()) appendLine("Rating: $rating\n")
            if (warning.isNotBlank()) appendLine("Warning: $warning\n")
            if (summaryText.isNotBlank()) appendLine("Summary: $summaryText\n")
            if (relation.isNotBlank()) appendLine("Relationships: $relation\n")
            if (character.isNotBlank()) appendLine("Characters: $character")
        }
        
        val statusText = document.select("dt.status").text()
        val status = if (statusText.contains("Updated")) MangaInfo.ONGOING else MangaInfo.COMPLETED
        
        return manga.copy(
            title = title,
            author = author,
            genres = genres,
            description = summary,
            status = status
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return chaptersParse(manga, chapterFetch.html.asJsoup())
        }
        
        val url = "$baseUrl/${manga.key}"
        val document = client.get(requestBuilder(url)).asJsoup()
        
        // Try to get chapters from navigate page
        val navigateUrl = "$baseUrl/${manga.key}/navigate"
        val navigateDoc = try {
            client.get(requestBuilder(navigateUrl)).asJsoup()
        } catch (e: Exception) {
            null
        }
        
        return chaptersParse(manga, document, navigateDoc)
    }

    private fun chaptersParse(manga: MangaInfo, document: Document, navigateDoc: Document? = null): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()
        
        // Get release dates from navigate page
        val releaseDates = mutableListOf<String>()
        navigateDoc?.select("ol.index li")?.forEach { li ->
            val dateText = li.select("span.datetime").text()
                .replace(Regex("\\(([^)]+)\\)"), "$1").trim()
            if (dateText.isNotBlank()) {
                releaseDates.add(dateText)
            }
        }
        
        // Check for chapter dropdown
        val chapterSelect = document.select("#chapter_index select")
        if (chapterSelect.isNotEmpty()) {
            var dateIndex = 0
            chapterSelect.first()?.select("option")?.forEach { option ->
                val chapterName = option.text().trim()
                val chapterCode = option.attr("value")
                if (chapterCode.isNotBlank()) {
                    val releaseDate = releaseDates.getOrNull(dateIndex) ?: ""
                    dateIndex++
                    chapters.add(ChapterInfo(
                        name = chapterName,
                        key = "${manga.key}/chapters/$chapterCode",
                        dateUpload = 0L
                    ))
                }
            }
        }
        
        // Fallback: check for single chapter or chapter titles
        if (chapters.isEmpty()) {
            document.select("#chapters h3.title").forEach { titleEl ->
                val fullText = titleEl.text().trim()
                val chapterName = fullText.substringAfter(":").trim().ifBlank {
                    document.select(".work .title.heading").text().trim()
                }
                val chapterUrl = titleEl.select("a").attr("href")
                val chapterCode = Regex("/chapters/(\\d+)").find(chapterUrl)?.groupValues?.get(1)
                
                if (chapterCode != null) {
                    chapters.add(ChapterInfo(
                        name = chapterName,
                        key = "${manga.key}/chapters/$chapterCode",
                        dateUpload = 0L
                    ))
                }
            }
        }
        
        // Last fallback: single chapter work
        if (chapters.isEmpty()) {
            val title = document.select("h2.title.heading").text().trim().ifBlank { manga.title }
            chapters.add(ChapterInfo(
                name = title,
                key = manga.key,
                dateUpload = 0L
            ))
        }
        
        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<ireader.core.source.model.Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            val url = "$baseUrl/${chapter.key}"
            client.get(requestBuilder(url)).asJsoup()
        }
        
        // Remove landmark heading
        document.select("h3.landmark.heading#work").remove()
        
        // Clean up chapter title links
        document.select("h3.title").forEach { h3 ->
            h3.select("a").removeAttr("href")
        }
        
        val content = document.select("div#chapters > div").html()
        return listOf(content).map { ireader.core.source.model.Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/works/search?page={page}&work_search%5Blanguage_id%5D=en&work_search%5Bsort_column%5D=hits&work_search%5Bsort_direction%5D=desc",
                selector = "li.work",
                nameSelector = "h4.heading > a",
                linkSelector = "h4.heading > a",
                linkAtt = "href",
                coverSelector = "",
                coverAtt = "",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/works/search?page={page}&work_search%5Blanguage_id%5D=en&work_search%5Bquery%5D={query}",
                selector = "li.work",
                nameSelector = "h4.heading > a",
                linkSelector = "h4.heading > a",
                linkAtt = "href",
                coverSelector = "",
                coverAtt = "",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h2.title",
            authorBookSelector = "a[rel=author]",
            descriptionSelector = "blockquote.userstuff",
            categorySelector = "dd.freeform.tags li a.tag"
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#chapter_index option",
            nameSelector = "",
            linkSelector = "",
            linkAtt = "value"
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div#chapters > div"
        )
}
