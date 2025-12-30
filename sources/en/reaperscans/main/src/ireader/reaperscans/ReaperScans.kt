package ireader.reaperscans

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension

@Extension
abstract class ReaperScans(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://reaperscans.com"
    private val apiBase: String get() = "https://api.reaperscans.com"
    private val mediaBase: String get() = "https://media.reaperscans.com/file/4SRBHm/"
    override val id: Long get() = 93L
    override val name: String get() = "Reaper Scans"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return queryNovels(page, "")
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim() ?: ""
        return queryNovels(page, query)
    }

    private suspend fun queryNovels(page: Int, search: String): MangasPageInfo {
        val url = "$apiBase/query?page=$page&perPage=20&series_type=Novel&query_string=${search.encodeURLParameter()}&order=desc&orderBy=created_at&adult=true&status=All&tags_ids=[]"
        val response = client.get(requestBuilder(url)).bodyAsText()

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val data = jsonObj["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)

        val novels = data.mapNotNull { element ->
            val novel = element.jsonObject
            val title = novel["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val thumbnail = novel["thumbnail"]?.jsonPrimitive?.contentOrNull ?: ""
            val seriesSlug = novel["series_slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            val cover = if (thumbnail.startsWith("novels/")) {
                mediaBase + thumbnail
            } else {
                thumbnail
            }

            MangaInfo(
                key = seriesSlug,
                title = title,
                cover = cover,
            )
        }

        val meta = jsonObj["meta"]?.jsonObject
        val lastPage = meta?.get("last_page")?.jsonPrimitive?.intOrNull ?: 1
        val hasNext = page < lastPage

        return MangasPageInfo(novels, hasNext)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val response = client.get(requestBuilder("$apiBase/series/${manga.key}")).bodyAsText()
        val novel = json.parseToJsonElement(response).jsonObject

        val title = novel["title"]?.jsonPrimitive?.contentOrNull ?: manga.title
        val thumbnail = novel["thumbnail"]?.jsonPrimitive?.contentOrNull ?: ""
        val cover = if (thumbnail.startsWith("novels/")) {
            mediaBase + thumbnail
        } else {
            thumbnail.ifBlank { manga.cover }
        }
        val description = novel["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val author = novel["author"]?.jsonPrimitive?.contentOrNull ?: ""
        val statusText = novel["status"]?.jsonPrimitive?.contentOrNull ?: ""
        val tags = novel["tags"]?.jsonArray?.mapNotNull { 
            it.jsonPrimitive.contentOrNull 
        } ?: emptyList()

        val status = when {
            statusText.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
            statusText.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = tags,
            status = status,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val response = client.get(requestBuilder("$apiBase/chapters/${manga.key}?perPage=${Int.MAX_VALUE}")).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        val data = jsonObj["data"]?.jsonArray ?: return emptyList()

        return data.mapNotNull { element ->
            val chapter = element.jsonObject
            val chapterSlug = chapter["chapter_slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val chapterName = chapter["chapter_name"]?.jsonPrimitive?.contentOrNull ?: "Chapter"
            val index = chapter["index"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f

            ChapterInfo(
                name = chapterName,
                key = "${manga.key}/$chapterSlug",
                number = index,
            )
        }.reversed()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val body = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html
        } else {
            client.get("$baseUrl/series/${chapter.key}") {
                headers {
                    append("RSC", "1")
                }
            }.bodyAsText()
        }

        val content = extractChapterContent(body)

        return content.split("\n", "</p>", "<p>", "<br>", "<br/>", "<br />")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private fun extractChapterContent(chapter: String): String {
        val lines = chapter.split("\n")
        val startIndex = lines.indexOfFirst { it.contains("<p") }
        if (startIndex == -1) return chapter

        val prefix = lines[startIndex].substring(0, lines[startIndex].indexOf("<"))
        val commonPrefix = if (prefix.contains(":") && prefix.contains(",")) {
            prefix.substring(prefix.indexOf(":"), prefix.indexOf(","))
        } else {
            return lines.subList(startIndex, lines.size).joinToString("\n")
        }

        val endIndex = lines.lastIndexOf(commonPrefix)
        if (endIndex <= startIndex) return lines.subList(startIndex, lines.size).joinToString("\n")

        val content = lines.subList(startIndex, endIndex).joinToString("\n")
        val parts = content.split(commonPrefix)
        if (parts.size < 2) return content

        val deduplicated = parts[1]
        val htmlStart = deduplicated.indexOf("<")
        val htmlEnd = deduplicated.lastIndexOf(">")
        
        return if (htmlStart >= 0 && htmlEnd > htmlStart) {
            deduplicated.substring(htmlStart, htmlEnd + 1)
        } else {
            deduplicated
        }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/",
                selector = "div",
                nameSelector = "h1",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "img",
            coverAtt = "src",
            descriptionSelector = "p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "p",
        )
}
