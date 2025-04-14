package ireader.libread

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class LibRead(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://libread.com"
    override val id: Long
        get() = 90 // Choose a unique ID
    override val name: String
        get() = "LibRead"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Select(
            "Novel Type/Genre",
            arrayOf(
                "All",
                "═══NOVEL TYPES═══",
                "Chinese Novel",
                "Korean Novel",
                "Japanese Novel",
                "English Novel",
                "═══GENRES═══",
                "Action",
                "Adult",
                "Adventure",
                "Comedy",
                "Drama",
                "Eastern",
                "Ecchi",
                "Fantasy",
                "Game",
                "Gender Bender",
                "Harem",
                "Historical",
                "Horror",
                "Josei",
                "Martial Arts",
                "Mature",
                "Mecha",
                "Mystery",
                "Psychological",
                "Reincarnation",
                "Romance",
                "School Life",
                "Sci-fi",
                "Seinen",
                "Shoujo",
                "Shounen Ai",
                "Shounen",
                "Slice of Life",
                "Smut",
                "Sports",
                "Supernatural",
                "Tragedy",
                "Wuxia",
                "Xianxia",
                "Xuanhuan",
                "Yaoi",
            )
        ),
        Filter.Sort(
            "Sort By:",
            arrayOf(
                "Popular",
                "Latest Novels"
            )
        ),
    )

    private val typeGenreValues = arrayOf(
        "all",
        "/sort/latest-release/",
        "/sort/latest-release/chinese-novel/",
        "/sort/latest-release/korean-novel/",
        "/sort/latest-release/japanese-novel/",
        "/sort/latest-release/english-novel/",
        "genre",
        "/genre/Action/",
        "/genre/Adult/",
        "/genre/Adventure/",
        "/genre/Comedy/",
        "/genre/Drama/",
        "/genre/Eastern/",
        "/genre/Ecchi/",
        "/genre/Fantasy/",
        "/genre/Game/",
        "/genre/Gender+Bender/",
        "/genre/Harem/",
        "/genre/Historical/",
        "/genre/Horror/",
        "/genre/Josei/",
        "/genre/Martial+Arts/",
        "/genre/Mature/",
        "/genre/Mecha/",
        "/genre/Mystery/",
        "/genre/Psychological/",
        "/genre/Reincarnation/",
        "/genre/Romance/",
        "/genre/School+Life/",
        "/genre/Sci-fi/",
        "/genre/Seinen/",
        "/genre/Shoujo/",
        "/genre/Shounen+Ai/",
        "/genre/Shounen/",
        "/genre/Slice+of+Life/",
        "/genre/Smut/",
        "/genre/Sports/",
        "/genre/Supernatural/",
        "/genre/Tragedy/",
        "/genre/Wuxia/",
        "/genre/Xianxia/",
        "/genre/Xuanhuan/",
        "/genre/Yaoi/",
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    // This client is needed for search functionality
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
                "Most Popular",
                endpoint = "/most-popular/{page}",
                selector = ".li-row",
                nameSelector = ".tit",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 10
            ),
            BaseExploreFetcher(
                "Latest Novels",
                endpoint = "/sort/latest-novels/{page}",
                selector = ".li-row",
                nameSelector = ".tit",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 10
            )
        )

    // Override to handle search and filter functionality
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        
        if (query != null) {
            // Search implementation
            return searchMangaPageParse(query)
        } else {
            // Check if any filters are active
            val typeGenreFilter = filters.findInstance<Filter.Select>()
            val sortFilter = filters.findInstance<Filter.Sort>()
            
            if (typeGenreFilter != null && typeGenreFilter.value > 0 &&
                typeGenreFilter.value != 1 && typeGenreFilter.value != 6) {
                // Type/Genre filter is active
                val typeGenreValue = typeGenreValues[typeGenreFilter.value]
                if (typeGenreValue != "all" && typeGenreValue != "genre") {
                    val url = baseUrl + typeGenreValue + page
                    val document = client.get(url).asJsoup()
                    return parseNovelsFromDocument(document)
                }
            }
            
            // Sort filter
            if (sortFilter != null) {
                // Latest Novels
                val url = baseUrl + "/sort/latest-novels/" + page
                val document = client.get(url).asJsoup()
                return parseNovelsFromDocument(document)
            }
            
            // Default to most popular if no specific filter is selected
            return super.getMangaList(filters, page)
        }
    }
    
    private suspend fun searchMangaPageParse(query: String): MangasPageInfo {
        val response = client.post(baseUrl + "/search/") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("searchkey=$query")
        }
        
        val document = response.asJsoup()
        return parseNovelsFromDocument(document)
    }
    
    private fun parseNovelsFromDocument(document: Document): MangasPageInfo {
        val novels = document.select(".li-row").map { element ->
            val name = element.select(".tit").text()
            val url = element.select("h3 > a").attr("href")
            val coverUrl = element.select("img").attr("src")
            
            MangaInfo(
                key = url,
                title = name,
                cover = coverUrl
            )
        }
        
        return MangasPageInfo(
            mangas = novels,
            hasNextPage = novels.isNotEmpty() // Assume there are more pages if we found novels
        )
    }

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.tit",
            coverSelector = ".pic > img",
            coverAtt = "src",
            authorBookSelector = "dt[title=Author] + dd",
            categorySelector = "dt[title=Genre] + dd",
            descriptionSelector = ".inner",
            statusSelector = "dt[title=Status] + dd",
            onStatus = { status ->
                when {
                    status.contains("Complete", ignoreCase = true) -> MangaInfo.COMPLETED
                    status.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "#idData > li",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false,
            // If no title is present, use the index to generate chapter name
            onName = { name ->
                if (name.isBlank()) "Chapter " else name
            }
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.txt",
            // Remove the last paragraph if it contains a site signature
            onContent = { content ->
                if (content.lastOrNull()?.contains("libread", ignoreCase = true) == true) {
                    content.dropLast(1)
                } else {
                    content
                }
            }
        )
} 