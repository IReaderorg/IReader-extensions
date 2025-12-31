package ireader.lnmtl

import io.ktor.client.request.get
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import ireader.lnmtl.chapters.LNMTLResponse
import ireader.lnmtl.volume.LNMTLVolumeResponse
import ireader.lnmtl.volume.LNMTLVolumnResponseItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

/**
 * 📚 LnMtl - Machine Translation Novel Source
 * 
 * Uses custom JSON parsing for chapters.
 * Uses @AutoSourceId for automatic ID generation.
 */
@Extension
@AutoSourceId(seed = "LnMtl")
abstract class LnMtl(deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://lnmtl.com/"
    override val id: Long get() = 82
    override val name: String get() = "LnMtl"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Order by:",
            arrayOf("Favourites", "Name", "Date")
        ),
        Filter.Select(
            "Sort",
            arrayOf("Descending", "Ascending")
        ),
        Filter.Select(
            "Status",
            arrayOf("All", "Ongoing", "Finished")
        )
    )
    
    private val orderValues = arrayOf("favourites", "name", "date")
    private val sortValues = arrayOf("desc", "asc")
    private val statusValues = arrayOf("all", "ongoing", "finished")

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Favourites",
                endpoint = "/novel?orderBy=favourites&order=desc&filter=all&page={page}",
                selector = ".media",
                nameSelector = ".media-title a",
                linkSelector = ".media-title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 80,
            ),
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novel?orderBy=date&order=desc&filter=all&page={page}",
                selector = ".media",
                nameSelector = ".media-title a",
                linkSelector = ".media-title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 80,
            ),
            BaseExploreFetcher(
                "By Name",
                endpoint = "/novel?orderBy=name&order=asc&filter=all&page={page}",
                selector = ".media",
                nameSelector = ".media-title a",
                linkSelector = ".media-title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 80,
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "span.novel-name",
            coverSelector = ".media-left img",
            coverAtt = "src",
            descriptionSelector = ".description p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".table",
            nameSelector = "a .chapter-link",
            linkSelector = "a .chapter-link",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title",
            pageContentSelector = ".translated",
        )

    val jsonDecoder = Json {
        ignoreUnknownKeys = true
    }
    
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return searchNovels(query)
        }
        
        // Handle filters
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val selectFilters = filters.filterIsInstance<Filter.Select>()
        val orderIndex = selectFilters.getOrNull(0)?.value ?: 0
        val statusIndex = selectFilters.getOrNull(1)?.value ?: 0
        
        val orderBy = orderValues.getOrElse(sortIndex) { "favourites" }
        val order = sortValues.getOrElse(orderIndex) { "desc" }
        val status = statusValues.getOrElse(statusIndex) { "all" }
        
        val url = "${baseUrl}novel?orderBy=$orderBy&order=$order&filter=$status&page=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        
        val novels = document.select(".media").mapNotNull { element ->
            val linkElement = element.selectFirst(".media-title a") ?: return@mapNotNull null
            val title = linkElement.text().trim()
            val link = linkElement.attr("href")
            val cover = element.selectFirst("img")?.attr("src") ?: ""
            
            MangaInfo(
                key = link,
                title = title,
                cover = cover
            )
        }
        
        val hasNext = document.select(".pagination li:last-child:not(.disabled)").isNotEmpty()
        return MangasPageInfo(novels, hasNext)
    }
    
    private suspend fun searchNovels(query: String): MangasPageInfo {
        // LNMTL uses a JSON file for search
        val homeHtml = client.get(requestBuilder(baseUrl)).asJsoup()
        val scripts = homeHtml.select("script").html()
        
        // Extract the JSON file path from script
        val jsonPath = Regex("prefetch: '/(.*?\\.json)'").find(scripts)?.groupValues?.get(1)
        if (jsonPath == null) {
            return MangasPageInfo(emptyList(), false)
        }
        
        val jsonUrl = "$baseUrl$jsonPath"
        val jsonText = client.get(requestBuilder(jsonUrl)).asJsoup().body().text()
        
        // Parse JSON dynamically
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val jsonArray = json.parseToJsonElement(jsonText).jsonArray
        
        val novels = jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!name.lowercase().contains(query.lowercase())) return@mapNotNull null
            
            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val image = obj["image"]?.jsonPrimitive?.contentOrNull ?: ""
            
            MangaInfo(
                key = "${baseUrl}novel/$slug",
                title = name,
                cover = image
            )
        }
        
        return MangasPageInfo(novels, false)
    }
    
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if (commands.isEmpty()) {
            val detailHtml = client.get(requestBuilder(manga.key)).asJsoup()
            val scripts = detailHtml.select("script").html()
            val volumeJson = scripts.parseScriptTagVolumne()
            val chaptersJson = scripts.parseScriptTagChapter()
            val firstPageChapterJson = jsonDecoder.decodeFromString<LNMTLResponse>(chaptersJson)
            val volume = jsonDecoder.decodeFromString<List<LNMTLVolumnResponseItem>>(volumeJson)
            val allChapters = mutableListOf<ChapterInfo>()
            allChapters.addAll(firstPageChapterJson.parseResponse())
            for (item in volume) {
                var currentPage = 1
                var maxPage = 1
                while (currentPage <= maxPage) {
                    val json = client.get(requestBuilder("https://lnmtl.com/chapter?page=$currentPage&volumeId=${item.id}")).asJsoup().body().html()
                    Log.error { json.toString() }
                    val parsedJson = jsonDecoder.decodeFromString<LNMTLResponse>(json)
                    maxPage = parsedJson.last_page
                    allChapters.addAll(parsedJson.parseResponse())
                    currentPage++
                }
            }
            return allChapters
        }
        return super.getChapterList(manga, commands)
    }
    fun String.parseScriptTagChapter():String {
        return this.substringBefore(";lnmtl.volumes").substringAfter("lnmtl.firstResponse = ")
    }
    fun String.parseScriptTagVolumne():String {
        return this.substringAfter("lnmtl.volumes = ").substringBefore(";lnmtl.route")
    }

    fun LNMTLResponse.parseResponse() : List<ChapterInfo> {
        return this.data.map {
            ChapterInfo(
                key = it.url,
                name = it.title,
                //   dateUpload = it.updated_at,
                number = it.number.toFloat(),
            )
        }
    }
}
