package ireader.fenrirrealm

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class FenrirRealm(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://fenrirealm.com"
    override val id: Long
        get() = 91 // Choose a unique ID
    override val name: String
        get() = "Fenrir Realm"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort by:",
            arrayOf(
                "Popular",
                "Latest",
                "Updated"
            )
        ),
        Filter.Select(
            "Status",
            arrayOf(
                "All",
                "Ongoing",
                "Completed"
            )
        ),
        Filter.Group(
            "Genres",
            arrayOf(
                "Action",
                "Adult",
                "Adventure",
                "Comedy",
                "Drama",
                "Ecchi",
                "Fantasy",
                "Gender Bender",
                "Harem",
                "Historical",
                "Horror",
                "Josei",
                "Martial Arts",
                "Mature",
                "Mecha",
                "Mystery",
                "Psychological",
                "Romance",
                "School Life",
                "Sci-fi",
                "Seinen",
                "Shoujo",
                "Shoujo Ai",
                "Shounen",
                "Shounen Ai",
                "Slice of Life",
                "Smut",
                "Sports",
                "Supernatural",
                "Tragedy",
                "Wuxia",
                "Xianxia",
                "Xuanhuan",
                "Yaoi",
                "Yuri"
            )
        )
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    private val statusValues = arrayOf(
        "any",
        "ongoing",
        "completed"
    )

    private val sortValues = arrayOf(
        "popular",
        "latest",
        "updated"
    )

    // Map genres to their API IDs
    private val genreMap = mapOf(
        "Action" to "1",
        "Adult" to "2",
        "Adventure" to "3",
        "Comedy" to "4",
        "Drama" to "5",
        "Ecchi" to "6",
        "Fantasy" to "7",
        "Gender Bender" to "8",
        "Harem" to "9",
        "Historical" to "10",
        "Horror" to "11",
        "Josei" to "12",
        "Martial Arts" to "13",
        "Mature" to "14",
        "Mecha" to "15",
        "Mystery" to "16",
        "Psychological" to "17",
        "Romance" to "18",
        "School Life" to "19",
        "Sci-fi" to "20",
        "Seinen" to "21",
        "Shoujo" to "22",
        "Shoujo Ai" to "23",
        "Shounen" to "24",
        "Shounen Ai" to "25",
        "Slice of Life" to "26",
        "Smut" to "27",
        "Sports" to "28",
        "Supernatural" to "29",
        "Tragedy" to "30",
        "Wuxia" to "31",
        "Xianxia" to "32",
        "Xuanhuan" to "33",
        "Yaoi" to "34",
        "Yuri" to "35"
    )

    override suspend fun getListRequest(
        filters: FilterList,
        page: Int
    ): Document {
        val sort = sortValues[filters.findInstance<Filter.Sort>()?.value ?: 0]
        val status = statusValues[filters.findInstance<Filter.Select>()?.value ?: 0]
        
        val genreFilter = filters.findInstance<Filter.Group>()?.let { group ->
            group.state.withIndex()
                .filter { it.value }
                .map { genreMap[group.values[it.index]] }
                .joinToString("") { "&genres%5B%5D=$it" }
        } ?: ""
        
        val searchQuery = filters.findInstance<Filter.Title>()?.value ?: ""
        val searchParam = if (searchQuery.isNotEmpty()) "&search=${searchQuery}" else ""
        
        val url = "$baseUrl/api/novels/filter?page=$page&per_page=20&status=$status&order=$sort$genreFilter$searchParam"
        val response = client.get(url).bodyAsText()
        val jsonData = json.parseToJsonElement(response).jsonObject

        // Create a dummy document to hold the JSON data
        val doc = Document(url)
        doc.attr("jsonData", response)
        return doc
    }

    override fun getExploreList(document: Document): List<MangaInfo> {
        val jsonData = document.attr("jsonData")
        val result = json.parseToJsonElement(jsonData).jsonObject
        val data = result["data"]?.jsonArray ?: return emptyList()
        
        return data.map { novel ->
            val novelObj = novel.jsonObject
            val title = novelObj["title"]?.jsonPrimitive?.content ?: ""
            val slug = novelObj["slug"]?.jsonPrimitive?.content ?: ""
            val cover = novelObj["cover"]?.jsonPrimitive?.content ?: ""
            val description = novelObj["description"]?.jsonPrimitive?.content ?: ""
            
            MangaInfo(
                key = slug,
                title = title,
                cover = "$baseUrl/$cover",
                description = description
            )
        }
    }

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.my-2",
            coverSelector = "#cover",
            descriptionSelector = "div.overflow-hidden.transition-all.max-h-\\[108px\\]",
            authorBookSelector = "div.flex-1 > div.mb-3 > a.inline-flex",
            categorySelector = "div.flex-1 > div.flex:not(.mb-3, .mt-5) > a",
            statusSelector = "div.flex-1 > div.mb-3 > span.rounded-md"
        )

    override suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        val response = client.get("$baseUrl/api/novels/chapter-list/${manga.key}").bodyAsText()
        
        // Create a dummy document to hold the JSON data
        val doc = Document("$baseUrl/series/${manga.key}")
        doc.attr("jsonData", response)
        return doc
    }

    override fun getChapterList(document: Document): List<MangaInfo.Chapter> {
        val jsonData = document.attr("jsonData")
        val chapters = json.parseToJsonElement(jsonData).jsonArray
        
        return chapters.map { chapter ->
            val chapterObj = chapter.jsonObject
            val number = chapterObj["number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val title = chapterObj["title"]?.jsonPrimitive?.content ?: ""
            val createdAt = chapterObj["created_at"]?.jsonPrimitive?.content ?: ""
            
            // Check if chapter is locked
            val locked = chapterObj["locked"]?.jsonObject?.get("price")?.jsonPrimitive?.content?.toIntOrNull()
            val isLocked = locked != null && locked > 0
            
            val chapterName = if (isLocked) {
                "🔒 Chapter $number" + (if (title.isNotBlank() && title != "Chapter $number") " - $title" else "")
            } else {
                "Chapter $number" + (if (title.isNotBlank() && title != "Chapter $number") " - $title" else "")
            }
            
            MangaInfo.Chapter(
                key = "${document.location().substringAfterLast("/series/")}/chapter-$number",
                name = chapterName,
                dateUpload = createdAt,
                number = number.toFloat()
            )
        }.sortedBy { it.number }
    }

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#reader-area",
        )
} 