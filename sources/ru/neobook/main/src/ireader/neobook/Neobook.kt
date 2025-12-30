package ireader.neobook

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
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
abstract class Neobook(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ru"
    override val baseUrl: String get() = "https://neobook.org"
    private val apiUrl: String get() = "https://api.neobook.org/"
    override val id: Long get() = 84L
    override val name: String get() = "Neobook"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Сортировка:",
            arrayOf("Сначала популярные", "Сначала новые", "В случайном порядке")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return fetchNovels(page, "popular", "")
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim() ?: ""
        val sortFilter = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val sortValue = when (sortFilter) {
            0 -> "popular"
            1 -> "new"
            2 -> "rand"
            else -> "popular"
        }
        return fetchNovels(page, sortValue, query)
    }

    private suspend fun fetchNovels(page: Int, sort: String, searchTerm: String): MangasPageInfo {
        val response = client.submitForm(
            url = apiUrl,
            formParameters = Parameters.build {
                append("version", "4.4")
                append("uid", "0")
                append("utoken", "")
                append("resource", "general")
                append("action", "get_bundle")
                append("bundle", "bundle_books")
                append("target", "feed")
                append("page", page.toString())
                append("filter_category_id", "0")
                append("filter_completed", "-1")
                append("filter_search", searchTerm)
                append("filter_tags", "")
                append("filter_sort", sort)
                append("filter_timeread", "0-999999")
            }
        ) {
            headers {
                append(HttpHeaders.Referrer, baseUrl)
            }
        }.bodyAsText()

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val bundleBooks = jsonObj["bundle_books"]?.jsonObject
        val feed = bundleBooks?.get("feed")?.jsonArray ?: return MangasPageInfo(emptyList(), false)

        val novels = feed.mapNotNull { element ->
            val novel = element.jsonObject
            val title = novel["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val token = novel["token"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val attachment = novel["attachment"]?.jsonObject
            val image = attachment?.get("image")?.jsonObject
            val cover = image?.get("m")?.jsonPrimitive?.contentOrNull ?: ""

            MangaInfo(
                key = "$token/",
                title = title,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val body = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html
        } else {
            val novelPath = manga.key.trimEnd('/')
            client.get(requestBuilder("$baseUrl/book/$novelPath/")).bodyAsText()
        }

        val bookRaw = Regex("""var postData = (\{.*?\});""").find(body)?.groupValues?.get(1)
            ?: return manga

        val book = json.parseToJsonElement(bookRaw).jsonObject

        val title = book["title"]?.jsonPrimitive?.contentOrNull ?: manga.title
        val description = book["text"]?.jsonPrimitive?.contentOrNull
            ?.replace("<br>", "\n")
            ?: book["text_fix"]?.jsonPrimitive?.contentOrNull ?: ""
        
        val user = book["user"]?.jsonObject
        val author = buildString {
            val firstname = user?.get("firstname")?.jsonPrimitive?.contentOrNull ?: ""
            val lastname = user?.get("lastname")?.jsonPrimitive?.contentOrNull ?: ""
            if (firstname.isNotBlank() || lastname.isNotBlank()) {
                append("$firstname $lastname".trim())
            } else {
                append(user?.get("initials")?.jsonPrimitive?.contentOrNull ?: "")
            }
        }

        val statusCode = book["status"]?.jsonPrimitive?.contentOrNull ?: "0"
        val status = when (statusCode) {
            "1" -> MangaInfo.ONGOING
            "2" -> MangaInfo.COMPLETED
            "3" -> MangaInfo.ON_HIATUS
            "4" -> MangaInfo.CANCELLED
            else -> MangaInfo.UNKNOWN
        }

        val attachment = book["attachment"]?.jsonObject
        val image = attachment?.get("image")?.jsonObject
        val cover = image?.get("m")?.jsonPrimitive?.contentOrNull ?: manga.cover

        val tags = book["tags"]?.jsonArray?.mapNotNull { 
            it.jsonPrimitive.contentOrNull 
        } ?: emptyList()

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = tags,
            status = status,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        val body = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html
        } else {
            val novelPath = manga.key.trimEnd('/')
            client.get(requestBuilder("$baseUrl/book/$novelPath/")).bodyAsText()
        }

        val bookRaw = Regex("""var postData = (\{.*?\});""").find(body)?.groupValues?.get(1)
            ?: return emptyList()

        val book = json.parseToJsonElement(bookRaw).jsonObject
        val token = book["token"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val chaptersArray = book["chapters"]?.jsonArray ?: return emptyList()

        return chaptersArray.mapIndexedNotNull { index, element ->
            val chapter = element.jsonObject
            val access = chapter["access"]?.jsonPrimitive?.contentOrNull
            val chapterStatus = chapter["status"]?.jsonPrimitive?.contentOrNull
            
            if (access != "1" || chapterStatus != "1") return@mapIndexedNotNull null

            val chapterToken = chapter["token"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val title = chapter["title"]?.jsonPrimitive?.contentOrNull ?: "Глава ${index + 1}"
            val sort = chapter["sort"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: (index + 1)

            ChapterInfo(
                name = title,
                key = "?book=$token&chapter=$chapterToken",
                number = sort.toFloat(),
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val body = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html
        } else {
            client.get(requestBuilder("$baseUrl/reader/${chapter.key}")).bodyAsText()
        }

        val dataRaw = Regex("""var data = (\{.*?\});""").find(body)?.groupValues?.get(1)
            ?: return listOf(Text("Error: Could not find chapter data"))

        val data = json.parseToJsonElement(dataRaw).jsonObject
        val chapterToken = chapter.key.substringAfter("chapter=")
        val chaptersArray = data["chapters"]?.jsonArray ?: return listOf(Text("Error: No chapters found"))

        val chapterData = chaptersArray.firstOrNull { element ->
            element.jsonObject["token"]?.jsonPrimitive?.contentOrNull == chapterToken
        }?.jsonObject

        val chapterContent = chapterData?.get("data")?.jsonObject
        val html = chapterContent?.get("html")?.jsonPrimitive?.contentOrNull
            ?.replace("<br>", "")
            ?: return listOf(Text("Error: No content found"))

        return html.split("\n", "</p>", "<p>")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/",
                selector = "div",
                nameSelector = "h1",
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
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "p",
        )
}
