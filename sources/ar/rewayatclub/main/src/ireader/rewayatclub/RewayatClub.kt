package ireader.rewayatclub

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import tachiyomix.annotations.Extension

@Extension
abstract class RewayatClub(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://rewayat.club"
    override val id: Long get() = 43L
    override val name: String get() = "Rewayat Club"

    private val apiUrl = "https://api.rewayat.club"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "الترتيب",
            arrayOf(
                "عدد الفصول (تنازلي)",
                "عدد الفصول (تصاعدي)",
                "الاسم (أ-ي)",
                "الاسم (ي-أ)",
            )
        ),
        Filter.Select(
            "الفئات",
            arrayOf(
                "جميع الروايات",
                "مترجمة",
                "مؤلفة",
                "مكتملة",
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // Novel list fetching via API
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$apiUrl/api/chapters/weekly/list/?page=$page"
        val response = client.get(requestBuilder(url)).bodyAsText()
        return parseNovelListJson(response)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            return searchNovels(query, page)
        }

        val sortFilter = filters.findInstance<Filter.Sort>()
        val categoryFilter = filters.findInstance<Filter.Select>()

        val sortIndex = sortFilter?.value?.index ?: 0
        val categoryIndex = categoryFilter?.value ?: 0

        val ordering = when (sortIndex) {
            0 -> "-num_chapters"
            1 -> "num_chapters"
            2 -> "english"
            3 -> "-english"
            else -> "-num_chapters"
        }

        val url = buildString {
            append("$apiUrl/api/novels/?")
            append("type=$categoryIndex")
            append("&ordering=$ordering")
            append("&page=$page")
        }

        val response = client.get(requestBuilder(url)).bodyAsText()
        return parseNovelListJson(response)
    }

    private suspend fun searchNovels(query: String, page: Int): MangasPageInfo {
        val url = "$apiUrl/api/novels/?type=0&ordering=-num_chapters&page=$page&search=$query"
        val response = client.get(requestBuilder(url)).bodyAsText()
        return parseNovelListJson(response)
    }

    private fun parseNovelListJson(jsonText: String): MangasPageInfo {
        val jsonElement = json.parseToJsonElement(jsonText).jsonObject
        val results = jsonElement["results"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
        val hasNext = jsonElement["next"]?.jsonPrimitive?.contentOrNull != null

        val novels = results.mapNotNull { element ->
            val obj = element.jsonObject

            // Try to get data from nested novel object first, then from root
            val nestedNovel = obj["novel"]?.jsonObject

            val title = obj["arabic"]?.jsonPrimitive?.contentOrNull
                ?: nestedNovel?.get("arabic")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull
                ?: nestedNovel?.get("slug")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val posterUrl = obj["poster_url"]?.jsonPrimitive?.contentOrNull
                ?: nestedNovel?.get("poster_url")?.jsonPrimitive?.contentOrNull

            val cover = if (!posterUrl.isNullOrBlank()) {
                "$apiUrl/${posterUrl.removePrefix("/")}"
            } else {
                ""
            }

            MangaInfo(
                key = "$baseUrl/novel/$slug",
                title = title,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, hasNext)
    }

    // Novel detail parsing from HTML
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder(manga.key)).asJsoup()
        }

        val title = document.selectFirst("h1.primary--text span")?.text()?.trim() ?: manga.title
        val author = document.selectFirst(".novel-author")?.text()?.trim() ?: ""
        val description = document.selectFirst("div.text-pre-line span")?.text()?.trim() ?: ""

        // Extract cover from NUXT script
        val scriptContent = document.selectFirst("body script:contains(__NUXT__)")?.data() ?: ""
        val posterMatch = Regex("""poster_url:"(\\u002F[^"]+)"""").find(scriptContent)
        val posterPath = posterMatch?.groupValues?.get(1)
            ?.replace("\\u002F", "/")
            ?.removePrefix("/")
        val cover = if (!posterPath.isNullOrBlank()) {
            "$apiUrl/$posterPath"
        } else {
            manga.cover
        }

        // Parse genres
        val mainGenres = document.select(".v-slide-group__content a").map { it.text().trim() }
        val statusWords = setOf("مكتملة", "متوقفة", "مستمرة")
        val statusChips = document.select("div.v-slide-group__content span.v-chip__content")
            .map { it.text().trim() }
            .filter { it in statusWords }

        val genres = (statusChips + mainGenres).filter { it.isNotBlank() }

        // Parse status
        val statusText = statusChips.firstOrNull() ?: ""
        val status = when (statusText) {
            "مكتملة" -> MangaInfo.COMPLETED
            "مستمرة" -> MangaInfo.ONGOING
            "متوقفة" -> MangaInfo.ON_HIATUS
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            author = author,
            description = description,
            cover = cover,
            genres = genres,
            status = status,
        )
    }

    // Chapter list fetching via API with pagination
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup(), manga.key)
        }

        // Get novel slug from URL
        val novelSlug = manga.key.substringAfter("/novel/").substringBefore("/")

        // First, get total chapter count from novel page
        val document = client.get(requestBuilder(manga.key)).asJsoup()
        val chapterCountText = document.selectFirst("div.v-tab--active span.mr-1")?.text() ?: "0"
        val totalChapters = chapterCountText.replace(Regex("[^\\d]"), "").toIntOrNull() ?: 0
        val totalPages = (totalChapters + 23) / 24 // 24 chapters per page, ceiling division

        val chapters = mutableListOf<ChapterInfo>()

        for (page in 1..maxOf(1, totalPages)) {
            try {
                val chaptersUrl = "$apiUrl/api/chapters/$novelSlug/?ordering=number&page=$page"
                val response = client.get(requestBuilder(chaptersUrl)).bodyAsText()
                val jsonElement = json.parseToJsonElement(response).jsonObject
                val results = jsonElement["results"]?.jsonArray ?: break

                results.forEach { element ->
                    val obj = element.jsonObject
                    val number = obj["number"]?.jsonPrimitive?.intOrNull ?: 0
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Chapter $number"

                    chapters.add(
                        ChapterInfo(
                            name = title,
                            key = "$baseUrl/novel/$novelSlug/$number",
                            number = number.toFloat(),
                        )
                    )
                }

                val hasNext = jsonElement["next"]?.jsonPrimitive?.contentOrNull != null
                if (!hasNext) break
            } catch (e: Exception) {
                break
            }
        }

        return chapters
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document, novelKey: String): List<ChapterInfo> {
        val novelSlug = novelKey.substringAfter("/novel/").substringBefore("/")
        return document.select(".chapter-list li a, ul.chapters li a").mapNotNull { element ->
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = element.attr("href")
            val chapterNum = href.substringAfterLast("/").toIntOrNull() ?: 0
            ChapterInfo(
                name = name,
                key = if (href.startsWith("http")) href else "$baseUrl$href",
                number = chapterNum.toFloat(),
            )
        }
    }

    // Chapter content fetching via API
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        if (contentFetch != null && contentFetch.html.isNotBlank()) {
            return parseContentFromHtml(contentFetch.html.asJsoup())
        }

        // Extract chapter path: /novel/slug/number -> slug/number
        val chapterPath = chapter.key.substringAfter("/novel/")
        val apiChapterUrl = "$apiUrl/api/chapters/$chapterPath"

        val response = client.get(requestBuilder(apiChapterUrl)).bodyAsText()
        val jsonElement = json.parseToJsonElement(response).jsonObject
        val contentArray = jsonElement["content"]?.jsonArray ?: return emptyList()

        // Flatten nested arrays and join content
        val contentParts = mutableListOf<String>()
        contentArray.forEach { element ->
            when (element) {
                is JsonArray -> {
                    element.forEach { inner ->
                        inner.jsonPrimitive.contentOrNull?.let { contentParts.add(it) }
                    }
                }
                else -> {
                    element.jsonPrimitive.contentOrNull?.let { contentParts.add(it) }
                }
            }
        }

        val content = contentParts
            .joinToString("\n\n")
            .replace(Regex("\\n"), "")
            .replace("<p>", "\n")
            .trim()

        return content.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private fun parseContentFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<Page> {
        return document.select(".chapter-content p, .content p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "application/json, text/html, */*")
        }
    }

    // Declarative fetchers as fallback
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/",
                selector = ".novel-item",
                nameSelector = ".title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                addBaseUrlToLink = true,
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.primary--text span",
            coverSelector = "img",
            coverAtt = "src",
            descriptionSelector = "div.text-pre-line span",
            authorBookSelector = ".novel-author",
            categorySelector = ".v-slide-group__content a",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".chapter-content p",
        )
}
