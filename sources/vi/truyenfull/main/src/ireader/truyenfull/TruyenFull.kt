package ireader.truyenfull

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
abstract class TruyenFull(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "vi"
    override val baseUrl: String get() = "https://truyenfull.io"
    override val id: Long get() = 90L
    override val name: String get() = "Truyện Full"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sắp xếp",
            arrayOf(
                "Truyện mới cập nhật", "Truyện hot", "Truyện full",
                "Tiên hiệp hay", "Kiếm hiệp hay", "Truyện teen hay",
                "Ngôn tình hay", "Ngôn tình ngược", "Ngôn tình sủng",
                "Ngôn tình hài", "Đam mỹ hay", "Đam mỹ hài",
                "Đam mỹ h văn", "Đam mỹ sắc"
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val sortValues = arrayOf(
        "truyen-moi", "truyen-hot", "truyen-full",
        "tien-hiep-hay", "kiem-hiep-hay", "truyen-teen-hay",
        "ngon-tinh-hay", "ngon-tinh-nguoc", "ngon-tinh-sung",
        "ngon-tinh-hai", "dam-my-hay", "dam-my-hai",
        "dam-my-h-van", "dam-my-sac"
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/danh-sach/truyen-hot/trang-$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            val searchUrl = "$baseUrl/tim-kiem?tukhoa=${query.encodeURLParameter()}&page=$page"
            val document = client.get(requestBuilder(searchUrl)).asJsoup()
            return parseNovelList(document)
        }

        val sortFilter = filters.findInstance<Filter.Sort>()?.value?.index ?: 1
        val sortValue = sortValues.getOrElse(sortFilter) { "truyen-hot" }

        val url = "$baseUrl/danh-sach/$sortValue/trang-$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select(".list-truyen .row").mapNotNull { element ->
            val novelName = element.selectFirst("h3.truyen-title > a")?.text()?.trim()
            val novelCover = element.selectFirst("div[data-classname='cover']")?.attr("data-image")
            val novelUrl = element.selectFirst("h3.truyen-title > a")?.attr("href")

            if (novelUrl != null && !novelName.isNullOrBlank()) {
                MangaInfo(
                    key = novelUrl.replace(baseUrl, ""),
                    title = novelName,
                    cover = novelCover ?: "",
                )
            } else null
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

        val title = document.selectFirst("div.book > img")?.attr("alt") ?: manga.title
        val cover = document.selectFirst("div.book > img")?.attr("src") ?: manga.cover
        val description = document.selectFirst("div.desc-text")?.text()?.trim() ?: ""
        
        val author = document.selectFirst("h3:contains(Tác giả:)")?.parent()?.text()
            ?.replace("Tác giả:", "")?.trim() ?: ""
        
        val genres = document.select("h3:contains(Thể loại)")
            .first()?.siblingElements()
            ?.map { it.text().trim() }
            ?: emptyList()

        val statusText = document.selectFirst("h3:contains(Trạng thái)")?.nextElementSibling()?.text() ?: ""
        val status = when {
            statusText == "Full" -> MangaInfo.COMPLETED
            statusText == "Đang ra" -> MangaInfo.ONGOING
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
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

        val chapters = mutableListOf<ChapterInfo>()

        // Get last page number
        var lastPage = 1
        document.select("ul.pagination.pagination-sm > li > a").forEach { element ->
            val pageMatch = Regex("""/trang-(\d+)/""").find(element.attr("href"))
            val page = pageMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (page > lastPage) lastPage = page
        }

        // Parse chapters from first page
        chapters.addAll(parseChaptersFromPage(document))

        // Fetch remaining pages
        for (pageNum in 2..lastPage) {
            val pageUrl = "$baseUrl${manga.key}trang-$pageNum/#list-chapter"
            val pageDoc = client.get(requestBuilder(pageUrl)).asJsoup()
            chapters.addAll(parseChaptersFromPage(pageDoc))
        }

        return chapters.mapIndexed { index, chapter ->
            chapter.copy(number = (index + 1).toFloat())
        }
    }

    private fun parseChaptersFromPage(document: com.fleeksoft.ksoup.nodes.Document): List<ChapterInfo> {
        return document.select("ul.list-chapter > li > a").mapNotNull { element ->
            val path = element.attr("href").replace(baseUrl, "")
            val name = element.text().trim()
            val chapterNumber = Regex("""/chuong-(\d+)/""").find(path)?.groupValues?.get(1)?.toFloatOrNull()

            if (path.isNotBlank() && name.isNotBlank()) {
                ChapterInfo(
                    name = name,
                    key = path,
                    number = chapterNumber ?: 0f,
                )
            } else null
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${chapter.key}")).asJsoup()
        }

        val chapterTitle = document.selectFirst(".chapter-title")?.html() ?: ""
        val chapterContent = document.selectFirst("#chapter-c")?.html() ?: ""

        val fullContent = "$chapterTitle\n$chapterContent"

        return fullContent.split("\n", "</p>", "<p>", "<br>", "<br/>", "<br />")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Hot",
                endpoint = "/danh-sach/truyen-hot/trang-{page}",
                selector = ".list-truyen .row",
                nameSelector = "h3.truyen-title > a",
                linkSelector = "h3.truyen-title > a",
                linkAtt = "href",
                coverSelector = "div[data-classname='cover']",
                coverAtt = "data-image",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.book > img",
            nameAtt = "alt",
            coverSelector = "div.book > img",
            coverAtt = "src",
            descriptionSelector = "div.desc-text",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ul.list-chapter > li > a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title",
            pageContentSelector = "#chapter-c",
        )
}
