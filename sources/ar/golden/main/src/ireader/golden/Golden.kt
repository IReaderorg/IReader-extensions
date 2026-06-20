package ireader.golden

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.ImageUrl
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.json.*
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@Extension
@AutoSourceId(seed = "Golden")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "love",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://golden.rest/api/mangas/2262",
    chapterUrl = "https://golden.rest/api/releases/2463",
    expectedTitle = "Deranged Marriage",
    expectedMinChapters = 10
)
@TestExpectations(
    minLatestNovels = 5,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class Golden(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://golden.rest"
    override val id: Long get() = GoldenSourceId.ID
    override val name: String get() = "Golden"

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = emptyList()

    override fun getListings(): List<Listing> = listOf(LatestListing())

    class LatestListing : Listing("Latest")

    private fun buildCoverUrl(id: Int, coverFile: String?): String {
        if (coverFile.isNullOrBlank()) return ""
        if (coverFile.startsWith("http")) return coverFile
        return "$baseUrl/uploads/$coverFile"
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/mangas?orderBy=created&orderDir=desc&page=$page&limit=50"))
            val body = response.bodyAsText()
            val arr = jsonParser.parseToJsonElement(body).jsonArray
            val mangaList = arr.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val isNovel = obj["is_novel"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!isNovel) return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val coverFile = obj["cover"]?.jsonPrimitive?.contentOrNull
                val cover = buildCoverUrl(id, coverFile)
                MangaInfo(
                    key = id.toString(),
                    title = title,
                    cover = cover,
                    description = "",
                    status = MangaInfo.UNKNOWN
                )
            }.distinctBy { it.key }
            MangasPageInfo(mangaList, mangaList.size >= 50)
        } catch (e: Exception) {
            Log.error { "Error fetching manga list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val query = titleFilter?.value?.takeIf { it.isNotBlank() }
        if (query != null) {
            return try {
                val allResults = mutableListOf<MangaInfo>()
                for (p in 1..20) {
                    val response = client.get(requestBuilder("$baseUrl/api/mangas?orderBy=created&orderDir=desc&page=$p&limit=50"))
                    val body = response.bodyAsText()
                    val arr = jsonParser.parseToJsonElement(body).jsonArray
                    if (arr.isEmpty()) break
                    arr.filter { el ->
                        val obj = el.jsonObject
                        val isNovel = obj["is_novel"]?.jsonPrimitive?.booleanOrNull ?: false
                        if (!isNovel) return@filter false
                        val title = obj["title"]?.jsonPrimitive?.content ?: ""
                        val arabicTitle = obj["arabic_title"]?.jsonPrimitive?.contentOrNull ?: ""
                        val english = obj["english"]?.jsonPrimitive?.contentOrNull ?: ""
                        val synonyms = obj["synonyms"]?.jsonPrimitive?.contentOrNull ?: ""
                        title.contains(query, ignoreCase = true) ||
                            arabicTitle.contains(query, ignoreCase = true) ||
                            english.contains(query, ignoreCase = true) ||
                            synonyms.contains(query, ignoreCase = true)
                    }.forEach { el ->
                        val obj = el.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
                        val title = obj["title"]?.jsonPrimitive?.content ?: return@forEach
                        val coverFile = obj["cover"]?.jsonPrimitive?.contentOrNull
                        val cover = buildCoverUrl(id, coverFile)
                        allResults.add(MangaInfo(key = id.toString(), title = title, cover = cover))
                    }
                }
                MangasPageInfo(allResults.distinctBy { it.key }, false)
            } catch (e: Exception) {
                MangasPageInfo(emptyList(), false)
            }
        }
        return getMangaList(null, page)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/mangas/${manga.key}"))
            val body = response.bodyAsText()
            parseDetailsFromJson(body, manga)
        } catch (e: Exception) {
            Log.error { "Error fetching manga details: ${e.message}" }
            manga
        }
    }

    private fun parseDetailsFromJson(jsonStr: String, manga: MangaInfo): MangaInfo {
        return try {
            val obj = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val data = obj["mangaData"]?.jsonObject ?: obj
            val title = data["title"]?.jsonPrimitive?.content ?: manga.title
            val summary = data["summary"]?.jsonPrimitive?.contentOrNull ?: ""
            val coverFile = data["cover"]?.jsonPrimitive?.contentOrNull
            val cover = if (!coverFile.isNullOrBlank()) buildCoverUrl(manga.key.toIntOrNull() ?: 0, coverFile) else manga.cover
            val storyStatus = data["story_status"]?.jsonPrimitive?.intOrNull
            val status = when (storyStatus) {
                1 -> MangaInfo.ONGOING
                3 -> MangaInfo.COMPLETED
                else -> MangaInfo.UNKNOWN
            }
            val categories = data["categories"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            } ?: emptyList()
            val authors = data["authors"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            } ?: emptyList()
            val typeObj = data["type"]?.jsonObject
            val typeName = typeObj?.get("title")?.jsonPrimitive?.contentOrNull ?: ""
            val desc = buildString {
                if (summary.isNotBlank()) append(summary)
                if (typeName.isNotBlank()) append("\nالنوع: $typeName")
                if (categories.isNotEmpty()) append("\nالتصنيفات: ${categories.joinToString(", ")}")
            }
            manga.copy(
                title = title,
                cover = cover,
                description = desc.trim(),
                author = authors.joinToString(", "),
                genres = categories,
                status = status
            )
        } catch (e: Exception) {
            Log.error { "Error parsing manga details: ${e.message}" }
            manga
        }
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/mangas/${manga.key}/releases"))
            val body = response.bodyAsText()
            parseChaptersFromJson(body, manga)
        } catch (e: Exception) {
            Log.error { "Error fetching chapters: ${e.message}" }
            emptyList()
        }
    }

    private fun parseChaptersFromJson(jsonStr: String, manga: MangaInfo): List<ChapterInfo> {
        return try {
            val obj = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val releases = obj["releases"]?.jsonArray ?: return emptyList()
            releases.mapNotNull { el ->
                val release = el.jsonObject
                val releaseId = release["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val chapter = release["chapter"]?.jsonPrimitive?.intOrNull ?: 0
                val volume = release["volume"]?.jsonPrimitive?.intOrNull ?: 0
                val title = release["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val chapterName = buildString {
                    if (volume > 0) append("Vol.$volume ")
                    append("Ch.$chapter")
                    if (title.isNotBlank()) append(" - $title")
                }
                ChapterInfo(name = chapterName, key = releaseId.toString())
            }.sortedBy { ch ->
                Regex("Ch\\.(\\d+)").find(ch.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            Log.error { "Error parsing chapters: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return try {
            val response = client.get(requestBuilder("$baseUrl/api/releases/${chapter.key}"))
            val body = response.bodyAsText()
            parseContentFromJson(body)
        } catch (e: Exception) {
            Log.error { "Error fetching pages: ${e.message}" }
            listOf(Text("محتوى الفصل غير متاح"))
        }
    }

    private fun parseContentFromJson(jsonStr: String): List<Page> {
        return try {
            val obj = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val storageKey = obj["storage_key"]?.jsonPrimitive?.contentOrNull ?: ""
            val pages = obj["pages"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val contentText = obj["content"]?.jsonPrimitive?.contentOrNull

            if (!contentText.isNullOrBlank()) {
                val doc = com.fleeksoft.ksoup.Ksoup.parse(contentText)
                val paragraphs = doc.select("p, br + text, div").map { it.text().trim() }.filter { it.isNotBlank() }
                if (paragraphs.isNotEmpty()) {
                    return paragraphs.map { Text(it) }
                }
                val plainText = contentText
                    .replace(Regex("<br\\s*/?>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&#8211;", "–")
                    .replace("&#8217;", "'")
                    .replace("&#8230;", "...")
                    .replace(Regex("\\n{3,}"), "\n\n")
                val lines = plainText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    return lines.map { Text(it) }
                }
            }

            if (pages.isNotEmpty() && storageKey.isNotBlank()) {
                return pages.map { page ->
                    ImageUrl("$baseUrl/uploads/releases/$storageKey/$page")
                }
            }

            listOf(Text("محتوى الفصل غير متاح"))
        } catch (e: Exception) {
            Log.error { "Error parsing pages: ${e.message}" }
            listOf(Text("محتوى الفصل غير متاح"))
        }
    }
}
