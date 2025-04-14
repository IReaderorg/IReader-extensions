package ireader.fictionzone

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Listing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Extension
abstract class FictionZone(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://fictionzone.net"
    override val id: Long
        get() = 91 // Choose a unique ID
    override val name: String
        get() = "FictionZone"

    private val cachedNovelIds = mutableMapOf<String, String>()

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

    // This JSON client is needed to handle API responses
    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/library?page={page}",
                selector = "div.novel-card",
                nameSelector = "a > div.title > h1",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                maxPage = 10
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/library?query={query}&page={page}&sort=views-all",
                selector = "div.novel-card",
                nameSelector = "a > div.title > h1",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                maxPage = 10,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.novel-title > h1",
            coverSelector = "div.novel-img > img",
            coverAtt = "src",
            authorBookSelector = "div.novel-author > content",
            categorySelector = "div.genres > .items > span, div.tags > .items > a",
            descriptionSelector = "#synopsis > div.content",
            statusSelector = "div.novel-status > div.content",
            onStatus = { status ->
                when {
                    status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    status.contains("Complete", ignoreCase = true) -> MangaInfo.COMPLETED
                    else -> MangaInfo.UNKNOWN
                }
            },
            onName = { name ->
                // Extract novel ID from script for later use
                try {
                    val novelId = Regex("\"novel_id\":\"([^\"]+)\"").find(name)?.groupValues?.get(1)
                    if (novelId != null) {
                        cachedNovelIds[name] = novelId
                    }
                } catch (e: Exception) {
                    // Ignore extraction errors
                }
                name
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.chapters > div.list-wrapper > div.items > a.chapter",
            nameSelector = "span.chapter-title",
            linkSelector = "a.chapter",
            linkAtt = "href",
            uploadDateSelector = "span.update-date",
            reverseChapterList = false,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.chapter-content",
        )

    private fun parseAgoDate(dateStr: String): Long {
        if (!dateStr.contains("ago")) return 0
        
        val timeAgo = dateStr.split(" ")[0].toIntOrNull() ?: return 0
        val currentTime = System.currentTimeMillis()
        
        return when {
            dateStr.contains("hour") -> currentTime - TimeUnit.HOURS.toMillis(timeAgo.toLong())
            dateStr.contains("day") -> currentTime - TimeUnit.DAYS.toMillis(timeAgo.toLong())
            dateStr.contains("month") -> currentTime - TimeUnit.DAYS.toMillis(timeAgo.toLong() * 30)
            dateStr.contains("year") -> currentTime - TimeUnit.DAYS.toMillis(timeAgo.toLong() * 365)
            else -> 0
        }
    }
    
    // Get additional chapters through API for detail pages with pagination
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        if (commands.isEmpty()) {
            val baseChapters = super.getChapterList(manga, commands)
            
            // Extract novel ID from the source HTML or cached values
            val htmlResponse = client.get(manga.key).asJsoup()
            var novelId = cachedNovelIds[manga.title]
            
            if (novelId == null) {
                val scriptData = htmlResponse.select("script#__NUXT_DATA__").html()
                // This is a simplistic approach - in reality parsing this JSON might be more complex
                val lastPageText = htmlResponse.select("div.chapters ul.el-pager > li:last-child").text()
                val totalPages = lastPageText.toIntOrNull() ?: 1
                
                // Try to extract the novel ID from the script
                novelId = try {
                    Regex("novel_covers/([^\"]+)").find(scriptData)?.groupValues?.get(1)
                } catch (e: Exception) {
                    null
                }
                
                if (novelId != null) {
                    cachedNovelIds[manga.title] = novelId
                } else {
                    // If we can't extract the ID, return only the base chapters
                    return baseChapters
                }
                
                // If there's only one page, return the base chapters
                if (totalPages <= 1) {
                    return baseChapters
                }
                
                // Otherwise, fetch additional pages
                val allChapters = baseChapters.toMutableList()
                
                // Start from page 2 since we already have page 1
                for (page in 2..totalPages) {
                    val requestBody = buildJsonObject {
                        put("path", "/chapter/all/$novelId")
                        putJsonObject("query") {
                            put("page", page)
                        }
                        putJsonObject("headers") {
                            put("content-type", "application/json")
                        }
                        put("method", "get")
                    }
                    
                    val response = client.post(baseUrl + "/api/__api_party/api-v1") {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                    }.body<JsonObject>()
                    
                    // Parse the response and add chapters
                    val chaptersData = response["_data"]
                    // Process chapters from the API response
                    // This would need proper implementation based on the actual response format
                }
                
                return allChapters
            }
        }
        
        return super.getChapterList(manga, commands)
    }
} 