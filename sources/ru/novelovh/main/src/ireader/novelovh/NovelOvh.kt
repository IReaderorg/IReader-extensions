package ireader.novelovh

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
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import tachiyomix.annotations.Extension

@Extension
abstract class NovelOvh(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ru"
    override val baseUrl: String get() = "https://novel.ovh"
    private val apiUrl: String get() = "https://api.novel.ovh/v2"
    override val id: Long get() = 82
    override val name: String get() = "НовелОВХ"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort by",
            arrayOf("Rating", "Views", "Likes", "Chapters", "Bookmarks", "Created", "Updated")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    private val sortValues = arrayOf(
        "averageRating", "viewsCount", "likesCount", "chaptersCount",
        "bookmarksCount", "createdAt", "updatedAt"
    )

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val sortBy = sortValues.getOrElse(sortIndex) { "averageRating" }
        
        val url = if (!query.isNullOrBlank()) {
            "$apiUrl/books?type=NOVEL&search=${query.trim().replace(" ", "+")}"
        } else {
            "$apiUrl/books?page=${page - 1}&sort=$sortBy,desc"
        }
        
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonArray = json.parseToJsonElement(response).jsonArray
        
        val novels = jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val nameObj = obj["name"]?.jsonObject
            val name = nameObj?.get("ru")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cover = obj["poster"]?.jsonPrimitive?.contentOrNull ?: ""
            
            MangaInfo(
                key = slug,
                title = name,
                cover = cover
            )
        }
        
        return MangasPageInfo(novels, novels.size >= 20)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val url = "$baseUrl/content/${manga.key}?_data=routes/reader/book/\$slug/index"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        
        val book = jsonObj["book"]?.jsonObject ?: return manga
        val nameObj = book["name"]?.jsonObject
        val name = nameObj?.get("ru")?.jsonPrimitive?.contentOrNull ?: manga.title
        val cover = book["poster"]?.jsonPrimitive?.contentOrNull ?: manga.cover
        val description = book["description"]?.jsonPrimitive?.contentOrNull ?: ""
        
        val genres = book["labels"]?.jsonArray?.mapNotNull { label ->
            label.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        } ?: emptyList()
        
        var author = ""
        var artist = ""
        book["relations"]?.jsonArray?.forEach { relation ->
            val relObj = relation.jsonObject
            val type = relObj["type"]?.jsonPrimitive?.contentOrNull
            val publisherName = relObj["publisher"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            when (type) {
                "AUTHOR" -> author = publisherName ?: ""
                "ARTIST" -> artist = publisherName ?: ""
            }
        }
        
        val statusText = book["status"]?.jsonPrimitive?.contentOrNull ?: ""
        val status = if (statusText == "ONGOING") MangaInfo.ONGOING else MangaInfo.COMPLETED
        
        return manga.copy(
            title = name,
            cover = cover,
            description = description,
            genres = genres,
            author = author,
            artist = artist,
            status = status
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val url = "$baseUrl/content/${manga.key}?_data=routes/reader/book/\$slug/index"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        
        val branches = jsonObj["branches"]?.jsonArray ?: return emptyList()
        val branchNames = mutableMapOf<String, String>()
        branches.forEach { branch ->
            val branchObj = branch.jsonObject
            val id = branchObj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val publishers = branchObj["publishers"]?.jsonArray
            val publisherName = publishers?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown"
            branchNames[id] = publisherName
        }
        
        val chaptersArray = jsonObj["chapters"]?.jsonArray ?: return emptyList()
        val chapters = chaptersArray.mapIndexedNotNull { index, element ->
            val chapterObj = element.jsonObject
            val id = chapterObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val title = chapterObj["title"]?.jsonPrimitive?.contentOrNull
            val volume = chapterObj["volume"]?.jsonPrimitive?.intOrNull ?: 0
            val number = chapterObj["number"]?.jsonPrimitive?.intOrNull ?: (chaptersArray.size - index)
            val chapterName = chapterObj["name"]?.jsonPrimitive?.contentOrNull
            
            val name = title ?: "Том $volume ${chapterName ?: "Глава $number"}"
            
            ChapterInfo(
                name = name,
                key = "${manga.key}/$id",
                number = (chaptersArray.size - index).toFloat(),
                dateUpload = 0L
            )
        }
        
        return chapters.reversed()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val chapterId = chapter.key.split("/").lastOrNull() ?: return emptyList()
        val url = "$apiUrl/chapters/$chapterId"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        
        // Get image mappings
        val imageMap = mutableMapOf<String, String>()
        jsonObj["pages"]?.jsonArray?.forEach { page ->
            val pageObj = page.jsonObject
            val id = pageObj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val image = pageObj["image"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            imageMap[id] = image
        }
        
        val content = jsonObj["content"]?.jsonObject
        val contentArray = content?.get("content")?.jsonArray ?: return emptyList()
        
        val html = buildContentHtml(contentArray, imageMap)
        return listOf(Text(html))
    }

    private fun buildContentHtml(content: kotlinx.serialization.json.JsonArray, imageMap: Map<String, String>): String {
        val sb = StringBuilder()
        
        content.forEach { element ->
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            
            when (type) {
                "image" -> {
                    val pageId = obj["attrs"]?.jsonObject?.get("pages")?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    if (pageId != null && imageMap.containsKey(pageId)) {
                        sb.append("<img src=\"${imageMap[pageId]}\"/>")
                    }
                }
                "hardBreak" -> sb.append("<br>")
                "horizontalRule", "delimiter" -> sb.append("<h2 style=\"text-align: center\">***</h2>")
                "paragraph" -> {
                    val innerContent = obj["content"]?.jsonArray
                    val innerHtml = if (innerContent != null) buildContentHtml(innerContent, imageMap) else "<br>"
                    sb.append("<p>$innerHtml</p>")
                }
                "orderedList" -> {
                    val innerContent = obj["content"]?.jsonArray
                    val innerHtml = if (innerContent != null) buildContentHtml(innerContent, imageMap) else ""
                    sb.append("<ol>$innerHtml</ol>")
                }
                "listItem" -> {
                    val innerContent = obj["content"]?.jsonArray
                    val innerHtml = if (innerContent != null) buildContentHtml(innerContent, imageMap) else ""
                    sb.append("<li>$innerHtml</li>")
                }
                "blockquote" -> {
                    val innerContent = obj["content"]?.jsonArray
                    val innerHtml = if (innerContent != null) buildContentHtml(innerContent, imageMap) else ""
                    sb.append("<blockquote>$innerHtml</blockquote>")
                }
                "italic" -> {
                    val innerContent = obj["content"]?.jsonArray
                    val innerHtml = if (innerContent != null) buildContentHtml(innerContent, imageMap) else ""
                    sb.append("<i>$innerHtml</i>")
                }
                "bold" -> {
                    val innerContent = obj["content"]?.jsonArray
                    val innerHtml = if (innerContent != null) buildContentHtml(innerContent, imageMap) else ""
                    sb.append("<b>$innerHtml</b>")
                }
                "underline" -> {
                    val innerContent = obj["content"]?.jsonArray
                    val innerHtml = if (innerContent != null) buildContentHtml(innerContent, imageMap) else ""
                    sb.append("<u>$innerHtml</u>")
                }
                "heading" -> {
                    val innerContent = obj["content"]?.jsonArray
                    val innerHtml = if (innerContent != null) buildContentHtml(innerContent, imageMap) else ""
                    sb.append("<h2>$innerHtml</h2>")
                }
                "text" -> {
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    sb.append(text)
                }
            }
        }
        
        return sb.toString()
    }

    override val exploreFetchers: List<BaseExploreFetcher> get() = emptyList()
    override val detailFetcher: Detail get() = SourceFactory.Detail()
    override val chapterFetcher: Chapters get() = SourceFactory.Chapters()
    override val contentFetcher: Content get() = SourceFactory.Content()
}
