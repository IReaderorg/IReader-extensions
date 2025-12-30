package ireader.divinedaolibrary

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
abstract class DivineDaoLibrary(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://www.divinedaolibrary.com"
    override val id: Long get() = 95L
    override val name: String get() = "Divine Dao Library"

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
        if (page > 1) return MangasPageInfo(emptyList(), false)
        return fetchAllNovels()
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        if (page > 1) return MangasPageInfo(emptyList(), false)
        
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val allNovels = fetchAllNovels()
        
        if (query.isNullOrBlank()) {
            return allNovels
        }

        val filtered = allNovels.mangas.filter { novel ->
            novel.title.contains(query, ignoreCase = true)
        }

        return MangasPageInfo(filtered, false)
    }

    private suspend fun fetchAllNovels(): MangasPageInfo {
        val document = client.get(requestBuilder("$baseUrl/novels")).asJsoup()
        
        val novels = mutableListOf<MangaInfo>()
        
        document.select(".entry-content ul").forEach { listElement ->
            listElement.select("a").forEach { anchor ->
                val href = anchor.attr("href")
                val name = anchor.text().trim()
                
                if (href.startsWith(baseUrl) && name.isNotBlank()) {
                    val path = href.removePrefix(baseUrl).trim('/').removePrefix("/")
                    if (path.isNotBlank()) {
                        novels.add(MangaInfo(
                            key = path,
                            title = name,
                            cover = "",
                        ))
                    }
                }
            }
        }

        return MangasPageInfo(novels, false)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val response = client.get(requestBuilder("$baseUrl/wp-json/wp/v2/pages?slug=${manga.key}")).bodyAsText()
        val data = json.parseToJsonElement(response).jsonArray
        
        if (data.isEmpty()) return manga

        val pageData = data[0].jsonObject
        val title = pageData["title"]?.jsonObject?.get("rendered")?.jsonPrimitive?.contentOrNull ?: manga.title
        val contentHtml = pageData["content"]?.jsonObject?.get("rendered")?.jsonPrimitive?.contentOrNull ?: ""
        val excerptHtml = pageData["excerpt"]?.jsonObject?.get("rendered")?.jsonPrimitive?.contentOrNull ?: ""

        val contentDoc = contentHtml.asJsoup()
        val excerptDoc = excerptHtml.asJsoup()

        val image = contentDoc.selectFirst("img")
        val cover = image?.attr("data-lazy-src") ?: image?.attr("src") ?: manga.cover

        val author = contentDoc.selectFirst("h3")?.text()
            ?.replace(Regex("^Author:\\s*"), "") ?: ""

        val summary = excerptDoc.selectFirst("p")?.text()
            ?.replace(Regex("^.+Description\\s*"), "") ?: ""

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            description = summary,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val response = client.get(requestBuilder("$baseUrl/wp-json/wp/v2/pages?slug=${manga.key}")).bodyAsText()
        val data = json.parseToJsonElement(response).jsonArray
        
        if (data.isEmpty()) return emptyList()

        val pageData = data[0].jsonObject
        val contentHtml = pageData["content"]?.jsonObject?.get("rendered")?.jsonPrimitive?.contentOrNull ?: ""
        val contentDoc = contentHtml.asJsoup()

        val chapters = contentDoc.select("li > span > a").mapNotNull { anchor ->
            val href = anchor.attr("href")
            val name = anchor.text().trim()
            
            if (href.startsWith(baseUrl) && name.isNotBlank()) {
                val path = href.removePrefix(baseUrl).trim('/').removePrefix("/")
                if (path.isNotBlank()) {
                    ChapterInfo(
                        name = name,
                        key = path,
                    )
                } else null
            } else null
        }

        // Try to find the latest published chapter to filter out unpublished ones
        val latestChapterPath = findLatestChapter(manga.key)
        
        val filteredChapters = if (latestChapterPath != null) {
            val latestIndex = chapters.indexOfFirst { it.key == latestChapterPath }
            if (latestIndex >= 0) {
                chapters.subList(0, latestIndex + 1)
            } else {
                chapters
            }
        } else {
            chapters
        }

        return filteredChapters.mapIndexed { index, chapter ->
            chapter.copy(number = (index + 1).toFloat())
        }
    }

    private suspend fun findLatestChapter(novelPath: String): String? {
        return try {
            val categoryResponse = client.get(requestBuilder("$baseUrl/wp-json/wp/v2/categories?slug=$novelPath")).bodyAsText()
            val categories = json.parseToJsonElement(categoryResponse).jsonArray
            
            if (categories.isEmpty()) return null
            
            val categoryId = categories[0].jsonObject["id"]?.jsonPrimitive?.intOrNull ?: return null
            
            val chapterResponse = client.get(requestBuilder("$baseUrl/wp-json/wp/v2/posts?categories=$categoryId&per_page=1")).bodyAsText()
            val chapters = json.parseToJsonElement(chapterResponse).jsonArray
            
            if (chapters.isEmpty()) return null
            
            chapters[0].jsonObject["slug"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val response = client.get(requestBuilder("$baseUrl/wp-json/wp/v2/posts?slug=${chapter.key}")).bodyAsText()
        val data = json.parseToJsonElement(response).jsonArray
        
        if (data.isEmpty()) return listOf(Text("Error: Chapter not found"))

        val postData = data[0].jsonObject
        val title = postData["title"]?.jsonObject?.get("rendered")?.jsonPrimitive?.contentOrNull ?: ""
        val content = postData["content"]?.jsonObject?.get("rendered")?.jsonPrimitive?.contentOrNull ?: ""

        val fullContent = "<h1>$title</h1>$content"

        return fullContent.split("\n", "</p>", "<p>", "<br>", "<br/>", "<br />")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "All Novels",
                endpoint = "/novels",
                selector = ".entry-content ul li a",
                nameSelector = "a",
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
            authorBookSelector = "h3",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li > span > a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".entry-content p",
        )
}
