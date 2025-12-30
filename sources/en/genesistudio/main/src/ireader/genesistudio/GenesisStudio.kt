package ireader.genesistudio

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import tachiyomix.annotations.Extension

@Extension
abstract class GenesisStudio(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://genesistudio.com"
    override val id: Long get() = 78L
    override val name: String get() = "Genesis Studio"

    private val apiUrl = "https://api.genesistudio.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Cache for API key (extracted from JS)
    private var supabaseApiKey: String? = null
    private var supabaseUrl: String? = null

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // Novel list fetching via API
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        return if (!query.isNullOrBlank()) {
            searchNovels(query, page)
        } else {
            getPopularNovels(page)
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getPopularNovels(page)
    }

    private suspend fun getPopularNovels(page: Int): MangasPageInfo {
        // Only one page of results
        if (page > 1) return MangasPageInfo(emptyList(), false)

        val url = "$baseUrl/api/directus/novels?status=published&fields=[\"cover\",\"novel_title\",\"abbreviation\"]&limit=-1"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val novels = parseNovelListJson(response)

        return MangasPageInfo(novels, false)
    }

    private suspend fun searchNovels(query: String, page: Int): MangasPageInfo {
        if (page > 1) return MangasPageInfo(emptyList(), false)

        val url = "$baseUrl/api/novels/search?title=${query.encodeURLParameter()}"
        val response = client.get(requestBuilder(url)).bodyAsText()
        val novels = parseNovelListJson(response)

        return MangasPageInfo(novels, false)
    }

    private fun parseNovelListJson(jsonText: String): List<MangaInfo> {
        val jsonArray = json.parseToJsonElement(jsonText).jsonArray

        return jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj["novel_title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val abbreviation = obj["abbreviation"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val coverId = obj["cover"]?.jsonPrimitive?.contentOrNull

            val cover = if (!coverId.isNullOrBlank()) {
                "$apiUrl/storage/v1/object/public/directus/$coverId.png"
            } else {
                ""
            }

            MangaInfo(
                key = "/novels/$abbreviation",
                title = title,
                cover = cover,
            )
        }
    }

    // Novel detail parsing via API
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val abbreviation = manga.key.removePrefix("/novels/")
        val url = "$baseUrl/api/directus/novels/by-abbreviation/$abbreviation"

        val response = client.get(requestBuilder(url)).bodyAsText()
        val obj = json.parseToJsonElement(response).jsonObject

        val title = obj["novel_title"]?.jsonPrimitive?.contentOrNull ?: manga.title
        val author = obj["author"]?.jsonPrimitive?.contentOrNull ?: ""
        val description = obj["synopsis"]?.jsonPrimitive?.contentOrNull ?: ""
        val serialization = obj["serialization"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""

        val status = when (serialization) {
            "ongoing" -> MangaInfo.ONGOING
            "completed" -> MangaInfo.COMPLETED
            "hiatus" -> MangaInfo.ON_HIATUS
            "dropped", "cancelled" -> MangaInfo.CANCELLED
            else -> MangaInfo.UNKNOWN
        }

        // Get cover with correct extension
        val coverId = obj["cover"]?.jsonPrimitive?.contentOrNull
        val cover = if (!coverId.isNullOrBlank()) {
            val coverType = getCoverType(coverId)
            "$apiUrl/storage/v1/object/public/directus/$coverId.$coverType"
        } else {
            manga.cover
        }

        return manga.copy(
            title = title,
            author = author,
            description = description,
            status = status,
            cover = cover,
        )
    }

    private suspend fun getCoverType(coverId: String): String {
        return try {
            val url = "$baseUrl/api/directus-file/$coverId"
            val response = client.get(requestBuilder(url)).bodyAsText()
            val obj = json.parseToJsonElement(response).jsonObject
            val mimeType = obj["type"]?.jsonPrimitive?.contentOrNull ?: "image/png"
            when {
                mimeType.contains("gif") -> "gif"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("webp") -> "webp"
                else -> "png"
            }
        } catch (e: Exception) {
            "png"
        }
    }

    // Chapter list fetching via API
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup())
        }

        // First get novel ID
        val abbreviation = manga.key.removePrefix("/novels/")
        val novelUrl = "$baseUrl/api/directus/novels/by-abbreviation/$abbreviation"
        val novelResponse = client.get(requestBuilder(novelUrl)).bodyAsText()
        val novelObj = json.parseToJsonElement(novelResponse).jsonObject
        val novelId = novelObj["id"]?.jsonPrimitive?.contentOrNull ?: return emptyList()

        // Fetch chapters
        val chaptersUrl = "$baseUrl/api/novels-chapter/$novelId"
        val chaptersResponse = client.get(requestBuilder(chaptersUrl)).bodyAsText()
        val chaptersObj = json.parseToJsonElement(chaptersResponse).jsonObject
        val data = chaptersObj["data"]?.jsonObject
        val chaptersArray = data?.get("chapters")?.jsonArray ?: return emptyList()

        return chaptersArray.mapNotNull { element ->
            val obj = element.jsonObject
            val chapterId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = obj["chapter_title"]?.jsonPrimitive?.contentOrNull ?: "Chapter"
            val chapterNumber = obj["chapter_number"]?.jsonPrimitive?.intOrNull ?: 0
            val isUnlocked = obj["isUnlocked"]?.jsonPrimitive?.booleanOrNull ?: true

            val chapterName = if (!isUnlocked) "🔒 $title" else title

            ChapterInfo(
                name = chapterName,
                key = "/viewer/$chapterId",
                number = chapterNumber.toFloat(),
            )
        }
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<ChapterInfo> {
        return document.select("a[href*=/viewer/]").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href,
            )
        }
    }

    // Chapter content fetching via Supabase API
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        if (contentFetch != null && contentFetch.html.isNotBlank()) {
            return parseContentFromHtml(contentFetch.html.asJsoup())
        }

        val chapterId = chapter.key.removePrefix("/viewer/")

        // Extract API key if not cached
        if (supabaseApiKey == null || supabaseUrl == null) {
            extractSupabaseCredentials(chapter.key)
        }

        if (supabaseApiKey == null || supabaseUrl == null) {
            return listOf(Text("Failed to extract API credentials. Please read in WebView."))
        }

        // Fetch chapter content from Supabase
        val url = "$supabaseUrl/rest/v1/chapters?select=id,chapter_title,chapter_number,chapter_content,status,novel&id=eq.$chapterId&status=eq.released"

        val response = client.get(url) {
            headers {
                append(HttpHeaders.Referrer, baseUrl)
                append("apikey", supabaseApiKey!!)
                append("x-client-info", "supabase-ssr/0.7.0 createBrowserClient")
            }
        }.bodyAsText()

        val jsonArray = json.parseToJsonElement(response).jsonArray
        if (jsonArray.isEmpty()) {
            return listOf(Text("Chapter not found or locked."))
        }

        val chapterObj = jsonArray[0].jsonObject
        val content = chapterObj["chapter_content"]?.jsonPrimitive?.contentOrNull ?: ""

        return content.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private suspend fun extractSupabaseCredentials(chapterPath: String) {
        try {
            val pageUrl = "$baseUrl$chapterPath"
            val document = client.get(requestBuilder(pageUrl)).asJsoup()

            // Find script URLs
            val scriptUrls = document.select("head script[src]")
                .mapNotNull { it.attr("src").takeIf { src -> src.isNotBlank() } }

            for (scriptSrc in scriptUrls) {
                try {
                    val scriptUrl = if (scriptSrc.startsWith("http")) scriptSrc else "$baseUrl$scriptSrc"
                    val scriptContent = client.get(requestBuilder(scriptUrl)).bodyAsText()

                    if (scriptContent.contains("sb_publishable")) {
                        // Extract API URL and key
                        val segments = scriptContent.split(";")
                        for (segment in segments) {
                            if (segment.contains("sb_publishable")) {
                                val parts = segment.split("\"")
                                for (part in parts) {
                                    if (part.startsWith("https")) {
                                        supabaseUrl = part
                                    } else if (part.contains("sb_publishable")) {
                                        supabaseApiKey = part
                                    }
                                }
                                break
                            }
                        }
                        if (supabaseApiKey != null && supabaseUrl != null) break
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            // Failed to extract credentials
        }
    }

    private fun parseContentFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<Page> {
        val content = document.selectFirst("div.chapter-content, article, main")
        return content?.select("p")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.map { Text(it) }
            ?: listOf(Text(document.body().text()))
    }

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "application/json, text/html, */*")
        }
    }

    // Declarative fetchers as fallback
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Browse",
                endpoint = "/browse",
                selector = "div.novel-card, div.grid > div",
                nameSelector = "h3, .title",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true,
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "img.cover, img[alt*=cover]",
            coverAtt = "src",
            authorBookSelector = ".author",
            descriptionSelector = ".synopsis, .description",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a[href*=/viewer/]",
            nameSelector = "span, div",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.chapter-content p, article p",
        )
}
