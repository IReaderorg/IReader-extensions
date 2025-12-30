package ireader.quanben

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
abstract class Quanben(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "zh"
    override val baseUrl: String get() = "https://www.quanben.io"
    override val id: Long get() = 85L
    override val name: String get() = "Quanben"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "分类",
            arrayOf("全部", "玄幻", "都市", "言情", "穿越", "青春", "仙侠", "灵异", "悬疑", "历史", "军事", "游戏", "竞技", "科幻", "职场", "官场", "现言", "耽美", "其它")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val genreValues = arrayOf("all", "xuanhuan", "dushi", "yanqing", "chuanyue", "qingchun", "xianxia", "lingyi", "xuanyi", "lishi", "junshi", "youxi", "jingji", "kehuan", "zhichang", "guanchang", "xianyan", "danmei", "qita")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val document = client.get(requestBuilder(baseUrl)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val sortIndex = sortFilter?.value?.index ?: 0
        val genre = genreValues.getOrElse(sortIndex) { "all" }

        if (!query.isNullOrBlank()) {
            val url = "$baseUrl/index.php?c=book&a=search&keywords=${query.encodeURLParameter()}"
            val document = client.get(requestBuilder(url)).asJsoup()
            return parseSearchResults(document)
        }

        val url = if (genre == "all") baseUrl else "$baseUrl/c/$genre.html"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = mutableListOf<MangaInfo>()

        document.select("div.list2").forEach { element ->
            val linkElement = element.selectFirst("h3 > a") ?: return@forEach
            val href = linkElement.attr("href").trim()
            val name = linkElement.text().trim()
            if (href.isBlank() || name.isBlank()) return@forEach

            val cover = element.selectFirst("img")?.let { img ->
                img.attr("src").takeIf { it.isNotBlank() } ?: img.attr("data-src")
            }?.let { makeAbsolute(it) } ?: ""

            val path = getStandardNovelPath(href)
            if (path != null) {
                novels.add(MangaInfo(key = path, title = name, cover = cover))
            }
        }

        // First entry from ul.list
        document.select("ul.list").forEach { ul ->
            val firstLi = ul.selectFirst("li") ?: return@forEach
            val linkElement = firstLi.selectFirst("a") ?: return@forEach
            val href = linkElement.attr("href").trim()
            val name = linkElement.text().trim().takeIf { it.isNotBlank() }
                ?: firstLi.selectFirst("span.author")?.text()?.trim() ?: return@forEach

            val path = getStandardNovelPath(href)
            if (path != null) {
                novels.add(MangaInfo(key = path, title = name, cover = ""))
            }
        }

        return MangasPageInfo(novels, false)
    }

    private fun parseSearchResults(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("div.list2").mapNotNull { element ->
            val linkElement = element.selectFirst("h3 > a") ?: return@mapNotNull null
            val href = linkElement.attr("href").trim()
            val name = linkElement.text().trim()
            if (href.isBlank() || name.isBlank()) return@mapNotNull null

            val cover = element.selectFirst("img")?.let { img ->
                img.attr("src").takeIf { it.isNotBlank() } ?: img.attr("data-src")
            }?.let { makeAbsolute(it) } ?: ""

            val path = getStandardNovelPath(makeAbsolute(href) ?: href)
            if (path != null) {
                MangaInfo(key = "/amp$path", title = name, cover = cover)
            } else null
        }

        return MangasPageInfo(novels, false)
    }

    private fun makeAbsolute(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http") -> url
            else -> "$baseUrl$url"
        }
    }

    private fun getStandardNovelPath(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val match = Regex("""^(/amp)?(/n/[^/]+/)""").find(url)
        return match?.groupValues?.get(2)
    }


    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val standardPath = manga.key.replace(Regex("^/amp"), "")
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl$standardPath")).asJsoup()
        }

        val info = document.selectFirst("div.list2")
        val desc = document.selectFirst("div.description")

        val title = info?.selectFirst("h3")?.text()?.trim() ?: manga.title
        val cover = info?.selectFirst("img")?.attr("src")?.let { makeAbsolute(it) } ?: manga.cover
        val description = desc?.selectFirst("p")?.text()?.trim() ?: desc?.text()?.trim() ?: ""
        val author = info?.selectFirst("p:contains(作者:) span")?.text()?.trim() ?: ""
        val genres = info?.selectFirst("p:contains(类别:) span")?.text()?.trim() ?: ""

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            description = description,
            genres = genres.split(",").map { it.trim() }.filter { it.isNotBlank() },
            status = MangaInfo.COMPLETED,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return parseChaptersFromHtml(chapterFetch.html.asJsoup(), manga.key)
        }

        val standardPath = manga.key.replace(Regex("^/amp"), "")
        val url = "$baseUrl${standardPath}list.html"
        val document = client.get(requestBuilder(url)).asJsoup()

        val novelName = Regex("""/n/([^/]+)/""").find(standardPath)?.groupValues?.get(1) ?: return emptyList()

        val chapters = document.select("ul.list3 li a").mapNotNull { element ->
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val fileName = getChapterFileName(makeAbsolute(href)) ?: return@mapNotNull null

            ChapterInfo(
                name = name,
                key = "$novelName/$fileName",
            )
        }

        // Remove duplicates and sort
        val uniqueChapters = chapters.distinctBy { it.key }
        return uniqueChapters.sortedBy {
            Regex("""(\d+)\.html$""").find(it.key)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }.mapIndexed { index, chapter ->
            chapter.copy(number = (index + 1).toFloat())
        }
    }

    private fun getChapterFileName(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val fileName = url.split("/").lastOrNull() ?: return null
        return if (Regex("""^\d+\.html$""").matches(fileName)) fileName else null
    }

    private fun parseChaptersFromHtml(document: com.fleeksoft.ksoup.nodes.Document, novelPath: String): List<ChapterInfo> {
        val novelName = Regex("""/n/([^/]+)/""").find(novelPath)?.groupValues?.get(1) ?: ""
        return document.select("ul.list3 li a").mapNotNull { element ->
            val name = element.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val fileName = getChapterFileName(makeAbsolute(href)) ?: return@mapNotNull null

            ChapterInfo(name = name, key = "$novelName/$fileName")
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/n/${chapter.key}")).asJsoup()
        }

        val content = document.selectFirst("#contentbody, #content, .content")
        content?.select("script, style, ins, iframe, [class*=ads], [id*=ads], [class*=google], [id*=google], [class*=recommend], div[align=center]")?.remove()

        val html = content?.html() ?: return listOf(Text("Error: Chapter content not found."))

        return html.replace(Regex("""[\t ]+"""), " ")
            .split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/",
                selector = "div.list2",
                nameSelector = "h3 > a",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.list2 h3",
            coverSelector = "div.list2 img",
            coverAtt = "src",
            descriptionSelector = "div.description p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ul.list3 li a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#contentbody, #content, .content",
        )
}
