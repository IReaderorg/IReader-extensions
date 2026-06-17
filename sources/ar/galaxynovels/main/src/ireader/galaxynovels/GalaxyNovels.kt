package ireader.galaxynovels

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateCommands
import tachiyomix.annotations.GenerateFilters
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "shadow",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://galaxynovels.com/novel/shadow-slave/",
    chapterUrl = "https://galaxynovels.com/novel/shadow-slave/chapter-1/%d8%a7%d9%84%d9%81%d8%b5%d9%84-1-%d9%8a%d8%a8%d8%af%d8%a3-%d8%a7%d9%84%d9%83%d8%a7%d8%a8%d9%88%d8%b3-2/",
    expectedTitle = "Shadow Slave",
    expectedAuthor = "Guiltythree"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false
)
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular"])
@GenerateCommands(detailFetch = true, chapterFetch = true, contentFetch = true)
@Extension
abstract class GalaxyNovels(private val deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://galaxynovels.com"
    override val id: Long get() = 5839019927924950627L
    override val name: String get() = "GalaxyNovels"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort:", arrayOf("Latest", "Popular")),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/recent/page/{page}/",
                selector = "article.wor-novel-card",
                nameSelector = "h3 > a",
                nameAtt = "",
                coverSelector = "a.wor-novel-card__cover > img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 100,
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false,
            ),
            BaseExploreFetcher(
                "Popular",
                endpoint = "/novels/?sort=popular&period=all",
                selector = "article.wor-novel-card",
                nameSelector = "h3 > a",
                nameAtt = "",
                coverSelector = "a.wor-novel-card__cover > img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 1,
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false,
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = ".wor-single-hero__cover img, img.wor-cover-img",
            coverAtt = "src",
            addBaseurlToCoverLink = false,
            authorBookSelector = ".wor-single-hero__meta-text span",
            descriptionSelector = ".wor-single-summary__text",
            categorySelector = "a.wor-tag-pill",
            statusSelector = ".wor-cover-status--ongoing, .wor-cover-status--completed",
            onStatus = { str ->
                when {
                    str.contains("ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    str.contains("completed", ignoreCase = true) -> MangaInfo.COMPLETED
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "article.wor-novel-chapter-item",
            nameSelector = "h3 > a",
            linkSelector = "a.wor-novel-chapter-item__num",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = false,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".wor-chapter-content p, .entry-content p, .chapter-content p",
        )

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index

        if (!query.isNullOrBlank()) {
            return search(query)
        }

        return when (sortIndex) {
            1 -> getPopular()
            else -> super.getMangaList(filters, page)
        }
    }

    private suspend fun getPopular(): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/novels/?sort=popular&period=all"))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)

            val mangaList = doc.select("article.wor-novel-card").mapNotNull { card ->
                val titleEl = card.selectFirst("h3 > a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                if (title.isBlank() || href.isBlank()) return@mapNotNull null

                val coverImg = card.selectFirst("a.wor-novel-card__cover > img")
                val cover = coverImg?.attr("src") ?: ""

                MangaInfo(
                    key = href,
                    title = title,
                    cover = cover
                )
            }

            MangasPageInfo(mangaList, mangaList.isNotEmpty())
        } catch (e: Exception) {
            Log.error { "Error fetching popular novels: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private suspend fun search(query: String): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder("$baseUrl/library/?q=$query"))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)

            val mangaList = doc.select("article.wor-library-card, article.wor-novel-card").mapNotNull { card ->
                val titleEl = card.selectFirst("h2.wor-library-card__title > a, h3 > a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                if (title.isBlank() || href.isBlank()) return@mapNotNull null

                val coverImg = card.selectFirst("a.wor-library-card__cover > img, a.wor-novel-card__cover > img")
                val cover = coverImg?.attr("src") ?: ""

                MangaInfo(
                    key = href,
                    title = title,
                    cover = cover
                )
            }

            MangasPageInfo(mangaList, mangaList.isNotEmpty())
        } catch (e: Exception) {
            Log.error { "Error searching: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseChaptersFromHtml(cmd.html)
        }

        return try {
            val response = client.get(requestBuilder(manga.key))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)

            val novelId = doc.selectFirst("article[data-novel-id]")?.attr("data-novel-id")

            if (!novelId.isNullOrBlank()) {
                val indexUrl = "$baseUrl/wp-content/uploads/wor-reader-cache/chapters/novel-$novelId.json"
                val indexResponse = client.get(requestBuilder(indexUrl))
                val indexBody = indexResponse.bodyAsText()
                return parseChaptersFromJson(indexBody)
            }

            parseChaptersFromHtml(body)
        } catch (e: Exception) {
            Log.error { "Error fetching chapters: ${e.message}" }
            emptyList()
        }
    }

    private fun parseChaptersFromJson(jsonStr: String): List<ChapterInfo> {
        return try {
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val chapters = json["chapters"]?.jsonArray ?: return emptyList()

            chapters.map { ch ->
                val obj = ch.jsonObject
                val number = obj["number"]?.jsonPrimitive?.int ?: 0
                val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: ""
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""

                val chapterName = if (title.isNotBlank()) {
                    "$label : $title"
                } else {
                    label.ifBlank { "الفصل $number" }
                }

                ChapterInfo(
                    name = chapterName,
                    key = url
                )
            }.sortedBy { chapter ->
                val numStr = Regex("(\\d+)").find(chapter.name)?.groupValues?.get(1)
                numStr?.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            Log.error { "Error parsing chapters JSON: ${e.message}" }
            emptyList()
        }
    }

    private fun parseChaptersFromHtml(html: String): List<ChapterInfo> {
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<ChapterInfo>()

        doc.select("article.wor-novel-chapter-item").forEach { item ->
            val linkEl = item.selectFirst("a.wor-novel-chapter-item__num") ?: return@forEach
            val href = linkEl.attr("href")
            val titleEl = item.selectFirst("h3 > a")
            val title = titleEl?.text()?.trim() ?: ""

            if (href.isNotBlank()) {
                chapters.add(ChapterInfo(name = title, key = href))
            }
        }

        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseContentFromHtml(cmd.html)
        }

        return try {
            val browserResult = deps.httpClients.browser.fetch(
                url = chapter.key,
                selector = "#content > article > div > p:nth-child(2)",
                timeout = 50000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                parseContentFromHtml(browserResult.responseBody)
            } else {
                val response = client.get(requestBuilder(chapter.key))
                val body = response.bodyAsText()
                parseContentFromHtml(body)
            }
        } catch (e: Exception) {
            Log.error { "Error fetching content: ${e.message}" }
            listOf(Text("حدث خطأ أثناء تحميل محتوى الفصل."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)


            val contentDiv = doc.select("#content p")

                val paragraphs = contentDiv.select("p").map { it.text() }.filter { it.isNotBlank() }
                if (paragraphs.isNotEmpty()) {
                    return paragraphs.map { Text(it) }
                }
                val text = contentDiv.text()
                if (text.isNotBlank()) {
                    return text.split("\n").filter { it.isNotBlank() }.map { Text(it) }
                }



        return listOf(Text("لم يتم العثور على محتوى الفصل. قد تحتاج إلى تسجيل الدخول."))
    }
}
