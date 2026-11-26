package ireader.ao3

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
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

    override fun getUserAgent(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override fun requestBuilder(
        url: String,
        block: HeadersBuilder.() -> Unit
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url { takeFrom(url) }
            headers {
                append(HttpHeaders.UserAgent, getUserAgent())
                append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                append(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                append(HttpHeaders.CacheControl, "max-age=0")
                append(HttpHeaders.Cookie, "view_adult=true")
                block()
            }
        }
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Author(),
        Filter.Sort(
            "Sort by",
            arrayOf("Hits", "Kudos", "Comments", "Bookmarks", "Word Count", "Date Updated", "Date Posted")
        ),
        Filter.Select(
            "Completion",
            arrayOf("All works", "Complete only", "In progress only")
        ),
        Filter.Select(
            "Crossover",
            arrayOf("All works", "Include crossovers", "Exclude crossovers")
        ),
        Filter.Select(
            "Single Chapter",
            arrayOf("All works", "Single chapter only", "Multi-chapter only")
        ),
        Filter.Group(
            "Tags / Text Fields",
            listOf(
                Filter.Text("Fandoms"),
                Filter.Text("Characters"),
                Filter.Text("Relationships"),
                Filter.Text("Additional Tags"),
                Filter.Text("Word Count (e.g. 1000-50000, >1000, <5000)")
            )
        ),
        Filter.Select(
            "Rating",
            arrayOf("Any", "Not Rated", "General Audiences", "Teen And Up Audiences", "Mature", "Explicit")
        ),
        Filter.Group(
            "Warnings",
            listOf(
                Filter.Check("Creator Chose Not To Use Archive Warnings"),
                Filter.Check("Graphic Depictions Of Violence"),
                Filter.Check("Major Character Death"),
                Filter.Check("No Archive Warnings Apply"),
                Filter.Check("Rape/Non-Con"),
                Filter.Check("Underage")
            )
        ),
        Filter.Group(
            "Categories",
            listOf(
                Filter.Check("F/F"),
                Filter.Check("F/M"),
                Filter.Check("Gen"),
                Filter.Check("M/M"),
                Filter.Check("Multi"),
                Filter.Check("Other")
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    private val sortValues = arrayOf("hits", "kudos", "comments", "bookmarks", "word_count", "revised_at", "created_at")
    private val completionValues = arrayOf("", "T", "F")
    private val crossoverValues = arrayOf("", "T", "F")
    private val singleChapterValues = arrayOf("", "1", "0")
    private val ratingIds = arrayOf("", "9", "10", "11", "12", "13")
    private val warningIds = arrayOf("14", "17", "18", "16", "19", "20")
    private val categoryIds = arrayOf("116", "22", "21", "23", "2246", "24")

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim().orEmpty()
        val author = filters.findInstance<Filter.Author>()?.value?.trim().orEmpty()
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0

        val selects = filters.filterIsInstance<Filter.Select>()
        val completionIndex = selects.getOrNull(1)?.value ?: 0
        val crossoverIndex = selects.getOrNull(2)?.value ?: 0
        val singleChapterIndex = selects.getOrNull(3)?.value ?: 0
        val ratingIndex = selects.getOrNull(4)?.value ?: 0

        val groups = filters.filterIsInstance<Filter.Group>()
        val tagGroup = groups.getOrNull(0)?.filters.orEmpty()
        val fandoms = (tagGroup.getOrNull(0) as? Filter.Text)?.value?.trim().orEmpty()
        val characters = (tagGroup.getOrNull(1) as? Filter.Text)?.value?.trim().orEmpty()
        val relationships = (tagGroup.getOrNull(2) as? Filter.Text)?.value?.trim().orEmpty()
        val otherTags = (tagGroup.getOrNull(3) as? Filter.Text)?.value?.trim().orEmpty()
        val wordCount = (tagGroup.getOrNull(4) as? Filter.Text)?.value?.trim().orEmpty()

        val warningChecks = groups.getOrNull(1)?.filters?.filterIsInstance<Filter.Check>().orEmpty()
        val categoryChecks = groups.getOrNull(2)?.filters?.filterIsInstance<Filter.Check>().orEmpty()

        val sortBy = if (query.isNotBlank() && sortIndex == 0) "_score" else sortValues.getOrElse(sortIndex) { "hits" }
        val completion = completionValues.getOrElse(completionIndex) { "" }
        val crossover = crossoverValues.getOrElse(crossoverIndex) { "" }
        val singleChapter = singleChapterValues.getOrElse(singleChapterIndex) { "" }
        val ratingId = ratingIds.getOrElse(ratingIndex) { "" }

        fun StringBuilder.param(name: String, value: String) {
            if (value.isNotBlank()) {
                append("&").append(name).append("=")
                append(java.net.URLEncoder.encode(value, "UTF-8"))
            }
        }

        val url = buildString {
            append("$baseUrl/works/search?page=$page")
            append("&work_search%5Blanguage_id%5D=en")
            append("&work_search%5Bsort_column%5D=$sortBy")
            append("&work_search%5Bsort_direction%5D=desc")
            param("work_search%5Bquery%5D", query)
            param("work_search%5Bcreators%5D", author)
            param("work_search%5Bcomplete%5D", completion)
            param("work_search%5Bcrossover%5D", crossover)
            param("work_search%5Bsingle_chapter%5D", singleChapter)
            param("work_search%5Bword_count%5D", wordCount)
            param("work_search%5Bfandom_names%5D", fandoms)
            param("work_search%5Bcharacter_names%5D", characters)
            param("work_search%5Brelationship_names%5D", relationships)
            param("work_search%5Bfreeform_names%5D", otherTags)
            param("work_search%5Brating_ids%5D", ratingId)

            warningChecks.forEachIndexed { i, check ->
                if (check.value == true) {
                    append("&work_search%5Barchive_warning_ids%5D%5B%5D=")
                    append(warningIds.getOrElse(i) { "" })
                }
            }
            categoryChecks.forEachIndexed { i, check ->
                if (check.value == true) {
                    append("&work_search%5Bcategory_ids%5D%5B%5D=")
                    append(categoryIds.getOrElse(i) { "" })
                }
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
        
        val url = when {
            manga.key.startsWith("http") -> manga.key
            manga.key.startsWith("/") -> "$baseUrl${manga.key}"
            else -> "$baseUrl/${manga.key}"
        }
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
        
        val url = when {
            manga.key.startsWith("http") -> manga.key
            manga.key.startsWith("/") -> "$baseUrl${manga.key}"
            else -> "$baseUrl/${manga.key}"
        }
        val document = client.get(requestBuilder(url)).asJsoup()
        
        // Try to get chapters from navigate page
        val navigateUrl = "$url/navigate"
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
            val url = when {
                chapter.key.startsWith("http") -> chapter.key
                chapter.key.startsWith("/") -> "$baseUrl${chapter.key}"
                else -> "$baseUrl/${chapter.key}"
            }
            val separator = if (url.contains("?")) "&" else "?"
            client.get(requestBuilder(url + separator + "view_adult=true&view_full_work=true")).asJsoup()
        }

        document.select(
            "h3.landmark.heading#work, #workskin .notes.module, #feedback, .kudos, .meta, " +
            "div.chapter.preface.group, div.preface.group, #work .meta, .secondary, #notes"
        ).remove()
        document.select("a[rel=author]").forEach { it.removeAttr("href") }

        val chapterDiv = document.select("div#chapters div.userstuff.module").first()
            ?: document.select("div.userstuff.module").first()
            ?: document.select("div#chapters > div").first()
            ?: document.select("div#chapters").first()

        chapterDiv?.select(
            "h3.title, h3.heading, .landmark.heading, div.preface, div.chapter.preface.group, .meta, .notes, .end.notes.module"
        )?.remove()
        chapterDiv?.select("a")?.forEach { it.unwrap() }

        val paragraphs = chapterDiv?.select("p")?.mapNotNull { p ->
            val t = p.text().replace(Regex("\\s+"), " ").trim()
            t.takeIf { it.isNotBlank() }
        }?.filterNot { it.startsWith("See the end of the chapter for more notes") }.orEmpty()

        val content = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n")
        } else {
            chapterDiv?.wholeText()
                ?.lines()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.joinToString("\n\n")
                .orEmpty()
        }

        if (content.isBlank()) return emptyList()
        return listOf(ireader.core.source.model.Text(content))
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
