package ireader.fenrir

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

/**
 * 🐺 Fenrir Realm Source - JSON API Based
 */
@Extension
abstract class Fenrir(deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://fenrirealm.com"
    override val id: Long get() = 1971001510155696709L
    override val name: String get() = "Fenrir Realm"

    // ═══════════════════════════════════════════════════════════════
    // 🔍 FILTERS & COMMANDS
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // No exploreFetchers - we use API instead
    override val exploreFetchers: List<BaseExploreFetcher> = emptyList()

    // ═══════════════════════════════════════════════════════════════
    // 📖 DETAIL FETCHER (for WebView fallback)
    // ═══════════════════════════════════════════════════════════════
    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = "h1.my-2",
            coverSelector = "img.rounded-md",
            coverAtt = "src",
            authorBookSelector = "div.flex-1 > div.mb-3 > a.inline-flex",
            descriptionSelector = "div.overflow-hidden.transition-all p",
            categorySelector = "div.flex-1 > div.flex:not(.mb-3, .mt-5) > a",
            statusSelector = "div.flex-1 > div.mb-3 > span.rounded-md",
            addBaseurlToCoverLink = true
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CHAPTER FETCHER (for WebView fallback)
    // ═══════════════════════════════════════════════════════════════
    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = "li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true
        )

    // ═══════════════════════════════════════════════════════════════
    // 📄 CONTENT FETCHER (for WebView fallback)
    // ═══════════════════════════════════════════════════════════════
    override val contentFetcher: Content
        get() = Content(
            pageContentSelector = "[id^=\"reader-area-\"] p, [id^=\"reader-area-\"] div.paragraph"
        )

    // ═══════════════════════════════════════════════════════════════
    // 🌐 API-BASED NOVEL LISTING
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getMangaList(sort: ireader.core.source.model.Listing?, page: Int): MangasPageInfo {
        val order = if (sort?.name == "Latest") "latest" else "popular"
        return getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&status=any&order=$order")
    }

    override suspend fun getMangaList(filters: ireader.core.source.model.FilterList, page: Int): MangasPageInfo {
        val query = filters.filterIsInstance<ireader.core.source.model.Filter.Title>().firstOrNull()?.value ?: ""
        return if (query.isNotEmpty()) {
            getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&search=$query")
        } else {
            getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&status=any&order=popular")
        }
    }

    private suspend fun getFromAPI(url: String): MangasPageInfo {
        val responseJson = client.get(requestBuilder(url)).bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<APIResponse>(responseJson)

        val mangas = response.data.map { novel ->
            MangaInfo(
                key = "$baseUrl/series/${novel.slug}",
                title = novel.title,
                cover = "$baseUrl/${novel.cover}",
                description = novel.description ?: "",
                status = when (novel.status?.lowercase()) {
                    "ongoing" -> 1
                    "completed" -> 2
                    else -> 0
                },
                genres = novel.genres?.map { it.name } ?: emptyList()
            )
        }
        return MangasPageInfo(mangas, hasNextPage = mangas.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 📚 API-BASED CHAPTER FETCHING
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val novelSlug = manga.key.removePrefix("$baseUrl/series/")
        val chaptersJson = client.get(requestBuilder("$baseUrl/api/novels/chapter-list/$novelSlug")).bodyAsText()

        val json = Json { ignoreUnknownKeys = true }
        val chapters = json.decodeFromString<List<APIChapter>>(chaptersJson)

        return chapters.map { c ->
            val chapterName =
                (if (c.locked?.price != null) "🔒 " else "") +
                (if (c.group?.index == null) "" else "Vol ${c.group.index} ") +
                "Chapter ${c.number}" +
                (if (!c.title.isNullOrBlank() && c.title.trim() != "Chapter ${c.number}")
                    " - ${c.title.replace(Regex("^chapter [0-9]+ . ", RegexOption.IGNORE_CASE), "")}"
                else "")

            val chapterPath = manga.key +
                (if (c.group?.index == null) "" else "/${c.group.slug}") +
                "/chapter-${c.number}"

            ChapterInfo(name = chapterName, key = chapterPath)
        }.sortedBy { it.name }
    }

    // ═══════════════════════════════════════════════════════════════
    // 📄 CONTENT PARSING
    // ═══════════════════════════════════════════════════════════════
    override fun pageContentParse(document: Document): List<Page> {
        val chapter = document.select("[id^=\"reader-area-\"]").first() ?: return emptyList()

        // Remove HTML comments and unwanted elements
        chapter.childNodes().filter { it.nodeName() == "#comment" }.forEach { it.remove() }
        chapter.select("script, style, .ads, .advertisement").remove()

        return chapter.select("p, div.paragraph, div.text").mapNotNull { element ->
            val text = element.text().trim()
            if (text.isNotEmpty()) Text(text) else null
        }.ifEmpty {
            listOf(Text(chapter.text()))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 📦 API DATA CLASSES
// ═══════════════════════════════════════════════════════════════
@Serializable
data class APIResponse(val data: List<APINovel>)

@Serializable
data class APINovel(
    val title: String,
    val slug: String,
    val cover: String,
    val description: String? = null,
    val status: String? = null,
    val genres: List<Genre>? = null
)

@Serializable
data class Genre(val name: String)

@Serializable
data class APIChapter(
    val locked: Locked? = null,
    val group: Group? = null,
    val title: String? = null,
    val number: Int,
    val created_at: String
)

@Serializable
data class Locked(val price: Int? = null)

@Serializable
data class Group(val index: Int? = null, val slug: String? = null)
