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
import kotlinx.serialization.json.intOrNull
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
            // Search must be registered as Type.Search so the app's search flow
            // (getMangaList(filters)) routes through this fetcher instead of
            // returning an empty page. Endpoint uses {query} placeholder.
            BaseExploreFetcher(
                "بحث",
                endpoint = "/library/?q={query}",
                selector = "article.wor-library-card",
                nameSelector = "h2.wor-library-card__title > a",
                nameAtt = "",
                coverSelector = "a.wor-library-card__cover > img",
                coverAtt = "data-src",
                linkSelector = "h2.wor-library-card__title > a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = false,
                type = Type.Search,
                onQuery = { query -> java.net.URLEncoder.encode(query.trim(), "UTF-8") },
            ),
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
            // The theme renamed the text container to .wor-reader-text-surface; the old
            // .wor-reading-page__content no longer exists, so chapter text came back empty.
            pageContentSelector = ".wor-reader-text-surface p, .wor-reading-page__content p",
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

    // The homepage's "أحدث الفصول" showcase renders 50 `article.wor-latest-item` cards,
    // each with up to 6 recent chapter links (a.wor-latest-chapter).
    //
    // The theme renamed the inner blocks: the title now sits in
    // .wor-latest-item__body > h3.wor-latest-card__title (the old .wor-latest-item__top
    // and .wor-latest-item__title no longer exist), which is why this listing came back
    // empty. Both namings are accepted so a theme rollback keeps working.
    private suspend fun getLatestChapters(): MangasPageInfo {
        return try {
            val doc = client.get(requestBuilder("$baseUrl/")).asJsoup()
            val novels = doc.select("article.wor-latest-item").mapNotNull { item ->
                val titleEl = item.selectFirst(
                    ".wor-latest-item__body h3 > a, .wor-latest-item__top h3 > a, h3 > a"
                ) ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val href = titleEl.attr("href")
                if (title.isBlank() || href.isBlank()) return@mapNotNull null

                val img = item.selectFirst("a.wor-latest-item__cover > img, a.wor-latest-card__cover > img")
                // Covers are lazy-loaded: src is a base64 SVG placeholder, data-src is real.
                val cover = img?.let { it.attr("data-src").ifBlank { it.attr("src") } }.orEmpty()
                MangaInfo(key = href, title = title, cover = cover)
            }
            Log.info { "GalaxyNovels: latest-chapters listing parsed ${novels.size} novels" }
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
            // Arabic text in a raw query string breaks the server (HTTP 400).
            // Encode so any character (Arabic, spaces, UTF-8) round-trips.
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            // /library/?q= is NOT behind the Cloudflare managed challenge (curl
            // reaches it: HTTP 200) — use the plain client so search stays fast and
            // never spins up a WebView.
            val response = deps.httpClients.default.get(requestBuilder("$baseUrl/library/?q=$encoded"))
            val body = response.bodyAsText()
            val doc = Ksoup.parse(body)

            val mangaList = doc.select("article.wor-library-card, article.wor-novel-card").mapNotNull { card ->
                parseSearchCard(card)
            }

            MangasPageInfo(mangaList, mangaList.isNotEmpty())
        } catch (e: Exception) {
            Log.error { "Error searching: ${e.message}" }
            MangasPageInfo(emptyList(), false)
        }
    }

    // wor-library-card: <article data-wor-library-novel-id="140126">
    //   <h2 class="wor-library-card__title"><a href=..>title</a></h2>
    //   <a class="wor-library-card__cover"><img data-src=..></a>
    private fun parseSearchCard(card: Element): MangaInfo? {
        val titleEl = card.selectFirst("h2.wor-library-card__title > a, h3 > a") ?: return null
        val title = titleEl.text().trim()
        val href = titleEl.attr("href").ifBlank { titleEl.absUrl("href") }
        if (title.isBlank() || href.isBlank()) return null

        val cover = card.selectFirst("a.wor-library-card__cover > img, a.wor-novel-card__cover > img")
            ?.let { img -> img.attr("data-src").ifBlank { img.attr("src") } } ?: ""
        return MangaInfo(key = href, title = title, cover = cover)
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) return parseChaptersFromHtml(cmd.html)
        }

        // The Wor theme's REST route is the only chapter source that returns the
        // *complete* list:
        //   GET /wp-json/wor-reader/v1/offline/novels/{id}
        //   → {"schema":4,"chapters_count":3188,"public_only":true,
        //      "chapters":[{id,position,label,title,url,published_at,access},...]}
        // Verified 2026-09: 140126 → 768, 111763 → 3188 chapters, all access:"public".
        //
        // The novel page itself only renders the first ~180 chapters, and the legacy
        // /wp-json/wor/read/v1/... route 404s — so the offline REST pack is the
        // authoritative list, not the page scrape.
        //
        // Note this route needs no Cloudflare solve (plain JSON, HTTP 200 for any UA),
        // so the cheap default client is used and no WebView is ever spun up.
        val novelId = fetchNovelId(manga.key) ?: run {
            Log.error { "GalaxyNovels: unable to resolve novel id for ${manga.key}" }
            return emptyList()
        }

        val chaptersUrl = "$baseUrl/wp-json/wor-reader/v1/offline/novels/$novelId"
        return try {
            Log.info { "GalaxyNovels: GET $chaptersUrl" }
            val body = deps.httpClients.default.get(requestBuilder(chaptersUrl)).bodyAsText()
            if (body.isBlank()) {
                Log.error { "GalaxyNovels: empty body from $chaptersUrl" }
                return emptyList()
            }
            val chapters = parseChaptersFromOfflineApi(body)
            Log.info { "GalaxyNovels: parsed ${chapters.size} chapters for novel $novelId" }
            chapters
        } catch (e: Exception) {
            Log.error { "Error fetching chapters for $novelId: ${e.message}" }
            emptyList()
        }
    }

    private suspend fun fetchNovelId(novelUrl: String): String? {
        // The slug is the last path segment of /novel/{slug}/; strip the query/fragment
        // too so keys captured from listing links (which may carry ?sort=…) still match.
        val slug = novelUrl.substringBefore('?').substringBefore('#')
            .trimEnd('/').substringAfterLast('/')
        if (slug.isBlank()) return null

        // Fastest & most reliable: the /library/ search index is a plain JSON cache
        // (no CF challenge) mapping slug → numeric id, covering every novel in the
        // public library. The novel page itself is CF-challenged for plain requests.
        try {
            val manifest = Json.parseToJsonElement(
                deps.httpClients.default
                    .get(requestBuilder("$baseUrl/wp-content/uploads/wor-reader-cache/search/manifest.json"))
                    .bodyAsText()
            ).jsonObject
            val indexUrl = manifest["index"]?.jsonPrimitive?.contentOrNull
            val resolved = if (indexUrl.isNullOrBlank()) null else {
                if (indexUrl.startsWith("http")) indexUrl else "$baseUrl$indexUrl"
            }
            if (resolved != null) {
                val index = Json.parseToJsonElement(
                    deps.httpClients.default.get(requestBuilder(resolved)).bodyAsText()
                ).jsonObject
                val found = index["items"]?.jsonArray?.firstOrNull { item ->
                    item.jsonObject["u"]?.jsonPrimitive?.contentOrNull
                        ?.trimEnd('/')?.substringAfterLast('/') == slug
                }?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                if (found != null) {
                    Log.info { "GalaxyNovels: novel id $found resolved from search index for $slug" }
                    return found.toString()
                }
            }
        } catch (e: Exception) {
            Log.error { "Error resolving novel id from search index: ${e.message}" }
        }

        // Second: the novel page carries data-novel-id, but a plain GET is answered with
        // a Cloudflare 403, so this must go through the Cloudflare-aware client.
        try {
            val doc = Ksoup.parse(client.get(requestBuilder(novelUrl)).bodyAsText())
            doc.selectFirst("[data-novel-id]")?.attr("data-novel-id")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        } catch (e: Exception) {
            Log.error { "Error fetching novel page: ${e.message}" }
        }

        // Last resort: a headless WebView that renders JS and solves the challenge.
        return try {
            val browserResult = deps.httpClients.browser.fetch(
                url = novelUrl,
                selector = "[data-novel-id]",
                timeout = 30000
            )
            if (browserResult.isSuccess && browserResult.responseBody.isNotBlank()) {
                Ksoup.parse(browserResult.responseBody)
                    .selectFirst("[data-novel-id]")?.attr("data-novel-id")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.error { "Error fetching novel page via browser: ${e.message}" }
            null
        }
    }

    // GET /wp-json/wor-reader/v1/offline/novels/{id}
    //   {"schema":4,"chapters_count":3184,"public_only":true,
    //    "chapters":[{"id":114442,"position":1,"order":"1.000000","number":"1",
    //                 "label":"الفصل 1","title":"يبدأ الكابوس",
    //                 "url":"https://galaxynovels.com/novel/shadow-slave/chapter-1/.../",
    //                 "published_at":"2026-06-14T17:35:58+00:00","access":"public"}, ...]}
    // position/order are 1-based ascending in the raw array; the app sorts newest-first.
    private fun parseChaptersFromOfflineApi(jsonStr: String): List<ChapterInfo> {
        return try {
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val chapters = json["chapters"]?.jsonArray ?: return emptyList()

            chapters.mapIndexedNotNull { index, ch ->
                val obj = ch.jsonObject
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                if (url.isBlank()) return@mapIndexedNotNull null

                // VIP/private chapters never render without a paid membership.
                if (obj["access"]?.jsonPrimitive?.contentOrNull != "public") {
                    return@mapIndexedNotNull null
                }

                // `position` is the reliable 1-based index. `order`/`number` are decimal
                // *strings* in this payload, so they must be read as text, not JSON ints —
                // reading them as ints throws and used to empty the whole chapter list.
                val position = obj["position"]?.jsonPrimitive?.intOrNull
                    ?: obj["order"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.toInt()
                    ?: obj["number"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.toInt()
                    ?: (index + 1)

                val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: ""
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val dateIso = obj["published_at"]?.jsonPrimitive?.contentOrNull ?: ""

                val chapterName = when {
                    label.isNotBlank() && title.isNotBlank() -> "$label : $title"
                    title.isNotBlank() -> title
                    label.isNotBlank() -> label
                    else -> "الفصل $position"
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
            Log.error { "Error parsing offline-api chapters: ${e.message}" }
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
                selector = ".wor-reader-text-surface p, .wor-reading-page__content p",
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

        // Keep both: the current Wor container and the pre-rename one, in case the
        // theme is rolled back.
        val content = doc.selectFirst(".wor-reader-text-surface")
            ?: doc.selectFirst(".wor-reading-page__content")
            ?: doc.selectFirst("[itemprop='text']")
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
