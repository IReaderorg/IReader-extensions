package ireader.azorafly

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
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
import kotlinx.serialization.json.*
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@Extension
@AutoSourceId(seed = "AzoraFly")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "Baylands",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://azorafly.com/series/13-baylands-street",
    chapterUrl = "https://azorafly.com/series/13-baylands-street/chapter-58",
    expectedTitle = "13 Baylands Street",
    expectedMinChapters = 10
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 10,
    supportsPagination = true,
    requiresLogin = false
)
abstract class AzoraFly(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://azorafly.com"
    override val id: Long get() = AzoraFlySourceId.ID
    override val name: String get() = "AzoraFly"

    private val apiBase = "https://api.azorafly.com/api"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun requestBuilder(url: String, block: HeadersBuilder.() -> Unit): HttpRequestBuilder {
        return super.requestBuilder(url, block).apply {
            headers {
                append(HttpHeaders.Origin, "https://azorafly.com")
                append(HttpHeaders.Referrer, "https://azorafly.com/")
            }
        }
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override fun getListings(): List<Listing> = listOf(
        PopularTodayListing(),
        TrendingListing(),
        NewListing(),
    )

    class PopularTodayListing : Listing("الشائع اليوم")
    class TrendingListing : Listing("رائج")
    class NewListing : Listing("جديد")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val (orderBy, orderDirection) = when (sort) {
            is TrendingListing -> "views" to "desc"
            is NewListing -> "createdAt" to "desc"
            else -> "popularity" to "desc"
        }
        return fetchNovels(page, null, orderBy, orderDirection)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.takeIf { it.isNotBlank() }
        return fetchNovels(page, query, "lastChapterAddedAt", "desc")
    }

    /**
     * Fetch novel list or search via the query API.
     * Only the NOVEL series type is included.
     */
    private suspend fun fetchNovels(
        page: Int,
        query: String?,
        orderBy: String = "lastChapterAddedAt",
        orderDirection: String = "desc"
    ): MangasPageInfo {
        return try {
            val perPage = 18
            val url = buildString {
                append("$apiBase/query?page=$page&perPage=$perPage&view=archive")
                append("&seriesType=NOVEL&orderBy=$orderBy&orderDirection=$orderDirection")
                if (!query.isNullOrBlank()) append("&searchTerm=${query.encodeURLParameter()}")
            }
            val body = client.get(requestBuilder(url)).bodyAsText()
            val obj = jsonParser.parseToJsonElement(body).jsonObject
            val total = obj["totalCount"]?.jsonPrimitive?.intOrNull ?: 0
            val posts = obj["posts"]?.jsonArray ?: emptyList()
            val mangas = posts.mapNotNull { el ->
                val post = el.jsonObject
                val id = post["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val title = post["postTitle"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val cover = post["featuredImage"]?.jsonPrimitive?.contentOrNull ?: ""
                val slug = post["slug"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val status = parseStatus(post["seriesStatus"]?.jsonPrimitive?.contentOrNull)
                val genres = post["genres"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                    ?: emptyList()
                MangaInfo(
                    key = slug?.let { "$baseUrl/series/$it" } ?: id.toString(),
                    title = title,
                    cover = cover,
                    genres = genres,
                    status = status,
                )
            }.distinctBy { it.key }
            MangasPageInfo(mangas, hasNextPage = page * perPage < total)
        } catch (e: Exception) {
            Log.error { "Error fetching AzoraFly list: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        return try {
            val identifier = manga.slugOrId()
            val url = if (identifier.all(Char::isDigit)) {
                "$apiBase/post?postId=$identifier"
            } else {
                "$apiBase/post?postSlug=$identifier"
            }
            val body = client.get(requestBuilder(url)).bodyAsText()
            parseDetailsFromJson(body, manga)
        } catch (e: Exception) {
            Log.error { "Error fetching AzoraFly details: ${e.message}" }
            manga
        }
    }

    private fun parseDetailsFromJson(jsonStr: String, manga: MangaInfo): MangaInfo {
        return try {
            val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val post = root["post"]?.jsonObject ?: return manga
            val title = post["postTitle"]?.jsonPrimitive?.contentOrNull ?: manga.title
            val cover = post["featuredImage"]?.jsonPrimitive?.contentOrNull ?: manga.cover
            val contentHtml = post["postContent"]?.jsonPrimitive?.contentOrNull ?: ""
            val description = contentHtml
                .replace(Regex("<br\\s*/?>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
            val alternativeTitles = post["alternativeTitles"]?.jsonPrimitive?.contentOrNull ?: ""
            val author = post["author"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: post["createdby"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                ?: ""
            val status = parseStatus(post["seriesStatus"]?.jsonPrimitive?.contentOrNull)
            val genres = post["genres"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?: emptyList()
            val fullDesc = buildString {
                if (description.isNotBlank()) append(description)
                if (alternativeTitles.isNotBlank()) append("\n\nالأسماء البديلة: $alternativeTitles")
                if (genres.isNotEmpty()) append("\n\nالتصنيفات: ${genres.joinToString("، ")}")
            }
            manga.copy(
                title = title,
                cover = cover,
                description = fullDesc.trim(),
                author = author,
                genres = genres,
                status = status,
            )
        } catch (e: Exception) {
            Log.error { "Error parsing AzoraFly details: ${e.message}" }
            manga
        }
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        return try {
            val postId = resolvePostId(manga.slugOrId()) ?: return emptyList()
            val allChapters = mutableListOf<ChapterInfo>()
            var skip = 0
            val take = 500
            var mangaSlug = ""
            while (true) {
                val body = client.get(
                    requestBuilder("$apiBase/chapters?postId=$postId&skip=$skip&take=$take&order=asc&view=archive")
                ).bodyAsText()
                val root = jsonParser.parseToJsonElement(body).jsonObject
                val total = root["totalChapterCount"]?.jsonPrimitive?.intOrNull ?: 0
                val chapters = root["post"]?.jsonObject?.get("chapters")?.jsonArray ?: emptyList()
                if (chapters.isEmpty()) break
                chapters.forEach { el ->
                    val ch = el.jsonObject
                    val chapterSlug = ch["slug"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (mangaSlug.isBlank()) {
                        mangaSlug = ch["mangaPost"]?.jsonObject?.get("slug")?.jsonPrimitive?.contentOrNull ?: ""
                    }
                    val number = ch["number"]?.jsonPrimitive?.intOrNull ?: -1
                    val rawTitle = ch["title"]?.jsonPrimitive?.contentOrNull
                    val name = when {
                        rawTitle.isNullOrBlank() -> if (number > 0) "الفصل $number" else chapterSlug
                        rawTitle.startsWith("Chapter ", true) -> "الفصل $number"
                        else -> rawTitle
                    }
                    val url = if (mangaSlug.isNotBlank()) {
                        "$baseUrl/series/$mangaSlug/$chapterSlug"
                    } else {
                        "$baseUrl/series/$chapterSlug"
                    }
                    allChapters.add(
                        ChapterInfo(
                            name = name,
                            key = url,
                            number = number.toFloat(),
                            type = ChapterInfo.NOVEL,
                        )
                    )
                }
                if (allChapters.size >= total) break
                skip += take
            }
            allChapters
        } catch (e: Exception) {
            Log.error { "Error fetching AzoraFly chapters: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                val pages = parseContentFromHtml(cmd.html)
                if (pages.isNotEmpty()) return pages
            }
        }
        return try {
            val body = client.get(requestBuilder(chapter.key)).bodyAsText()
            val pages = parseContentFromHtml(body)
            if (pages.isNotEmpty()) pages else listOf(Text("محتوى الفصل غير متاح"))
        } catch (e: Exception) {
            Log.error { "Error fetching AzoraFly content: ${e.message}" }
            listOf(Text("محتوى الفصل غير متاح"))
        }
    }

    /**
     * Extract the novel chapter content from the chapter page HTML.
     * The content HTML is embedded in the Astro island props as a JSON string
     * under "content":[0,"..."].
     */
    private fun parseContentFromHtml(html: String): List<Page> {
        return try {
            val marker = "&quot;content&quot;:[0,&quot;"
            val start = html.indexOf(marker)
            if (start < 0) return emptyList()
            val propsStart = html.lastIndexOf("props=\"", start)
            if (propsStart < 0) return emptyList()
            val attrStart = propsStart + "props=\"".length
            val attrEnd = html.indexOf('"', attrStart)
            if (attrEnd < 0) return emptyList()
            val raw = html.substring(attrStart, attrEnd)
            val jsonText = raw
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
            val root = jsonParser.parseToJsonElement(jsonText).jsonObject
            val contentHtml = root["chapter"]?.jsonArray
                ?.getOrNull(1)?.jsonObject
                ?.get("content")?.jsonArray
                ?.getOrNull(1)?.jsonPrimitive?.contentOrNull
            if (contentHtml.isNullOrBlank()) return emptyList()
            val doc = com.fleeksoft.ksoup.Ksoup.parse(contentHtml)
            val paragraphs = doc.select("p").map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 1 }
            if (paragraphs.isNotEmpty()) return paragraphs.map { Text(it) }
            emptyList()
        } catch (e: Exception) {
            Log.error { "Error parsing AzoraFly content: ${e.message}" }
            emptyList()
        }
    }

    private fun parseStatus(status: String?): Long = when (status?.trim()?.uppercase()) {
        "ONGOING" -> MangaInfo.ONGOING
        "COMPLETED" -> MangaInfo.COMPLETED
        else -> MangaInfo.UNKNOWN
    }

    /**
     * The novel key is the web page URL (e.g. https://azorafly.com/series/<slug>)
     * so the app can open it in the WebView. Extract the slug from it, falling
     * back to the legacy numeric post id for books saved before the fix.
     */
    private fun MangaInfo.slugOrId(): String {
        val key = this.key
        return if (key.startsWith("$baseUrl/series/")) {
            key.removePrefix("$baseUrl/series/").substringBefore("/")
        } else {
            key
        }
    }

    private suspend fun resolvePostId(identifier: String): String? {
        if (identifier.all(Char::isDigit)) return identifier
        return try {
            val body = client.get(requestBuilder("$apiBase/post?postSlug=$identifier")).bodyAsText()
            jsonParser.parseToJsonElement(body).jsonObject["post"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.intOrNull
                ?.toString()
        } catch (e: Exception) {
            Log.error { "Error resolving AzoraFly postId for $identifier: ${e.message}" }
            null
        }
    }
}
