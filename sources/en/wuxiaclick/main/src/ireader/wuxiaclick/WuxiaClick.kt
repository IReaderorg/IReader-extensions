package ireader.wuxiaclick

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import ireader.core.source.model.Listing
import ireader.core.source.model.Page
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import tachiyomix.annotations.Extension

@Extension
abstract class WuxiaClick(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://wuxia.click"
    override val id: Long get() = 92
    override val name: String get() = "WuxiaClick"

    private val apiUrl = "https://wuxiaworld.eu/api"
    private val json = Json { ignoreUnknownKeys = true }
    private val itemsPerPage = 20

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf("Latest", "Popular", "Rating")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // Empty exploreFetchers - we override getMangaList instead
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/",
                selector = "a[href*='/novel/']",
                nameSelector = "h5",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 1,
                addBaseUrlToLink = true
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h5",
            coverSelector = "img",
            coverAtt = "src",
            descriptionSelector = "p",
            authorBookSelector = "div",
            categorySelector = "a",
            statusSelector = "div"
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a",
            nameSelector = "span",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false,
            addBaseUrlToLink = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1",
            pageContentSelector = "p"
        )

    // Override getMangaList to use API
    override suspend fun getMangaList(
        sort: Listing?,
        page: Int
    ): MangasPageInfo {
        val offset = (page - 1) * itemsPerPage
        val ordering = when (sort?.name) {
            "Popular" -> "-views"
            "Rating" -> "-rating"
            else -> "-id" // Latest
        }

        return try {
            val response = client.get("$apiUrl/novels?ordering=$ordering&limit=$itemsPerPage&offset=$offset") {
                headers {
                    append("Accept", "application/json")
                    append("User-Agent", getUserAgent())
                }
            }
            val jsonText = response.bodyAsText()
            parseNovelListResponse(jsonText, page)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaList(
        filters: FilterList,
        page: Int
    ): MangasPageInfo {
        val query = filters.filterIsInstance<Filter.Title>().firstOrNull()?.value ?: ""
        val sortFilter = filters.filterIsInstance<Filter.Sort>().firstOrNull()
        val offset = (page - 1) * itemsPerPage

        val ordering = when (sortFilter?.value?.index) {
            1 -> "-views"  // Popular
            2 -> "-rating" // Rating
            else -> "-id"  // Latest
        }

        return try {
            val url = if (query.isNotBlank()) {
                "$apiUrl/novels?search=$query&limit=$itemsPerPage&offset=$offset"
            } else {
                "$apiUrl/novels?ordering=$ordering&limit=$itemsPerPage&offset=$offset"
            }

            val response = client.get(url) {
                headers {
                    append("Accept", "application/json")
                    append("User-Agent", getUserAgent())
                }
            }
            val jsonText = response.bodyAsText()
            parseNovelListResponse(jsonText, page)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    private fun parseNovelListResponse(jsonText: String, page: Int): MangasPageInfo {
        val jsonElement = json.parseToJsonElement(jsonText).jsonObject
        val results = jsonElement["results"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
        val hasNext = jsonElement["next"]?.jsonPrimitive?.content != null

        val novels = results.map { element ->
            val obj = element.jsonObject
            val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
            val name = obj["name"]?.jsonPrimitive?.content ?: ""
            val image = obj["image"]?.jsonPrimitive?.content ?: ""
            val description = obj["description"]?.jsonPrimitive?.content ?: ""

            MangaInfo(
                key = "$baseUrl/novel/$slug",
                title = name,
                cover = image,
                description = description
            )
        }

        return MangasPageInfo(novels, hasNext)
    }

    // Override getMangaDetails to use API
    override suspend fun getMangaDetails(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): MangaInfo {
        val slug = manga.key.substringAfterLast("/novel/").removeSuffix("/")

        return try {
            val response = client.get("$apiUrl/novels/$slug") {
                headers {
                    append("Accept", "application/json")
                    append("User-Agent", getUserAgent())
                }
            }
            val jsonText = response.bodyAsText()
            val obj = json.parseToJsonElement(jsonText).jsonObject

            val name = obj["name"]?.jsonPrimitive?.content ?: manga.title
            val image = obj["image"]?.jsonPrimitive?.content ?: manga.cover
            val description = obj["description"]?.jsonPrimitive?.content ?: ""
            val author = obj["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
            val categories = obj["categories"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            } ?: emptyList()

            MangaInfo(
                key = manga.key,
                title = name,
                cover = image,
                description = description,
                author = author,
                genres = categories,
                status = MangaInfo.UNKNOWN
            )
        } catch (e: Exception) {
            manga
        }
    }

    // Override getChapterList to use API
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        val slug = manga.key.substringAfterLast("/novel/").removeSuffix("/")

        return try {
            val response = client.get("$apiUrl/chapters/$slug") {
                headers {
                    append("Accept", "application/json")
                    append("User-Agent", getUserAgent())
                }
            }
            val jsonText = response.bodyAsText()
            val jsonElement = json.parseToJsonElement(jsonText)

            when (jsonElement) {
                is JsonArray -> {
                    jsonElement.mapIndexed { index, element ->
                        val obj = element.jsonObject
                        val chapterSlug = obj["novSlugChapSlug"]?.jsonPrimitive?.content ?: ""
                        val title = obj["title"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                        val chapterIndex = obj["index"]?.jsonPrimitive?.intOrNull?.toFloat()
                            ?: (index + 1).toFloat()

                        ChapterInfo(
                            key = "$baseUrl/chapter/$chapterSlug",
                            name = title,
                            number = chapterIndex
                        )
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }


    // Fetch chapter content from HTML page
    override suspend fun getPageList(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): List<Page> {
        return try {
            val response = client.get(chapter.key) {
                headers {
                    append("Accept", "text/html")
                    append("Referer", baseUrl)
                    append("User-Agent", getUserAgent())
                }
            }
            val html = response.bodyAsText()
            val doc = html.asJsoup()

            // Content is in div elements with id="chapterText"
            val paragraphs = doc.select("#chapterText, [id=chapterText]")
                .map { it.text() }
                .filter { text ->
                    text.isNotBlank() && text.length > 2
                }

            if (paragraphs.isNotEmpty()) {
                paragraphs.toPage()
            } else {
                // Fallback to p tags
                val fallback = doc.select("p")
                    .map { it.text() }
                    .filter { text ->
                        text.isNotBlank() &&
                        !text.contains("WuxiaClick", ignoreCase = true) &&
                        text.length > 10
                    }
                if (fallback.isNotEmpty()) {
                    fallback.toPage()
                } else {
                    listOf("Content not available").toPage()
                }
            }
        } catch (e: Exception) {
            listOf("Error loading content: ${e.message}").toPage()
        }
    }

    override fun getUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
