package ireader.storyseedling

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
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
abstract class StorySeedling(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://storyseedling.com"
    override val id: Long get() = 500L
    override val name: String get() = "StorySeedling"

    private var nonce: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Order By",
            arrayOf("Recent", "Popular", "A-Z")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // Novel list fetching via AJAX
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val orderBy = when (sortFilter?.value?.index ?: 0) {
            0 -> "recent"
            1 -> "popular"
            2 -> "alphabetical"
            else -> "recent"
        }

        return fetchNovelsViaAjax(page, query ?: "", orderBy)
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return fetchNovelsViaAjax(page, "", "recent")
    }

    private suspend fun fetchNovelsViaAjax(page: Int, search: String, orderBy: String): MangasPageInfo {
        // First request to get the post value from browse page
        val browseDoc = client.get(requestBuilder("$baseUrl/browse")).asJsoup()
        val xData = browseDoc.selectFirst("div[ax-load][x-data]")?.attr("x-data") ?: ""
        val postValue = xData.replace("browse('", "").replace("')", "")

        // Submit form to AJAX endpoint
        val response = client.submitForm(
            url = "$baseUrl/ajax",
            formParameters = Parameters.build {
                append("search", search)
                append("orderBy", orderBy)
                append("curpage", page.toString())
                append("post", postValue)
                append("action", "fetch_browse")
            }
        ) {
            headers {
                append(HttpHeaders.Referrer, "$baseUrl/browse")
            }
        }.bodyAsText()

        val jsonElement = json.parseToJsonElement(response).jsonObject
        val data = jsonElement["data"]?.jsonObject
        val posts = data?.get("posts")?.jsonArray ?: return MangasPageInfo(emptyList(), false)

        val novels = posts.mapNotNull { post ->
            val postObj = post.jsonObject
            val title = postObj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val thumbnail = postObj["thumbnail"]?.jsonPrimitive?.contentOrNull ?: ""
            val permalink = postObj["permalink"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            MangaInfo(
                key = permalink.removePrefix(baseUrl),
                title = title,
                cover = thumbnail,
            )
        }

        // Check if there are more pages
        val hasNextPage = posts.size >= 20

        return MangasPageInfo(novels, hasNextPage)
    }

    // Novel detail parsing from HTML
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("h1")?.text()?.trim() ?: manga.title
        val coverUrl = document.selectFirst("img[x-ref=art].w-full.rounded.shadow-md")?.attr("src")
        val cover = if (!coverUrl.isNullOrBlank()) {
            if (coverUrl.startsWith("http")) coverUrl else "$baseUrl$coverUrl"
        } else {
            manga.cover
        }

        val author = document.selectFirst("div.mb-1 a")?.text()?.trim() ?: ""

        // Parse genres
        val genres = document.select("section[x-data*=tab].relative > div > div > div.flex.flex-wrap > a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        // Parse status
        val rawStatus = document.selectFirst("div.gap-2 span.text-sm")?.text()?.trim()?.lowercase() ?: ""
        val status = when (rawStatus) {
            "ongoing" -> MangaInfo.ONGOING
            "completed" -> MangaInfo.COMPLETED
            "hiatus" -> MangaInfo.ON_HIATUS
            "dropped", "cancelled" -> MangaInfo.CANCELLED
            else -> MangaInfo.UNKNOWN
        }

        // Parse summary
        val summaryDiv = document.selectFirst("div.mb-4.order-2:not(.lg\\:grid-in-buttons)")
        val pTags = summaryDiv?.select("p")
        val description = if (pTags != null && pTags.isNotEmpty()) {
            pTags.joinToString("\n\n") { it.text().trim() }
        } else {
            summaryDiv?.text()?.trim() ?: ""
        }

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            genres = genres,
            status = status,
            description = description,
        )
    }

    // Chapter list fetching via AJAX API
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup())
        }

        // Get novel page to extract x-data for chapter fetching
        val document = client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()

        val xData = document.selectFirst(".bg-accent div[ax-load][x-data]")?.attr("x-data") ?: ""
        // Expected format: toc('000000', 'xxxxxxxxxx')
        val parts = xData.split("'")
        if (parts.size < 4) {
            return parseChaptersFromHtml(document)
        }

        val dataNovelId = parts.getOrNull(1) ?: ""
        val dataNovelN = parts.getOrNull(3) ?: ""

        if (dataNovelId.isBlank() || dataNovelN.isBlank()) {
            return parseChaptersFromHtml(document)
        }

        // Fetch chapters via AJAX
        val response = client.submitForm(
            url = "$baseUrl/ajax",
            formParameters = Parameters.build {
                append("post", dataNovelN)
                append("id", dataNovelId)
                append("action", "series_toc")
            }
        ) {
            headers {
                append(HttpHeaders.Referrer, "$baseUrl${manga.key}")
            }
        }.bodyAsText()

        val jsonElement = json.parseToJsonElement(response).jsonObject
        val data = jsonElement["data"]?.jsonArray ?: return parseChaptersFromHtml(document)

        return data.mapNotNull { element ->
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Chapter"
            val chapterSlug = obj["slug"]?.jsonPrimitive?.contentOrNull
            val chapterNumber = chapterSlug?.toIntOrNull()?.toFloat() ?: -1f

            ChapterInfo(
                name = title,
                key = url.removePrefix(baseUrl),
                number = chapterNumber,
            )
        }
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<ChapterInfo> {
        return document.select("div.grid.w-full.grid-cols-1.gap-4 a, div.grid.grid-cols-1.gap-4 a").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.selectFirst(".truncate")?.text()?.trim() ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href.removePrefix(baseUrl),
            )
        }
    }

    // Chapter content fetching via POST with nonce
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        if (contentFetch != null && contentFetch.html.isNotBlank()) {
            return parseContentFromHtml(contentFetch.html.asJsoup())
        }

        val chapterUrl = "$baseUrl${chapter.key}"

        // Update nonce if needed
        if (nonce == null) {
            updateNonce(chapter.key)
        }

        // Fetch content via POST
        val response = try {
            client.post("$chapterUrl/content") {
                headers {
                    append(HttpHeaders.Referrer, "$chapterUrl/")
                    append("x-nonce", nonce ?: "")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody("""{"captcha_response":""}""")
            }.bodyAsText()
        } catch (e: Exception) {
            // Fallback to HTML parsing
            val doc = client.get(requestBuilder(chapterUrl)).asJsoup()
            return parseContentFromHtml(doc)
        }

        // Check if response is JSON error
        try {
            val jsonElement = json.parseToJsonElement(response).jsonObject
            val success = jsonElement["success"]?.jsonPrimitive?.booleanOrNull
            if (success == false) {
                val message = jsonElement["message"]?.jsonPrimitive?.contentOrNull
                if (message == "Invalid security.") {
                    // Reset nonce and retry
                    nonce = null
                    updateNonce(chapter.key)
                    return getPageList(chapter, commands)
                }
                val hasCaptcha = jsonElement["captcha"]?.jsonPrimitive?.booleanOrNull
                if (hasCaptcha == true) {
                    return listOf(Text("Captcha required. Please read in WebView."))
                }
            }
        } catch (e: Exception) {
            // Not JSON, continue with decoding
        }

        // Decode the obfuscated content
        val decodedHtml = decodeContent(response)
        val doc = decodedHtml.asJsoup()

        // Remove watermarks
        doc.select("span").forEach { span ->
            val text = span.text().lowercase()
            if (text.contains("storyseedling") || text.contains("story seedling")) {
                span.text("")
            }
        }

        return doc.select("p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
            .ifEmpty { listOf(Text(doc.text())) }
    }

    private suspend fun updateNonce(chapterPath: String) {
        val doc = client.get(requestBuilder("$baseUrl$chapterPath")).asJsoup()
        val xData = doc.selectFirst("div.mb-4:has(h1.text-xl) > div")?.attr("x-data") ?: ""
        val match = Regex("""loadChapter\('.+?', '(.+?)'\)""").find(xData)
        nonce = match?.groupValues?.get(1)
    }

    private fun decodeContent(text: String): String {
        // Remove obfuscation class names
        val cleaned = text.replace(Regex("cls[a-f0-9]+"), "")

        // Decode character offsets
        return cleaned.map { char ->
            val code = char.code
            val offset = if (code > 12123) 12027 else 12033
            val decoded = code - offset
            if (decoded in 32..126) {
                decoded.toChar()
            } else {
                char
            }
        }.joinToString("")
    }

    private fun parseContentFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<Page> {
        val content = document.selectFirst("div.justify-center > div.mb-4")
        return content?.select("p")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.map { Text(it) }
            ?: listOf(Text(document.body().text()))
    }

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
    }

    // Declarative fetchers as fallback
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Browse",
                endpoint = "/browse",
                selector = "div.grid.grid-cols-2.gap-4 > div",
                nameSelector = "h3",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a.block",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "img[x-ref=art].w-full.rounded.shadow-md",
            coverAtt = "src",
            authorBookSelector = "div.mb-1 a",
            categorySelector = "section[x-data*=tab].relative > div > div > div.flex.flex-wrap > a",
            descriptionSelector = "div.mb-4.order-2",
            statusSelector = "div.gap-2 span.text-sm",
            onStatus = { status ->
                when (status.lowercase()) {
                    "ongoing" -> MangaInfo.ONGOING
                    "completed" -> MangaInfo.COMPLETED
                    "hiatus" -> MangaInfo.ON_HIATUS
                    "dropped", "cancelled" -> MangaInfo.CANCELLED
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.grid.w-full.grid-cols-1.gap-4 a",
            nameSelector = ".truncate",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.justify-center > div.mb-4 p",
        )
}
