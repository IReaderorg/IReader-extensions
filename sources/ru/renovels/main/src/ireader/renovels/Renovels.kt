package ireader.renovels

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
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
import kotlinx.serialization.json.booleanOrNull
import tachiyomix.annotations.Extension

@Extension
abstract class Renovels(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ru"
    override val baseUrl: String get() = "https://renovels.org"
    override val id: Long get() = 83
    override val name: String get() = "Renovels"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort by",
            arrayOf("Rating", "Views", "Likes", "Date Added", "Date Updated", "Chapters")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    private val sortValues = arrayOf("rating", "views", "votes", "id", "chapter_date", "count_chapters")

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val sortBy = sortValues.getOrElse(sortIndex) { "rating" }
        
        val url = if (!query.isNullOrBlank()) {
            "$baseUrl/api/v2/search/?query=${query.trim().replace(" ", "+")}&count=100&field=titles&page=$page"
        } else {
            "$baseUrl/api/v2/search/catalog/?count=30&ordering=-$sortBy&page=$page"
        }
        
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        val results = jsonObj["results"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
        
        val novels = results.mapNotNull { element ->
            val obj = element.jsonObject
            val dir = obj["dir"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val mainName = obj["main_name"]?.jsonPrimitive?.contentOrNull
            val secondaryName = obj["secondary_name"]?.jsonPrimitive?.contentOrNull
            val name = mainName ?: secondaryName ?: return@mapNotNull null
            
            val coverObj = obj["cover"]?.jsonObject
            val coverPath = coverObj?.get("high")?.jsonPrimitive?.contentOrNull
                ?: coverObj?.get("mid")?.jsonPrimitive?.contentOrNull
                ?: coverObj?.get("low")?.jsonPrimitive?.contentOrNull
                ?: ""
            val cover = if (coverPath.isNotBlank()) "$baseUrl$coverPath" else ""
            
            MangaInfo(
                key = dir,
                title = name,
                cover = cover
            )
        }
        
        return MangasPageInfo(novels, novels.size >= 30)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val url = "$baseUrl/api/titles/${manga.key}"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        val content = jsonObj["content"]?.jsonObject ?: return manga
        
        val mainName = content["main_name"]?.jsonPrimitive?.contentOrNull
        val secondaryName = content["secondary_name"]?.jsonPrimitive?.contentOrNull
        val anotherName = content["another_name"]?.jsonPrimitive?.contentOrNull
        val name = mainName ?: secondaryName ?: anotherName ?: manga.title
        
        val description = content["description"]?.jsonPrimitive?.contentOrNull ?: ""
        
        val coverObj = content["cover"]?.jsonObject
        val coverPath = coverObj?.get("high")?.jsonPrimitive?.contentOrNull
            ?: coverObj?.get("mid")?.jsonPrimitive?.contentOrNull
            ?: coverObj?.get("low")?.jsonPrimitive?.contentOrNull
            ?: ""
        val cover = if (coverPath.isNotBlank()) "$baseUrl/media/$coverPath" else manga.cover
        
        val statusObj = content["status"]?.jsonObject
        val statusName = statusObj?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
        val status = if (statusName == "Продолжается") MangaInfo.ONGOING else MangaInfo.COMPLETED
        
        val genres = mutableListOf<String>()
        content["genres"]?.jsonArray?.forEach { genre ->
            genre.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.let { genres.add(it) }
        }
        content["categories"]?.jsonArray?.forEach { category ->
            category.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.let { genres.add(it) }
        }
        
        return manga.copy(
            title = name,
            cover = cover,
            description = description,
            genres = genres,
            status = status
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        // First get novel info to find branch_id
        val infoUrl = "$baseUrl/api/titles/${manga.key}"
        val infoResponse = client.get(requestBuilder(infoUrl)).bodyAsText()
        val infoObj = json.parseToJsonElement(infoResponse).jsonObject
        val content = infoObj["content"]?.jsonObject ?: return emptyList()
        
        val branches = content["branches"]?.jsonArray
        val branchId = branches?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: content["id"]?.jsonPrimitive?.contentOrNull
            ?: return emptyList()
        
        val totalChapters = branches?.firstOrNull()?.jsonObject?.get("count_chapters")?.jsonPrimitive?.intOrNull
            ?: content["count_chapters"]?.jsonPrimitive?.intOrNull
            ?: 0
        
        val totalPages = (totalChapters + 99) / 100
        val chapters = mutableListOf<ChapterInfo>()
        
        for (page in 1..totalPages) {
            val url = "$baseUrl/api/titles/chapters/?branch_id=$branchId&ordering=index&user_data=1&count=100&page=$page"
            val response = client.get(requestBuilder(url)).bodyAsText()
            val jsonObj = json.parseToJsonElement(response).jsonObject
            val chapterContent = jsonObj["content"]?.jsonArray ?: continue
            
            var skipRest = false
            chapterContent.forEach { element ->
                if (skipRest) return@forEach
                
                val chapterObj = element.jsonObject
                val isPaid = chapterObj["is_paid"]?.jsonPrimitive?.booleanOrNull == true
                val isBought = chapterObj["is_bought"]?.jsonPrimitive?.booleanOrNull == true
                
                if (!isPaid || isBought) {
                    val id = chapterObj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val tome = chapterObj["tome"]?.jsonPrimitive?.intOrNull ?: 0
                    val chapterNum = chapterObj["chapter"]?.jsonPrimitive?.contentOrNull ?: ""
                    val chapterName = chapterObj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                    val index = chapterObj["index"]?.jsonPrimitive?.intOrNull ?: chapters.size
                    
                    val name = "Том $tome Глава $chapterNum" + if (chapterName.isNotBlank()) " $chapterName" else ""
                    
                    chapters.add(ChapterInfo(
                        name = name,
                        key = "${manga.key}/$id",
                        number = index.toFloat(),
                        dateUpload = 0L
                    ))
                } else {
                    skipRest = true
                }
            }
            
            if (skipRest) break
        }
        
        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val chapterId = chapter.key.split("/").lastOrNull() ?: return emptyList()
        val url = "$baseUrl/api/v2/titles/chapters/$chapterId"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        
        val content = jsonObj["content"]?.jsonPrimitive?.contentOrNull ?: ""
        return listOf(Text(content))
    }

    override val exploreFetchers: List<BaseExploreFetcher> get() = emptyList()
    override val detailFetcher: Detail get() = SourceFactory.Detail()
    override val chapterFetcher: Chapters get() = SourceFactory.Chapters()
    override val contentFetcher: Content get() = SourceFactory.Content()
}
