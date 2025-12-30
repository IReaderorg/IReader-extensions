package ireader.leafstudio

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import tachiyomix.annotations.Extension

@Extension
abstract class LeafStudio(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://leafstudio.site"
    override val id: Long get() = 83L
    override val name: String get() = "LeafStudio"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = if (page > 1) "$baseUrl/novels/page/$page" else "$baseUrl/novels"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        val url = buildString {
            append(if (page > 1) "$baseUrl/novels/page/$page" else "$baseUrl/novels")
            if (!query.isNullOrBlank()) {
                append("?search=${query.encodeURLParameter()}&type=&language=&status=&sort=")
            }
        }

        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("a.novel-item").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.selectFirst("p.novel-item-title")?.text()?.trim() ?: return@mapNotNull null
            val cover = element.selectFirst("img.novel-item-Cover")?.attr("src") ?: ""

            MangaInfo(
                key = href.removePrefix(baseUrl),
                title = name,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("h1.title")?.text()?.trim() ?: manga.title
        val cover = document.selectFirst("img#novel_cover")?.attr("src") ?: manga.cover
        val description = document.select("div.desc_div > p")
            .joinToString("\n\n") { it.text() }
        val genres = document.select("div#tags_div > a.novel_genre")
            .map { it.text().trim() }
        val statusText = document.selectFirst("a#novel_status")?.text()?.trim() ?: ""
        val status = if (statusText == "Active") MangaInfo.ONGOING else MangaInfo.UNKNOWN

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            genres = genres,
            status = status,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        return document.select("a.free_chap.chap").mapNotNull { element ->
            val href = element.attr("href")?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href.removePrefix(baseUrl),
            )
        }.reversed()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${chapter.key}")).asJsoup()
        }

        return document.select("article > p.chapter_content")
            .map { it.html() }
            .joinToString("<br>")
            .split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/novels",
                selector = "a.novel-item",
                nameSelector = "p.novel-item-title",
                coverSelector = "img.novel-item-Cover",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.title",
            coverSelector = "img#novel_cover",
            coverAtt = "src",
            descriptionSelector = "div.desc_div > p",
            categorySelector = "div#tags_div > a.novel_genre",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a.free_chap.chap",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "article > p.chapter_content",
        )
}
