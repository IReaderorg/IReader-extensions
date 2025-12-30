package ireader.novelsonline

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
abstract class NovelsOnline(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://novelsonline.org"
    override val id: Long get() = 86L
    override val name: String get() = "NovelsOnline"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/top-novel/$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            val formData = "keyword=${query.encodeURLParameter()}&search=1"
            val response = client.post("$baseUrl/detailed-search") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(formData)
            }.asJsoup()
            return parseNovelList(response)
        }

        return getMangaList(null, page)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select(".top-novel-block").mapNotNull { element ->
            val name = element.selectFirst("h2")?.text()?.trim() ?: return@mapNotNull null
            val cover = element.selectFirst(".top-novel-cover img")?.attr("src") ?: ""
            val href = element.selectFirst("h2 a")?.attr("href") ?: return@mapNotNull null

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

        val title = document.selectFirst("h1")?.text()?.trim() ?: manga.title
        val cover = document.selectFirst(".novel-cover a > img")?.attr("src") ?: manga.cover

        var description = ""
        var genres = listOf<String>()
        var author = ""
        var artist = ""
        var status = MangaInfo.UNKNOWN

        document.select(".novel-detail-item").forEach { item ->
            val detailName = item.selectFirst("h6")?.text()?.trim() ?: ""
            val detail = item.selectFirst(".novel-detail-body")

            when (detailName) {
                "Description" -> description = detail?.text()?.trim() ?: ""
                "Genre" -> genres = detail?.select("li")?.map { it.text().trim() } ?: emptyList()
                "Author(s)" -> author = detail?.select("li")?.joinToString(", ") { it.text().trim() } ?: ""
                "Artist(s)" -> {
                    val artistText = detail?.select("li")?.joinToString(", ") { it.text().trim() } ?: ""
                    if (artistText.isNotBlank() && artistText != "N/A") artist = artistText
                }
                "Status" -> {
                    val statusText = detail?.text()?.trim() ?: ""
                    status = when {
                        statusText.contains("Ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                        statusText.contains("Completed", ignoreCase = true) -> MangaInfo.COMPLETED
                        else -> MangaInfo.UNKNOWN
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

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        return document.select("ul.chapter-chs > li > a").mapNotNull { element ->
            val href = element.attr("href")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: "Chapter"

            ChapterInfo(
                name = name,
                key = href.removePrefix(baseUrl),
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${chapter.key}")).asJsoup()
        }

        val content = document.selectFirst("#contentall")?.html() ?: ""

        return content.split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/top-novel/{page}",
                selector = ".top-novel-block",
                nameSelector = "h2",
                coverSelector = ".top-novel-cover img",
                coverAtt = "src",
                linkSelector = "h2 a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = ".novel-cover a > img",
            coverAtt = "src",
            descriptionSelector = ".novel-detail-item:contains(Description) .novel-detail-body",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ul.chapter-chs > li > a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#contentall",
        )
}
