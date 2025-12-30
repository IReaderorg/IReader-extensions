package ireader.ixdzs8

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension

@Extension
abstract class Ixdzs8(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "zh"
    override val baseUrl: String get() = "https://ixdzs8.com"
    override val id: Long get() = 86L
    override val name: String get() = "爱下电子书"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/hot/?page=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            val searchUrl = "$baseUrl/bsearch?q=${query.encodeURLParameter()}"
            val document = client.get(requestBuilder(searchUrl)).asJsoup()
            return parseSearchResults(document)
        }

        val url = "$baseUrl/hot/?page=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("ul.u-list > li.burl").mapNotNull { element ->
            val link = element.selectFirst(".l-info h3 a")
            val novelPath = link?.attr("href")?.trim()
            val novelName = (link?.attr("title") ?: link?.text())?.trim()
            val novelCover = element.selectFirst(".l-img img")?.attr("src")?.trim()

            if (novelPath != null && !novelName.isNullOrBlank()) {
                MangaInfo(
                    key = novelPath,
                    title = novelName,
                    cover = makeAbsolute(novelCover) ?: "",
                )
            } else null
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    private fun parseSearchResults(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("ul.u-list li.burl").mapNotNull { element ->
            val novelPath = element.attr("data-url")?.trim()
            val novelName = element.selectFirst("h3.bname a")?.text()?.trim()
            val novelCover = element.selectFirst(".l-img img")?.attr("src")?.trim()

            if (novelPath != null && !novelName.isNullOrBlank()) {
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
            client.get(requestBuilder(makeAbsolute(manga.key) ?: "$baseUrl${manga.key}")).asJsoup()
        }

        val novelSection = document.selectFirst("div.novel")
        val introSection = document.selectFirst("p#intro.pintro")
        
        // Remove unwanted elements from intro
        introSection?.select("span.icon")?.remove()
        
        val summary = introSection?.html()
            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace("\u3000", " ")
            ?.replace("&nbsp;", " ")
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ")
            ?: ""

        val title = novelSection?.selectFirst(".n-text h1")?.text()?.trim() ?: manga.title
        val cover = makeAbsolute(novelSection?.selectFirst(".n-img img")?.attr("src")) ?: manga.cover
        val author = novelSection?.selectFirst(".n-text p a.bauthor")?.text()?.trim() ?: ""
        
        val tagsDiv = document.selectFirst("div.panel div.tags")
        val genres = tagsDiv?.select("em a")
            ?.map { it.text().trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val statOngoing = novelSection?.selectFirst(".n-text p span.lz")?.text()?.trim() ?: ""
        val statEnd = novelSection?.selectFirst(".n-text p span.end")?.text()?.trim() ?: ""
        val status = when {
            statEnd.isNotEmpty() -> MangaInfo.COMPLETED
            statOngoing.isNotEmpty() -> MangaInfo.ONGOING
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            cover = cover,
            description = summary,
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
            client.get(requestBuilder(makeAbsolute(manga.key) ?: "$baseUrl${manga.key}")).asJsoup()
        }

        val bid = document.selectFirst("#bid")?.attr("value") ?: return emptyList()

        // Fetch chapter list via API
        val response = client.submitForm(
            url = "$baseUrl/novel/clist/",
            formParameters = Parameters.build {
                append("bid", bid)
            }
        ).bodyAsText()

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val rs = jsonObj["rs"]?.jsonPrimitive?.intOrNull
        if (rs != 200) return emptyList()

        val data = jsonObj["data"]?.jsonArray ?: return emptyList()

        return data.mapNotNull { element ->
            val chapter = element.jsonObject
            val title = chapter["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val ctype = chapter["ctype"]?.jsonPrimitive?.contentOrNull
            val ordernum = chapter["ordernum"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            // Only include normal chapters (ctype == "0")
            if (ctype != "0") return@mapNotNull null

            ChapterInfo(
                name = title,
                key = "read/$bid/p$ordernum.html",
                number = ordernum.toFloatOrNull() ?: 0f,
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        var html = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html
        } else {
            client.get(requestBuilder("$baseUrl/${chapter.key}")).bodyAsText()
        }

        // Handle challenge page
        if (html.contains("正在進行安全驗證") || html.contains("challenge")) {
            val tokenMatch = Regex("""let token\s*=\s*"([^"]+)"""").find(html)
            if (tokenMatch != null) {
                val challengeUrl = "$baseUrl/${chapter.key}?challenge=${tokenMatch.groupValues[1].encodeURLParameter()}"
                html = client.get(requestBuilder(challengeUrl)).bodyAsText()
            }
        }

        val document = html.asJsoup()
        val content = document.selectFirst("article section") 
            ?: return listOf(Text("Error: Could not find chapter content"))

        // Remove ads and junk
        content.select("script, style, ins, iframe, [class*='abg'], [class*='ads'], [id*='ads'], [class*='google'], [id*='google'], [class*='recommend'], div[align='center'], a[href*='javascript:']").remove()

        // Remove empty paragraphs and promotional content
        content.select("p").forEach { p ->
            val text = p.text().trim()
            if (text.isEmpty() || text.contains("推薦本書")) {
                p.remove()
            }
        }

        // Unwrap font tags
        content.select("font").forEach { font ->
            font.unwrap()
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
                "Hot",
                endpoint = "/hot/?page={page}",
                selector = "ul.u-list > li.burl",
                nameSelector = ".l-info h3 a",
                nameAtt = "title",
                linkSelector = ".l-info h3 a",
                linkAtt = "href",
                coverSelector = ".l-img img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.novel .n-text h1",
            coverSelector = "div.novel .n-img img",
            coverAtt = "src",
            descriptionSelector = "p#intro.pintro",
            authorBookSelector = "div.novel .n-text p a.bauthor",
            addBaseurlToCoverLink = true,
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.chapter-list a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "article section p",
        )
}
