package ireader.fenrir

import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class Fenrir(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://fenrirealm.com"
    override val id: Long get() = 1971001510155696709L
    override val name: String get() = "Fenrir Realm"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher> = emptyList()

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.my-2",
            coverSelector = "img.rounded-md",
            coverAtt = "src",
            descriptionSelector = "div.overflow-hidden.transition-all.max-h-\\[108px\\] p",
            authorBookSelector = "div.flex-1 > div.mb-3 > a.inline-flex",
            statusSelector = "div.flex-1 > div.mb-3 > span.rounded-md",
            categorySelector = "div.flex-1 > div.flex:not(.mb-3, .mt-5) > a",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "[id^=\"reader-area-\"]",
        )

    override fun pageContentParse(document: Document): List<Page> {
        val chapter = document.select("[id^=\"reader-area-\"]").first() ?: return emptyList()
        
        // Remove HTML comments
        val nodesToRemove = chapter.childNodes().filter { it.nodeName() == "#comment" }
        nodesToRemove.forEach { it.remove() }
        
        // Remove unwanted elements
        chapter.select("script, style, .ads, .advertisement").remove()
        
        // Split content into paragraphs
        return chapter.select("p, div.paragraph, div.text").mapNotNull { element ->
            val text = element.text().trim()
            if (text.isNotEmpty()) Text(text) else null
        }.ifEmpty {
            // Fallback: if no paragraphs found, return all text
            listOf(Text(chapter.text()))
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val order = if (sort?.name == "Latest") "latest" else "popular"
        return getFromAPI("$baseUrl/api/series/filter?page=$page&per_page=20&status=any&order=$order")
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value ?: ""
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

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        // manga.key is "https://fenrirealm.com/series/novel-slug", extract just the slug
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

            val chapterPath =
                manga.key +
                (if (c.group?.index == null) "" else "/${c.group.slug}") +
                "/chapter-${c.number}"

            ChapterInfo(
                name = chapterName,
                key = chapterPath
            )
        }.sortedBy { it.name }
    }
}

@Serializable
data class APIResponse(
    val data: List<APINovel>
)

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
data class Genre(
    val name: String
)

@Serializable
data class APIChapter(
    val locked: Locked? = null,
    val group: Group? = null,
    val title: String? = null,
    val number: Int,
    val created_at: String
)

@Serializable
data class Locked(
    val price: Int? = null
)

@Serializable
data class Group(
    val index: Int? = null,
    val slug: String? = null
)