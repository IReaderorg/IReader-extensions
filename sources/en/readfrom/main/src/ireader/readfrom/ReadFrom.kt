package ireader.readfrom

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
abstract class ReadFrom(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://readfrom.net"
    override val id: Long get() = 94L
    override val name: String get() = "Read From Net"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/allbooks/page/$page/"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document, false)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            if (page > 1) return MangasPageInfo(emptyList(), false)
            val searchUrl = "$baseUrl/build_in_search/?q=${query.encodeURLParameter()}"
            val document = client.get(requestBuilder(searchUrl)).asJsoup()
            return parseNovelList(document, true)
        }

        val url = "$baseUrl/allbooks/page/$page/"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document, false)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document, isSearch: Boolean): MangasPageInfo {
        val selector = if (isSearch) "div.text > article.box" else "#dle-content > article.box"
        
        val novels = document.select(selector).mapNotNull { element ->
            val titleElement = element.selectFirst("h2.title > a")
            val name = element.selectFirst("h2.title")?.text()?.trim()
            val href = titleElement?.attr("href")
            val cover = element.selectFirst("img")?.attr("src") ?: ""

            if (name != null && href != null) {
                val path = href.replace("https://readfrom.net/", "").removePrefix("/")
                MangaInfo(
                    key = path,
                    title = name,
                    cover = cover,
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
            client.get(requestBuilder("$baseUrl/${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("center > h2.title")?.text()
            ?.split(", \n\n")?.firstOrNull()?.trim() ?: manga.title
        val cover = document.selectFirst("article.box > div > center > div > a > img")?.attr("src") ?: manga.cover

        // Try to get more info from search
        val searchDoc = client.get(requestBuilder("$baseUrl/build_in_search/?q=${title.encodeURLParameter()}")).asJsoup()
        val matchingArticle = searchDoc.select("div.text > article.box").firstOrNull { element ->
            val href = element.selectFirst("h2.title > a")?.attr("href")
            href?.contains(manga.key) == true
        }

        val description = matchingArticle?.let { article ->
            val summaryElement = article.selectFirst("div.text5")
            summaryElement?.selectFirst(".coll-ellipsis")?.remove()
            summaryElement?.select("a")?.remove()
            summaryElement?.text()?.trim()
        } ?: ""

        val author = matchingArticle?.select("h5.title > a")
            ?.filter { it.attr("title")?.startsWith("Book author - ") == true }
            ?.firstOrNull()?.text() ?: ""

        val genres = matchingArticle?.select("h5.title > a")
            ?.filter { it.attr("title")?.startsWith("Genre - ") == true }
            ?.map { it.text() }
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
            client.get(requestBuilder("$baseUrl/${manga.key}")).asJsoup()
        }

        val chapters = mutableListOf<ChapterInfo>()

        // First chapter is the novel page itself
        chapters.add(ChapterInfo(
            name = "1",
            key = manga.key,
            number = 1f,
        ))

        // Get additional chapters from pagination
        val pagesDiv = document.select("div.pages").firstOrNull()
        pagesDiv?.select("> a")?.forEachIndexed { index, element ->
            val href = element.attr("href")
            val name = element.text().trim()
            if (href.isNotBlank()) {
                val path = href.replace("https://readfrom.net/", "").removePrefix("/")
                chapters.add(ChapterInfo(
                    name = name,
                    key = path,
                    number = (index + 2).toFloat(),
                ))
            }
        }

        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/${chapter.key}")).asJsoup()
        }

        val textToRead = document.selectFirst("#textToRead") ?: return listOf(Text("Error: Could not find chapter content"))

        // Remove empty spans and center elements
        textToRead.select("span:empty").remove()
        textToRead.select("center").remove()

        val paragraphs = mutableListOf<String>()
        var currentParagraph = StringBuilder()

        textToRead.childNodes().forEach { node ->
            when {
                node.nodeName() == "#text" -> {
                    val text = node.toString().trim()
                    if (text.isNotBlank()) {
                        currentParagraph.append(text).append(" ")
                    }
                }
                node.nodeName() == "br" -> {
                    if (currentParagraph.isNotBlank()) {
                        paragraphs.add(currentParagraph.toString().trim())
                        currentParagraph = StringBuilder()
                    }
                }
                else -> {
                    if (currentParagraph.isNotBlank()) {
                        paragraphs.add(currentParagraph.toString().trim())
                        currentParagraph = StringBuilder()
                    }
                    val element = node as? com.fleeksoft.ksoup.nodes.Element
                    val text = element?.text()?.trim()
                    if (!text.isNullOrBlank()) {
                        paragraphs.add(text)
                    }
                }
            }
        }

        if (currentParagraph.isNotBlank()) {
            paragraphs.add(currentParagraph.toString().trim())
        }

        return paragraphs.filter { it.isNotBlank() }.map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "All Books",
                endpoint = "/allbooks/page/{page}/",
                selector = "#dle-content > article.box",
                nameSelector = "h2.title",
                linkSelector = "h2.title > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "center > h2.title",
            coverSelector = "article.box > div > center > div > a > img",
            coverAtt = "src",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.pages > a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#textToRead",
        )
}
