package ireader.novelight

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tachiyomix.annotations.Extension

@Extension
abstract class Novelight(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://novelight.net"
    override val id: Long get() = 80L
    override val name: String get() = "Novelight"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By",
            arrayOf("Popularity", "Latest Update", "Publication Date", "Title (A-Z)")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // Novel list fetching from catalog
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val sortIndex = sortFilter?.value?.index ?: 0

        val ordering = when (sortIndex) {
            0 -> "popularity"
            1 -> "-time_updated"
            2 -> "-time_created"
            3 -> "title"
            else -> "popularity"
        }

        val url = if (!query.isNullOrBlank()) {
            "$baseUrl/catalog/?search=${query.encodeURLParameter()}"
        } else {
            "$baseUrl/catalog/?ordering=$ordering&page=$page"
        }

        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/catalog/?ordering=popularity&page=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("a.item").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = element.selectFirst("div.title")?.text()?.trim() ?: return@mapNotNull null
            val coverPath = element.selectFirst("img")?.attr("src") ?: ""
            val cover = if (coverPath.isNotBlank()) "$baseUrl$coverPath" else ""

            MangaInfo(
                key = href.removePrefix("/"),
                title = title,
                cover = cover,
            )
        }

        val hasNextPage = document.selectFirst("a.next, a[rel=next]") != null

        return MangasPageInfo(novels, hasNextPage)
    }

    // Novel detail parsing
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("h1")?.text()?.trim() ?: manga.title
        val coverPath = document.selectFirst(".poster > img")?.attr("src") ?: ""
        val cover = if (coverPath.isNotBlank()) "$baseUrl$coverPath" else manga.cover
        val description = document.selectFirst("section.text-info.section > p")?.text()?.trim() ?: ""

        // Parse info items
        var author = ""
        var status = MangaInfo.UNKNOWN
        val genres = mutableListOf<String>()

        document.select("div.mini-info > .item").forEach { item ->
            val type = item.selectFirst(".sub-header")?.text()?.trim() ?: ""
            val info = item.selectFirst("div.info")

            when (type) {
                "Author" -> author = info?.text()?.trim() ?: ""
                "Status" -> {
                    val statusText = info?.text()?.trim()?.lowercase() ?: ""
                    status = when (statusText) {
                        "releasing" -> MangaInfo.ONGOING
                        "completed" -> MangaInfo.COMPLETED
                        "cancelled" -> MangaInfo.CANCELLED
                        else -> MangaInfo.UNKNOWN
                    }
                }
                "Translation" -> {
                    val translationStatus = info?.text()?.trim()?.lowercase() ?: ""
                    if (translationStatus == "ongoing" && status == MangaInfo.UNKNOWN) {
                        status = MangaInfo.ONGOING
                    }
                }
                "Genres" -> {
                    info?.select("a")?.forEach { genre ->
                        genres.add(genre.text().trim())
                    }
                }
            }
        }

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            description = description,
            genres = genres,
            status = status,
        )
    }

    // Chapter list fetching via AJAX pagination
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup())
        }

        val novelUrl = "$baseUrl/${manga.key}"
        val rawBody = client.get(requestBuilder(novelUrl)).bodyAsText()

        // Extract CSRF token and book ID
        val csrfToken = Regex("""window\.CSRF_TOKEN = "([^"]+)"""").find(rawBody)?.groupValues?.get(1) ?: ""
        val bookId = Regex("""const OBJECT_BY_COMMENT = (\d+)""").find(rawBody)?.groupValues?.get(1) ?: ""

        // Get total pages
        val totalPagesMatch = Regex("""<option value="(\d+)"""").findAll(rawBody).lastOrNull()
        val totalPages = totalPagesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val chapters = mutableListOf<ChapterInfo>()

        // Fetch all pages (in reverse order as per TypeScript)
        for (page in totalPages downTo 1) {
            try {
                val ajaxUrl = "$baseUrl/book/ajax/chapter-pagination?csrfmiddlewaretoken=$csrfToken&book_id=$bookId&page=$page"
                val response = client.get(ajaxUrl) {
                    headers {
                        append(HttpHeaders.Referrer, novelUrl)
                        append("X-Requested-With", "XMLHttpRequest")
                    }
                }.bodyAsText()

                val jsonObj = json.parseToJsonElement(response).jsonObject
                val html = jsonObj["html"]?.jsonPrimitive?.contentOrNull ?: continue

                val chapterDoc = "<html>$html</html>".asJsoup()
                chapterDoc.select("a").forEach { element ->
                    val title = element.selectFirst(".title")?.text()?.trim() ?: "Chapter"
                    val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@forEach
                    val isLocked = element.selectFirst(".cost")?.text()?.isNotBlank() == true
                    val chapterName = if (isLocked) "🔒 $title" else title

                    chapters.add(
                        ChapterInfo(
                            name = chapterName,
                            key = href,
                        )
                    )
                }
            } catch (e: Exception) {
                continue
            }
        }

        return chapters.reversed()
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<ChapterInfo> {
        return document.select("a[href*=/chapter/]").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.selectFirst(".title")?.text()?.trim() ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href,
            )
        }
    }

    // Chapter content fetching via AJAX
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        if (contentFetch != null && contentFetch.html.isNotBlank()) {
            return parseContentFromHtml(contentFetch.html.asJsoup())
        }

        val chapterUrl = if (chapter.key.startsWith("http")) chapter.key else "$baseUrl${chapter.key}"
        val rawBody = client.get(requestBuilder(chapterUrl)).bodyAsText()

        // Extract CSRF token and chapter ID
        val csrfToken = Regex("""window\.CSRF_TOKEN = "([^"]+)"""").find(rawBody)?.groupValues?.get(1) ?: ""
        val chapterId = Regex("""const CHAPTER_ID = "(\d+)"""").find(rawBody)?.groupValues?.get(1) ?: ""

        if (chapterId.isBlank()) {
            return listOf(Text("Failed to extract chapter ID. Please read in WebView."))
        }

        // Fetch chapter content via AJAX
        val response = client.get("$baseUrl/book/ajax/read-chapter/$chapterId") {
            headers {
                append(HttpHeaders.Cookie, "csrftoken=$csrfToken")
                append(HttpHeaders.Referrer, chapterUrl)
                append("X-Requested-With", "XMLHttpRequest")
            }
        }.bodyAsText()

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val content = jsonObj["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val className = jsonObj["class"]?.jsonPrimitive?.contentOrNull ?: ""

        val contentDoc = content.asJsoup()
        val chapterContent = contentDoc.selectFirst(".$className")?.html() ?: content

        // Remove advertisements
        val cleanedContent = chapterContent.replace(Regex("""class="advertisment""""), """style="display:none;"""")

        return cleanedContent.split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private fun parseContentFromHtml(document: com.fleeksoft.ksoup.nodes.Document): List<Page> {
        return document.select("div.chapter-content p, article p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
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
                "Popular",
                endpoint = "/catalog/?ordering=popularity&page={page}",
                selector = "a.item",
                nameSelector = "div.title",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
                addBaseurlToCoverLink = true,
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = ".poster > img",
            coverAtt = "src",
            authorBookSelector = "div.mini-info .item:contains(Author) div.info",
            descriptionSelector = "section.text-info.section > p",
            categorySelector = "div.mini-info .item:contains(Genres) div.info a",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a[href*=/chapter/]",
            nameSelector = ".title",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.chapter-content p",
        )
}
