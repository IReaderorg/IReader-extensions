package ireader.storyseedling

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.Locale

@Extension
abstract class StorySeedling(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://storyseedling.com"
    override val id: Long
        get() = 500 // Choose a unique ID
    override val name: String
        get() = "StorySeedling"

    override fun getFilters(): FilterList = listOf(
        Filter.Title()
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    // This JSON client is needed to handle API responses
    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Recent Novels",
                endpoint = "/browse",
                selector = "div.grid.grid-cols-2.gap-4.md\\:grid-cols-3.lg\\:grid-cols-4 > div",
                nameSelector = "h3",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a.block",
                linkAtt = "href",
                type = SourceFactory.Type.Others
            )
        )

    // Custom implementation needed for popular novels as it uses AJAX
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value

        return if (query != null) {
            // Search implementation using AJAX
            searchMangaPageParse(page, query)
        } else {
            // Default popular novels
            popularMangaPageParse(page)
        }
    }

    private suspend fun popularMangaPageParse(page: Int): MangasPageInfo {
        // First request to get the post value
        val response = client.get(baseUrl + "/browse").asJsoup()
        val postValue = response.select("div[ax-load][x-data]").attr("x-data")
            .replace("browse('", "")
            .replace("')", "")

        // Second request to get the actual novels
        val formData = formData {
            append("search", "")
            append("orderBy", "recent")
            append("curpage", page.toString())
            append("post", postValue)
            append("action", "fetch_browse")
        }

        val jsonResponse = client.submitFormWithBinaryData(
            url = baseUrl + "/ajax",
            formData = formData
        ).body<String>()

        // Parse JSON response
        val jsonObject = Json.decodeFromString<JsonObject>(jsonResponse)
        val posts = jsonObject["data"]?.jsonObject?.get("posts")?.jsonArray

        val novels = posts?.mapNotNull { post ->
            val postObj = post.jsonObject
            val title = postObj["title"]?.toString()?.trim('"') ?: return@mapNotNull null
            val thumbnail = postObj["thumbnail"]?.toString()?.trim('"') ?: ""
            val permalink = postObj["permalink"]?.toString()?.trim('"')?.replace(baseUrl, "") ?: return@mapNotNull null

            MangaInfo(
                key = permalink,
                title = title,
                cover = thumbnail
            )
        } ?: emptyList()

        return MangasPageInfo(
            mangas = novels,
            hasNextPage = page < 10 // Assume 10 pages maximum
        )
    }

    private suspend fun searchMangaPageParse(page: Int, query: String): MangasPageInfo {
        // First request to get the post value
        val response = client.get(baseUrl + "/browse").asJsoup()
        val postValue = response.select("div[ax-load][x-data]").attr("x-data")
            .replace("browse('", "")
            .replace("')", "")

        // Second request to get the actual novels
        val formData = formData {
            append("search", query)
            append("orderBy", "recent")
            append("curpage", page.toString())
            append("post", postValue)
            append("action", "fetch_browse")
        }

        val jsonResponse = client.submitFormWithBinaryData(
            url = baseUrl + "/ajax",
            formData = formData
        ).body<String>()

        // Parse JSON response
        val jsonObject = Json.decodeFromString<JsonObject>(jsonResponse)
        val posts = jsonObject["data"]?.jsonObject?.get("posts")?.jsonArray

        val novels = posts?.mapNotNull { post ->
            val postObj = post.jsonObject
            val title = postObj["title"]?.toString()?.trim('"') ?: return@mapNotNull null
            val thumbnail = postObj["thumbnail"]?.toString()?.trim('"') ?: ""
            val permalink = postObj["permalink"]?.toString()?.trim('"')?.replace(baseUrl, "") ?: return@mapNotNull null

            MangaInfo(
                key = permalink,
                title = title,
                cover = thumbnail
            )
        } ?: emptyList()

        return MangasPageInfo(
            mangas = novels,
            hasNextPage = page < 10 // Assume 10 pages maximum
        )
    }

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "img[x-ref=\"art\"].w-full.rounded.shadow-md",
            coverAtt = "src",
            categorySelector = "section[x-data=\"{ tab: location.hash.substr(1) || 'chapters' }\"].relative > div > div > div.flex.flex-wrap > a",
            descriptionSelector = "div.mb-4.order-2:not(.lg\\:grid-in-buttons)"
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.grid.w-full.grid-cols-1.gap-4.md\\:grid-cols-2 > a",
            nameSelector = ".truncate",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = false,
            onLink = { link ->
                if (link.startsWith(baseUrl)) link.replace(baseUrl, "") else link
            },
            uploadDateSelector = "div > div > small",
            numberSelector = ".truncate",
            onNumber = { name ->
                val chapterNumber = name.split('-')[0].trim().split(' ')[1]
                chapterNumber
            }
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.justify-center > div.mb-4",
        )
} 