package ireader.galaxynovels

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.helpers.DateParser
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId
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
@GenerateFilters(title = true)
@GenerateCommands(detailFetch = true, chapterFetch = true, contentFetch = true)
@Extension
@AutoSourceId(seed = "GalaxyNovels")
abstract class GalaxyNovels(private val deps: Dependencies) : SourceFactory(deps = deps) {
    override val lang: String get() = "ar"
    override val baseUrl: String get() = "https://galaxynovels.com"
    override val id: Long get() = GalaxyNovelsSourceId.ID
    override val name: String get() = "GalaxyNovels"

    override val client: HttpClient
        get() = deps.httpClients.cloudflareClient

    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return client to HttpRequestBuilder().apply {
            this.url(url)
            headers {
                append(HttpHeaders.UserAgent, getUserAgent())
            }
        }
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "أضيف حديثا",
                endpoint = "/recent/?recent_page={page}",
                selector = "article.wor-novel-card",
                nameSelector = "h3 > a",
                nameAtt = "",
                coverSelector = "a.wor-novel-card__cover > img",
                coverAtt = "src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 13,
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false,
            ),
            BaseExploreFetcher(
                "الأكثر شهرة",
                endpoint = "/novels/page/{page}/?sort=popular&period=month",
                selector = "article.wor-novel-card",
                nameSelector = "h3 > a",
                nameAtt = "",
                coverSelector = "a.wor-novel-card__cover > img",
                coverAtt = "data-src",
                linkSelector = "h3 > a",
                linkAtt = "href",
                maxPage = 15,
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false,
            ),
        )

    class PopularListing : Listing("الأكثر شهرة")
    class NewListing : Listing("أضيف حديثا")
    class LatestChaptersListing : Listing("أحدث الفصول")

    override fun getListings(): List<Listing> = listOf(
        PopularListing(),
        NewListing(),
        LatestChaptersListing(),
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
            pageContentSelector = ".wor-reading-page__content p",
        )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return when (sort) {
            is PopularListing -> getPopular(page)
            is NewListing -> getRecent(page)
            is LatestChaptersListing -> getLatestChapters()
            else -> super.getMangaList(sort, page)
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        if (!query.isNullOrBlank()) {
            return search(query)
        }
        return super.getMangaList(filters, page)
    }

    private suspend fun getPopular(page: Int): MangasPageInfo {
        val url = if (page <= 1) "/novels/?sort=popular&period=month" else "/novels/page/$page/?sort=popular&period=month"
        return fetchNovelGrid(url, page)
    }

    private suspend fun getRecent(page: Int): MangasPageInfo {
        val url = if (page <= 1) "/recent/" else "/recent/?recent_page=$page"
        return fetchNovelGrid(url, page)
    }

    private suspend fun fetchNovelGrid(url: String, page: Int): MangasPageInfo {
        return try {
            val doc = client.get(requestBuilder("$baseUrl$url")).asJsoup()
            val novels = doc.select("article.wor-novel-card").mapNotNull { card ->
                parseNovelCard(card)
            }
            val hasNext = doc.selectFirst("a.next.page-numbers") != null
            MangasPageInfo(novels, hasNext)
        } catch (e: Exception) {
            Log.error { "Error fetching novel grid: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private suspend fun getLatestChapters(): MangasPageInfo {
        return try {
            val doc = client.get(requestBuilder("$baseUrl/")).asJsoup()
            val novels = doc.select("article.wor-latest-item").mapNotNull { item ->
                val titleEl = item.selectFirst(".wor-latest-item__top h3 > a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                if (title.isBlank() || href.isBlank()) return@mapNotNull null

                val cover = item.selectFirst("a.wor-latest-item__cover > img")?.attr("src") ?: ""
                MangaInfo(key = href, title = title, cover = cover)
            }
            MangasPageInfo(novels, false)
        } catch (e: Exception) {
            Log.error { "Error fetching latest chapters: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    private fun parseNovelCard(card: Element): MangaInfo? {
        val titleEl = card.selectFirst("h3 > a") ?: return null
        val title = titleEl.text().trim()
        val href = titleEl.attr("href")
        if (title.isBlank() || href.isBlank()) return null

        val cover = card.selectFirst("a.wor-novel-card__cover > img, img.wor-cover-img")
            ?.let { img -> img.attr("data-src").ifBlank { img.attr("src") } } ?: ""
        return MangaInfo(key = href, title = title, cover = cover)
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

        val novelId = fetchNovelId(manga.key)
        if (novelId.isNullOrBlank()) {
            Log.error { "Unable to resolve novel id for ${manga.key}" }
            return emptyList()
        }

        return try {
            // Use the static reader cache JSON. The /wp-json REST API is behind a
            // Cloudflare managed challenge that cannot be bypassed from the app and
            // would crash the source, so we avoid it entirely.
            val chaptersUrl = "$baseUrl/wp-content/uploads/wor-reader-cache/chapters/novel-$novelId.json"
            val response = deps.httpClients.default.get(requestBuilder(chaptersUrl))
            val body = response.bodyAsText()
            parseChaptersFromJson(body)
        } catch (e: Exception) {
            Log.error { "Error fetching chapters: ${e.message}" }
            emptyList()
        }
    }

    private suspend fun fetchNovelId(novelUrl: String): String? {
        try {
            val doc = Ksoup.parse(deps.httpClients.default.get(requestBuilder(novelUrl)).bodyAsText())
            doc.selectFirst("article[data-novel-id]")?.attr("data-novel-id")?.let { return it }
        } catch (e: Exception) {
            Log.error { "Error fetching novel page: ${e.message}" }
        }

        try {
            val manifestResponse = deps.httpClients.default.get(requestBuilder("$baseUrl/wp-content/uploads/wor-reader-cache/search/manifest.json"))
            val manifest = Json.parseToJsonElement(manifestResponse.bodyAsText()).jsonObject
            val indexUrl = manifest["index"]?.jsonPrimitive?.contentOrNull ?: return null
            val resolvedIndexUrl = if (indexUrl.startsWith("http")) indexUrl else "$baseUrl$indexUrl"
            val indexResponse = deps.httpClients.default.get(requestBuilder(resolvedIndexUrl))
            val index = Json.parseToJsonElement(indexResponse.bodyAsText()).jsonObject
            val path = novelUrl.removePrefix(baseUrl).trimEnd('/')
            return index["items"]?.jsonArray?.firstOrNull { item ->
                item.jsonObject["u"]?.jsonPrimitive?.contentOrNull?.trimEnd('/') == path
            }?.jsonObject?.get("id")?.jsonPrimitive?.int?.toString()
        } catch (e: Exception) {
            Log.error { "Error resolving novel id from search manifest: ${e.message}" }
        }

        return try {
            val browserResult = deps.httpClients.browser.fetch(
                url = novelUrl,
                selector = "article[data-novel-id]",
                timeout = 50000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                val doc = Ksoup.parse(browserResult.responseBody)
                doc.selectFirst("article[data-novel-id]")?.attr("data-novel-id")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.error { "Error fetching novel page via browser: ${e.message}" }
            null
        }
    }

    private fun parseChaptersFromJson(jsonStr: String): List<ChapterInfo> {
        return try {
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val chapters = json["chapters"]?.jsonArray ?: return emptyList()

            chapters.mapNotNull { ch ->
                val obj = ch.jsonObject
                val position = obj["position"]?.jsonPrimitive?.int ?: 0
                val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: ""
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                val dateIso = obj["date_iso"]?.jsonPrimitive?.contentOrNull ?: ""

                if (url.isBlank()) return@mapNotNull null

                val chapterName = if (title.isNotBlank()) {
                    "$label : $title"
                } else {
                    label.ifBlank { "الفصل $position" }
                }

                ChapterInfo(
                    name = chapterName,
                    key = url,
                    number = position.toFloat(),
                    dateUpload = if (dateIso.isNotBlank()) DateParser.parse(dateIso) else 0L,
                    scanlator = ""
                )
            }.sortedBy { it.number }
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
            val timeEl = item.selectFirst("time[datetime]")
            val dateUpload = timeEl?.attr("datetime")?.let { DateParser.parse(it) } ?: 0L

            if (href.isNotBlank()) {
                chapters.add(
                    ChapterInfo(
                        name = title,
                        key = href,
                        dateUpload = dateUpload,
                        scanlator = ""
                    )
                )
            }
        }

        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseContentFromHtml(cmd.html)
        }

        return try {
            val response = client.get(requestBuilder(chapter.key))
            val body = response.bodyAsText()
            val pages = parseContentFromHtml(body)
            if (pages.isNotEmpty()) return pages

            loadContentViaBrowser(chapter.key)
        } catch (e: Exception) {
            Log.error { "Error fetching content, trying browser: ${e.message}" }
            loadContentViaBrowser(chapter.key)
        }
    }

    private suspend fun loadContentViaBrowser(url: String): List<Page> {
        return try {
            val browserResult = deps.httpClients.browser.fetch(
                url = url,
                selector = ".wor-reading-page__content p",
                timeout = 50000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                val pages = parseContentFromHtml(browserResult.responseBody)
                if (pages.isNotEmpty()) return pages
            }
            listOf(Text("حدث خطأ أثناء تحميل محتوى الفصل."))
        } catch (e: Exception) {
            Log.error { "Error fetching content via browser: ${e.message}" }
            listOf(Text("حدث خطأ أثناء تحميل محتوى الفصل."))
        }
    }

    private fun parseContentFromHtml(html: String): List<Page> {
        val doc = Ksoup.parse(html)

        val content = doc.selectFirst(".wor-reading-page__content") ?: doc.selectFirst("#content")
            ?: return emptyList()

        val paragraphs = content.select("p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.contains("يرجى تفعيل JavaScript") }

        if (paragraphs.isNotEmpty()) {
            return paragraphs.map { Text(it) }
        }

        val text = content.text().trim()
        if (text.isNotBlank()) {
            return text.split("\n").filter { it.isNotBlank() }.map { Text(it) }
        }

        return emptyList()
    }
}
