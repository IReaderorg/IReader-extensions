package ireader.novelbuddy

import io.ktor.client.request.*
import ireader.core.source.*
import ireader.core.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Extension
abstract class Novelbuddy(private val deps: Dependencies) : ParsedHttpSource(deps) {
    override val name = "NovelBuddy.io"
    override val id: Long = 1260875580122894435L
    override val baseUrl = "https://novelbuddy.io"
    override val lang = "en"

    // Helper for parsing novel lists
    private fun parseNovels(document: Document): List<MangaInfo> {
        return document.select(".book-item").map { element ->
            val novelUrl = element.selectFirst(".title a")?.attr("href")?.removePrefix("/")
                ?: throw Exception("Novel URL not found")
            MangaInfo(
                key = novelUrl,
                title = element.selectFirst(".title")!!.text(),
                cover = element.selectFirst("img")!!.attr("data-src").let {
                    if (it.startsWith("//")) "https:$it" else "https:$it"
                }
            )
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val params = mutableMapOf<String, String>()
        params["sort"] = "views"
        params["status"] = "all"
        params["q"] = ""
        params["page"] = page.toString()

        val url = "$baseUrl/search?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }

        val response = client.get(requestBuilder(url)).bodyAsText()
        val document = Jsoup.parse(response)
        val novels = parseNovels(document)
        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val params = mutableMapOf<String, String>()

        val orderBy = filters.find { it.name == "Order by" } as TextPicker
        val keyword = filters.find { it.name == "Keywords" } as TextField
        val status = filters.find { it.name == "Status" } as TextPicker
        val genre = filters.find { it.name == "Genres (OR, not AND)" } as GroupList

        params["sort"] = orderBy.value
        params["status"] = status.value
        genre.values.forEach {
            params["genre[]"] = it
        }
        params["q"] = keyword.value
        params["page"] = page.toString()

        val url = "$baseUrl/search?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }

        val response = client.get(requestBuilder(url)).bodyAsText()
        val document = Jsoup.parse(response)
        val novels = parseNovels(document)
        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val doc = client.get(requestBuilder("$baseUrl/${manga.key}")).asJsoup()

        // Extract novel ID for chapter fetching
        val novelId = doc.selectFirst("script:containsData(bookId)")
            ?.data()?.let { Regex("bookId = (\\d+)").find(it)?.groupValues?.get(1) }
            ?: throw Exception("Novel ID not found")

        // Fetch chapters from API if needed
        val chapters = if (novelId != null) {
            val chaptersHtml = client.get(requestBuilder("$baseUrl/api/manga/$novelId/chapters?source=detail")).bodyAsText()
            val chaptersDoc = Jsoup.parse(chaptersHtml)
            // Parse chapters using EXACT selectors from TS
            chaptersDoc.select("li").map { chapterElement ->
                val chapterName = chapterElement.selectFirst(".chapter-title")!!.text().trim()
                val releaseDate = chapterElement.selectFirst(".chapter-update")!!.text().trim()

                val months = listOf(
                    "jan", "feb", "mar", "apr", "may", "jun",
                    "jul", "aug", "sep", "oct", "nov", "dec"
                )
                val monthsJoined = months.joinToString("|")
                val rx = Regex("($monthsJoined) (\\d{1,2}), (\\d{4})", RegexOption.IGNORE_CASE).find(releaseDate)

                val year = rx?.groupValues?.get(3)?.toInt() ?: 1970
                val month = months.indexOf(rx?.groupValues?.get(1)?.lowercase())
                val day = rx?.groupValues?.get(2)?.toInt() ?: 1

                val chapterUrl = chapterElement.selectFirst("a")?.attr("href")?.removePrefix("/")
                    ?: throw Exception("Chapter URL not found")

                ChapterInfo(
                    key = chapterUrl,
                    name = chapterName,
                    dateUpload = java.util.Calendar.getInstance().apply {
                        set(year, month, day)
                    }.timeInMillis
                )
            }
        } else emptyList()

        val authorElements = doc.select(".meta.box p")
            .firstOrNull { it.selectFirst("strong")?.text() == "Authors :" }
            ?.select("a span")
            ?.map { it.text() }
            ?.joinToString(", ") ?: ""

        val statusElement = doc.select(".meta.box p")
            .firstOrNull { it.selectFirst("strong")?.text() == "Status :" }
            ?.selectFirst("a")?.text() ?: ""

        val genresElement = doc.select(".meta.box p")
            .firstOrNull { it.selectFirst("strong")?.text() == "Genres :" }
            ?.selectFirst("a")?.text()?.trim() ?: ""

        return manga.copy(
            title = doc.selectFirst(".name h1")!!.text().trim() ,
            cover = "https:" + doc.selectFirst(".img-cover img")!!.attr("data-src"),
            description = doc.selectFirst(".section-body.summary .content")!!.text().trim(),
            author = authorElements,
            status = when (statusElement) {
                "Ongoing" -> Manga.ONGOING
                "Completed" -> Manga.COMPLETED
                else -> Manga.UNKNOWN
            },
            genres = listOf(genresElement),
            chapters = chapters.reversed()
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        return emptyList()
    }

    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        val doc = client.get(requestBuilder("$baseUrl/${chapter.key}")).asJsoup()
        // Use EXACT selectors from parseChapter in TS
        doc.select("#listen-chapter, #google_translate_element").remove()
        return listOf(doc.selectFirst(".chapter__content")!!.html())
    }

    override suspend fun getSearchMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.find { it.name == "Search" } as TextField
        val searchTerm = query.value
        val encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8.toString())
        val url = "$baseUrl/search?q=$encodedSearchTerm&page=$page"

