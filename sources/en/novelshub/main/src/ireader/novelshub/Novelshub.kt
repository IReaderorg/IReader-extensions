package ireader.novelshub

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

@Extension
@AutoSourceId(seed = "Novelshub")
abstract class Novelshub(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://novelshub.org"
    override val id: Long get() = NovelshubSourceId.ID
    override val name: String get() = "Novelshub"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/series?page=$page&limit=20"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val meta = json["meta"]?.jsonObject
            val hasMore = meta?.get("hasMore")?.jsonPrimitive?.boolean ?: false

            val mangaList = data.map { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
                val coverImage = obj["coverImage"]?.jsonPrimitive?.content ?: ""
                val fullCover = if (coverImage.startsWith("http")) coverImage else "$baseUrl$coverImage"
                MangaInfo(key = "$baseUrl/series/novel/$slug", title = title, cover = fullCover)
            }

            MangasPageInfo(mangaList, hasMore)
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val query = titleFilter?.value ?: ""

        val endpoint = if (query.isNotBlank()) {
            "$baseUrl/api/series?search=$query&page=$page&limit=20"
        } else {
            "$baseUrl/api/series?page=$page&limit=20"
        }

        return try {
            val response = client.get(requestBuilder(endpoint))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val meta = json["meta"]?.jsonObject
            val hasMore = meta?.get("hasMore")?.jsonPrimitive?.boolean ?: false

            val mangaList = data.map { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
                val coverImage = obj["coverImage"]?.jsonPrimitive?.content ?: ""
                val fullCover = if (coverImage.startsWith("http")) coverImage else "$baseUrl$coverImage"
                MangaInfo(key = "$baseUrl/series/novel/$slug", title = title, cover = fullCover)
            }

            MangasPageInfo(mangaList, hasMore)
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return try {
            val response = client.get(requestBuilder(manga.key))
            val doc = response.asJsoup()

            val title = doc.selectFirst("h1")?.text() ?: manga.title
            val cover = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: manga.cover
            val description = doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
            val author = doc.selectFirst("meta[name=author]")?.attr("content") ?: ""

            manga.copy(
                title = title,
                cover = cover,
                description = description,
                author = author,
            )
        } catch (e: Exception) {
            Log.error { "Error fetching manga detail: ${e.message}" }
            manga
        }
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val slug = manga.key.substringAfterLast("/")
        val allChapters = mutableListOf<ChapterInfo>()

        return try {
            val response = client.get(requestBuilder("$baseUrl/api/series?page=1&limit=100&search=$slug"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return emptyList()

            val series = data.firstOrNull { element ->
                val obj = element.jsonObject
                obj["slug"]?.jsonPrimitive?.content == slug
            }?.jsonObject ?: return emptyList()

            val chapters = series["chapters"]?.jsonArray ?: return emptyList()
            for (chapter in chapters) {
                val obj = chapter.jsonObject
                val chapterTitle = obj["title"]?.jsonPrimitive?.content ?: ""
                val chapterNumber = obj["number"]?.jsonPrimitive?.int ?: 0
                val isLocked = obj["isLocked"]?.jsonPrimitive?.boolean ?: false

                if (!isLocked) {
                    allChapters.add(
                        ChapterInfo(
                            name = "Chapter $chapterNumber: $chapterTitle",
                            key = "$baseUrl/series/novel/$slug/chapter/$chapterNumber",
                        )
                    )
                }
            }

            allChapters.reversed()
        } catch (e: Exception) {
            Log.error { "Error fetching chapters: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return try {
            val response = client.get(requestBuilder(chapter.key))
            val doc = response.asJsoup()

            val content = doc.selectFirst(".chapter-content, .prose, article, #content")?.html() ?: ""
            if (content.isBlank()) {
                return listOf(Text("Chapter content not available or locked."))
            }

            val cleanedContent = content
                .replace(Regex("<br\\s*/?>"), "\n")
                .replace(Regex("<p[^>]*>"), "")
                .replace(Regex("</p>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()

            cleanedContent.split("\n").filter { it.isNotBlank() }.map { Text(it.trim()) }
        } catch (e: Exception) {
            Log.error { "Error fetching page list: ${e.message}" }
            listOf(Text("Error loading chapter content."))
        }
    }
}
