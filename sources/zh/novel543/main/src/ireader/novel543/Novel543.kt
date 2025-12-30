package ireader.novel543

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
abstract class Novel543(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "zh"
    override val baseUrl: String get() = "https://www.novel543.com"
    override val id: Long get() = 85L
    override val name: String get() = "Novel543"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        if (page > 1) return MangasPageInfo(emptyList(), false)
        
        val document = client.get(requestBuilder(baseUrl)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            if (page > 1) return MangasPageInfo(emptyList(), false)
            
            // Check if query is numeric (novel ID)
            if (query.matches(Regex("^\\d+$"))) {
                return try {
                    val novelPath = "/$query/"
                    val document = client.get(requestBuilder("$baseUrl$novelPath")).asJsoup()
                    val title = document.selectFirst("section#detail div.media-content.info h1.title")?.text()?.trim()
                    val cover = document.selectFirst("section#detail div.cover img")?.attr("src")
                    
                    if (title != null) {
                        MangasPageInfo(listOf(MangaInfo(
                            key = novelPath,
                            title = title,
                            cover = makeAbsolute(cover) ?: "",
                        )), false)
                    } else {
                        MangasPageInfo(emptyList(), false)
                    }
                } catch (e: Exception) {
                    MangasPageInfo(emptyList(), false)
                }
            }
            
            val searchUrl = "$baseUrl/search/${query.encodeURLParameter()}"
            val document = client.get(requestBuilder(searchUrl)).asJsoup()
            return parseSearchResults(document)
        }

        if (page > 1) return MangasPageInfo(emptyList(), false)
        val document = client.get(requestBuilder(baseUrl)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = mutableListOf<MangaInfo>()
        val processedPaths = mutableSetOf<String>()

        document.select("ul.list > li.media, ul.list li > a[href^='/'][href$='/']").forEach { element ->
            var novelPath: String? = null
            var novelName: String? = null
            var novelCover: String? = null

            if (element.tagName() == "li" && element.hasClass("media")) {
                val link = element.selectFirst(".media-content h3 a")
                novelPath = link?.attr("href")?.trim()
                novelName = link?.text()?.trim()
                novelCover = element.selectFirst(".media-left img")?.attr("src")?.trim()
            } else if (element.tagName() == "a") {
                novelPath = element.attr("href")?.trim()
                novelName = element.selectFirst("h3, b, span")?.text()?.trim()
                    ?: element.parent()?.selectFirst("h3")?.text()?.trim()
                    ?: element.text().trim()
                novelCover = element.selectFirst("img")?.attr("src")?.trim()
                    ?: element.parent()?.selectFirst("img")?.attr("src")?.trim()
            }

            if (novelPath != null && novelName != null && 
                novelPath.matches(Regex("^/\\d+/$")) && 
                !processedPaths.contains(novelPath)) {
                novels.add(MangaInfo(
                    key = novelPath,
                    title = novelName,
                    cover = makeAbsolute(novelCover) ?: "",
                ))
                processedPaths.add(novelPath)
            }
        }

        return MangasPageInfo(novels, false)
    }

    private fun parseSearchResults(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("div.search-list ul.list > li.media").mapNotNull { element ->
            val link = element.selectFirst(".media-content h3 a")
            val novelPath = link?.attr("href")?.trim()
            val novelName = link?.text()?.trim()
            val novelCover = element.selectFirst(".media-left img")?.attr("src")?.trim()

            if (novelPath != null && novelName != null && novelPath.matches(Regex("^/\\d+/$"))) {
                MangaInfo(
                    key = novelPath,
                    title = novelName,
                    cover = makeAbsolute(novelCover) ?: "",
                )
            } else null
        }

        return MangasPageInfo(novels, false)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        val infoSection = document.selectFirst("section#detail div.media-content.info")
        val modSection = document.selectFirst("section#detail div.mod")

        val title = infoSection?.selectFirst("h1.title")?.text()?.trim() ?: manga.title
        val cover = makeAbsolute(document.selectFirst("section#detail div.cover img")?.attr("src")) ?: manga.cover
        val description = modSection?.selectFirst("div.intro")?.text()?.trim() ?: ""
        val author = infoSection?.selectFirst("p.meta span.author")?.text()?.trim() ?: ""
        val genres = infoSection?.select("p.meta a[href*='/bookstack/']")
            ?.map { it.text().trim() }
            ?: emptyList()

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = genres,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        // Find chapter list page link
        val chapterListPath = document.selectFirst("div.mod p.action.buttons a.button.is-info[href$='/dir']")?.attr("href")
            ?: document.selectFirst("div.media-content.info a.button.is-info[href$='/dir']")?.attr("href")
            ?: return emptyList()

        val chapterListUrl = makeAbsolute(chapterListPath) ?: return emptyList()
        val chapterDoc = client.get(requestBuilder(chapterListUrl)).asJsoup()

        val chapters = chapterDoc.select("div.chaplist ul.all li a").mapIndexedNotNull { index, element ->
            val chapterName = element.text().trim()
            val chapterUrl = element.attr("href")?.trim()

            if (chapterName.isNotBlank() && !chapterUrl.isNullOrBlank()) {
                ChapterInfo(
                    name = chapterName,
                    key = chapterUrl,
                    number = (index + 1).toFloat(),
                )
            } else null
        }

        // Check if we need to reverse
        val sortButtonText = chapterDoc.selectFirst("div.chaplist .header button.reverse span:last-child")?.text()?.trim()
        return if (sortButtonText == "倒序") chapters.reversed() else chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder(makeAbsolute(chapter.key) ?: "$baseUrl${chapter.key}")).asJsoup()
        }

        val content = document.selectFirst("div.content.py-5") ?: return listOf(Text("Error: Could not find chapter content"))

        // Remove ads and junk
        content.select("script, style, ins, iframe, [class*='ads'], [id*='ads'], [class*='google'], [id*='google'], [class*='recommend'], div[align='center'], a[href*='javascript:']").remove()

        // Remove promotional paragraphs
        content.select("p").forEach { p ->
            val text = p.text().trim()
            if (text.contains("請記住本站域名") ||
                text.contains("手機版閱讀網址") ||
                text.contains("novel543") ||
                text.contains("稷下書院") ||
                text.contains("最快更新") ||
                text.contains("最新章節") ||
                text.contains("章節報錯") ||
                text.matches(Regex(".*(?:app|APP|下載|客户端|关注微信|公众号).*")) ||
                text.isEmpty() ||
                text.contains("溫馨提示")) {
                p.remove()
            }
        }

        return content.select("p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    private fun makeAbsolute(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/",
                selector = "ul.list > li.media",
                nameSelector = ".media-content h3 a",
                linkSelector = ".media-content h3 a",
                linkAtt = "href",
                coverSelector = ".media-left img",
                coverAtt = "src",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "section#detail div.media-content.info h1.title",
            coverSelector = "section#detail div.cover img",
            coverAtt = "src",
            descriptionSelector = "section#detail div.mod div.intro",
            authorBookSelector = "section#detail div.media-content.info p.meta span.author",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.chaplist ul.all li a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div.content.py-5 p",
        )
}