        val response = client.get(requestBuilder(url)).bodyAsText()
        val document = Jsoup.parse(response)
        val novels = parseNovels(document)
        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    override val filterList: FilterList = FilterList(
        TextPicker(
            name = "Order by",
            values = listOf(
                "Views" to "views",
                "Updated At" to "updated_at",
                "Created At" to "created_at",
                "Name" to "name",
                "Rating" to "rating"
            ),
            defaultValue = "views"
        ),
        TextField(name = "Keywords", defaultValue = ""),
        TextPicker(
            name = "Status",
            values = listOf(
                "All" to "all",
                "Ongoing" to "ongoing",
                "Completed" to "completed"
            ),
            defaultValue = "all"
        ),
        GroupList(
            name = "Genres (OR, not AND)",
            values = listOf(
                "Action" to "action",
                "Action Adventure" to "action-adventure",
                "Adult" to "adult",
                "Adventcure" to "adventcure",
                "Adventure" to "adventure",
                "Adventurer" to "adventurer",
                "Bender" to "bender",
                "Chinese" to "chinese",
                "Comedy" to "comedy",
                "Cultivation" to "cultivation",
                "Drama" to "drama",
                "Eastern" to "eastern",
                "Ecchi" to "ecchi",
                "Fan Fiction" to "fan-fiction",
                "Fanfiction" to "fanfiction",
                "Fantas" to "fantas",
                "Fantasy" to "fantasy",
                "Game" to "game",
                "Gender" to "gender",
                "Gender Bender" to "gender-bender",
                "Harem" to "harem",
                "HaremAction" to "haremaction",
                "Haremv" to "haremv",
                "Historica" to "historica",
                "Historical" to "historical",
                "History" to "history",
                "Horror" to "horror",
                "Isekai" to "isekai",
                "Josei" to "josei",
                "Lolicon" to "lolicon",
                "Magic" to "magic",
                "Martial" to "martial",
                "Martial Arts" to "martial-arts",
                "Mature" to "mature",
                "Mecha" to "mecha",
                "Military" to "military",
                "Modern Life" to "modern-life",
                "Mystery" to "mystery",
                "Mystery Adventure" to "mystery-adventure",
                "Psychologic" to "psychologic",
                "Psychological" to "psychological",
                "Reincarnation" to "reincarnation",
                "Romance" to "romance",
                "Romance Adventure" to "romance-adventure",
                "Romance Harem" to "romance-harem",
                "Romancem" to "romancem",
                "School Life" to "school-life",
                "Sci-fi" to "sci-fi",
                "Seinen" to "seinen",
                "Shoujo" to "shoujo",
                "Shoujo Ai" to "shoujo-ai",
                "Shounen" to "shounen",
                "Shounen Ai" to "shounen-ai",
                "Slice of Life" to "slice-of-life",
                "Smut" to "smut",
                "Sports" to "sports",
                "Superna" to "superna",
                "Supernatural" to "supernatural",
                "System" to "system",
                "Tragedy" to "tragedy",
                "Urban" to "urban",
                "Urban Life" to "urban-life",
                "Wuxia" to "wuxia",
                "Xianxia" to "xianxia",
                "Xuanhuan" to "xuanhuan",
                "Yaoi" to "yaoi",
                "Yuri" to "yuri"
            ),
            defaultSelection = emptyList()
        ),
        TextField(name = "Search", defaultValue = "")
    )
}