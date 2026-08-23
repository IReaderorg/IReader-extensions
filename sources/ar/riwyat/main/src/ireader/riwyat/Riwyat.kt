package ireader.riwyat

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.headers
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.SourceFactory
import ireader.core.source.findInstance
import ireader.core.http.noCache
import ireader.core.util.DefaultDispatcher
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    "status",
    1
)
@TestFixture(
    "https://cenele.com/cont/beyond-the-time/",
    chapterUrl = "https://cenele.com/cont/beyond-the-time/%d8%a7%d9%84%d9%81%d8%b5%d9%84-1321/",
    expectedAuthor = "",
    expectedTitle = "رواية ما وراء الزمن",

    )
@TestExpectations()
abstract class Riwyat(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://cenele.com"
    override val id: Long
        get() = 23
    override val name: String
        get() = "Riwyat(cenele)"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override fun getListings(): List<Listing> = listOf(
        LatestNovelsListing(),
        MostViewedNovelsListing(),
        TopRatedNovelsListing(),
        MostPromotedNovelsListing(),
        AToZNovelsListing()
    )

    class LatestNovelsListing : Listing("Latest")
    class MostViewedNovelsListing : Listing("Most Viewed")
    class TopRatedNovelsListing : Listing("Top Rated")
    class MostPromotedNovelsListing : Listing("Most Promoted")
    class AToZNovelsListing : Listing("A-Z")

    override fun requestBuilder(url: String, block: HeadersBuilder.() -> Unit): HttpRequestBuilder {
        return super.requestBuilder(url, block).apply {
            noCache()
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = when (sort) {
            is MostViewedNovelsListing -> "$baseUrl/cont/?m_orderby=views&paged=$page"
            is TopRatedNovelsListing -> "$baseUrl/cont/?m_orderby=rating&paged=$page"
            is MostPromotedNovelsListing -> "$baseUrl/cont/?m_orderby=promoted&paged=$page"
            is AToZNovelsListing -> "$baseUrl/cont/?m_orderby=title&paged=$page"
            else -> "$baseUrl/cont/?paged=$page"
        }
        return fetchNovelList(url)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        if (!query.isNullOrBlank()) {
            val encodedQuery = query.encodeURLParameter()
            val searchUrl = "$baseUrl/wp-admin/admin-ajax.php?action=nhv_manga_suggest&term=$encodedQuery"
            return fetchNovelListViaAjax(searchUrl)
        }
        return getMangaList(sort = null, page = page)
    }

    private suspend fun fetchNovelListViaAjax(url: String): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder(url)).bodyAsText()
            val json = Json.parseToJsonElement(response).jsonObject
            val items = json["data"]?.jsonObject?.get("items")?.jsonArray
            val novels = items?.mapNotNull { item ->
                val obj = item.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val link = obj["url"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val cover = obj["thumb"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                MangaInfo(
                    key = link,
                    title = title,
                    cover = cover,
                )
            } ?: emptyList()
            MangasPageInfo(novels, false)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    private suspend fun fetchNovelList(url: String): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder(url)).bodyAsText()
            val document = response.asJsoup()
            val items = document.select("article.nhv-library-card")
            val novels = items.mapNotNull { element ->
                val title = element.selectFirst("h2.nhv-library-card__title a")?.text()?.trim() ?: return@mapNotNull null
                val link = element.selectFirst("h2.nhv-library-card__title a")?.attr("href")?.trim() ?: return@mapNotNull null
                val cover = element.selectFirst("a.nhv-library-card__cover img")?.attr("src")?.trim() ?: ""
                MangaInfo(
                    key = link,
                    title = title,
                    cover = cover,
                )
            }
            val hasNext = document.selectFirst("nav.nhv-archive-pagination a.next.page-numbers") != null
            MangasPageInfo(novels, hasNext)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.nhv-novel-title",
            coverSelector = "div.nhv-novel-cover img",
            coverAtt = "src",
            descriptionSelector = "div.nhv-novel-synopsis p",
            authorBookSelector = "div.nhv-novel-meta a[href*='/cont-author/']",
            categorySelector = "div.nhv-novel-genres a",
            statusSelector = "div.nhv-novel-meta div.nhv-novel-status",
            onStatus = { status ->
                when {
                    status.contains("مكتملة") || status.contains("Completed", true) -> MangaInfo.COMPLETED
                    status.contains("مستمرة") || status.contains("Ongoing", true) -> MangaInfo.ONGOING
                    else -> MangaInfo.UNKNOWN
                }
            },
            addBaseurlToCoverLink = true,
        )
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li.wp-manga-chapter",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false,
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h3.chapter-name",
            pageContentSelector = ".reading-content-wrap.chapter-type-text p",
        )

    private val invisibleCharRegex = Regex(
        "[\\u200B-\\u200F\\u2028-\\u202F\\u2060-\\u206F\\uFEFF\\u00AD\\u034F\\u061C\\u115F-\\u1160\\u17B4-\\u17B5\\u180E\\u2000-\\u200A]"
    )

    private val watermarkIndicatorRegex = Regex(
        "فضا⁣ء|فضاء|شاي|رواي.?ات|تطبي.?ق|سار.?ق|مسرو.?ق|ت.?مويه|محتو.?ى|بدون.?اذن|بدون.?إذن" +
        "|حقوق.?الترجمة|حقوق.?النشر|جميع.?الحقوق|يقوم.?بنقل|ينقل.?المحتوى" +
        "|ohnovel|novelfull|cenele|cenel|tale.?read|app|تحميل|تنزيل" +
        "#\\w{4,}",
        RegexOption.IGNORE_CASE
    )

    private fun sanitizeElement(element: com.fleeksoft.ksoup.nodes.Element) {
        element.select(
            "span[aria-hidden=true], span[role=presentation], span[data-nosnippet=true], " +
            "input[type=hidden]"
        ).remove()
        element.select("span[style]").filter { s ->
            val style = s.attr("style")
            style.contains("opacity:0") || style.contains("visibility:hidden") ||
            (style.contains("overflow:hidden") && style.contains("position:absolute")) ||
            style.contains("clip-path")
        }.forEach { it.remove() }
        element.select("span[data-ro4q3prp], span[data-elgslyf8], span[data-mlnolt4r], " +
            "span[data-we8luxao], span[data-bkq0thcb], span[data-o4ufbgva], " +
            "span[data-ixrnb3k6], span[data-rfdne8tt]").remove()
        element.select("span[id^=data-]").remove()
    }

    override fun pageContentParse(document: Document): List<Page> {
        val doc = document.clone()

        val allParagraphs = doc.select(contentFetcher.pageContentSelector!!)
            .mapNotNull { element ->
                sanitizeElement(element)
                var text = element.text()
                text = invisibleCharRegex.replace(text, "")
                text = text.replace(Regex("\\s{2,}"), " ").trim()

                if (text.length < 4) return@mapNotNull null
                if (watermarkIndicatorRegex.containsMatchIn(text)) return@mapNotNull null

                text
            }

        val head = selectorReturnerStringType(
            doc,
            selector = contentFetcher.pageTitleSelector,
            contentFetcher.pageTitleAtt
        )

        return listOf(head.toPage()) + allParagraphs.map { it.toPage() }
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            if (cmd.html.isNotBlank()) {
                val chapters = chaptersParse(cmd.html.asJsoup())
                return chapters.sortedBy { it.number }
            }
        }

        val detailDoc = client.get(requestBuilder(manga.key)).asJsoup()

        val nonce = extractChaptersNonce(detailDoc)
        val postId = extractPostId(detailDoc)

        if (nonce != null && postId != null) {
            try {
                val ajaxChapters = fetchChaptersViaAjax(postId, nonce)
                if (ajaxChapters.isNotEmpty()) {
                    return ajaxChapters.sortedBy { it.number }
                }
            } catch (e: Exception) {
            }
        }

        val staticChapters = chaptersParse(detailDoc)
        if (staticChapters.isNotEmpty()) {
            return staticChapters.sortedBy { it.number }
        }

        return emptyList()
    }

    private fun extractChaptersNonce(doc: Document): String? {
        val scriptText = doc.selectFirst("script:containsData(chaptersNonce)")?.data() ?: return null
        return Regex("chaptersNonce[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']")
            .find(scriptText)?.groupValues?.get(1)
    }

    private fun extractPostId(doc: Document): Int? {
        doc.selectFirst("[data-post-id]")?.attr("data-post-id")?.toIntOrNull()?.let { return it }
        doc.selectFirst("[data-post]")?.attr("data-post")?.toIntOrNull()?.let { return it }
        doc.select("script:containsData(postId)").forEach { script ->
            val scriptText = script.data()
            Regex("postId[\"']?\\s*[:=]\\s*[\"'](\\d+)[\"']")
                .find(scriptText)?.groupValues?.get(1)?.toIntOrNull()
                ?.let { return it }
            Regex("postId[\"']?\\s*[:=]\\s*(\\d+)(?=[,\"}])")
                .find(scriptText)?.groupValues?.get(1)?.toIntOrNull()
                ?.let { return it }
        }
        return null
    }

    private suspend fun fetchChaptersViaAjax(postId: Int, nonce: String): List<ChapterInfo> = withContext(DefaultDispatcher) {
        val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"
        val perPage = 100

        suspend fun fetchPage(page: Int): List<ChapterInfo> {
            return runCatching {
                val response = client.submitForm(
                    url = ajaxUrl,
                    formParameters = Parameters.build {
                        append("action", "nhv_manga_single_chapters_page")
                        append("nonce", nonce)
                        append("manga_id", postId.toString())
                        append("volume", "-1")
                        append("page", page.toString())
                        append("per_page", perPage.toString())
                        append("order", "desc")
                    }
                ) {
                    headersBuilder()
                }.bodyAsText()

                val json = Json.parseToJsonElement(response).jsonObject
                val html = json["html"]?.jsonPrimitive?.contentOrNull ?: ""
                val document = html.asJsoup()
                chaptersParse(document)
            }.getOrNull() ?: emptyList()
        }

        val firstResponse = runCatching {
            client.submitForm(
                url = ajaxUrl,
                formParameters = Parameters.build {
                    append("action", "nhv_manga_single_chapters_page")
                    append("nonce", nonce)
                    append("manga_id", postId.toString())
                    append("volume", "-1")
                    append("page", "1")
                    append("per_page", perPage.toString())
                    append("order", "desc")
                }
            ) {
                headersBuilder()
            }.bodyAsText()
        }.getOrNull() ?: return@withContext emptyList()

        val firstJson = Json.parseToJsonElement(firstResponse).jsonObject
        val firstHtml = firstJson["html"]?.jsonPrimitive?.contentOrNull ?: ""
        val firstHasMore = firstJson["has_more"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        val total = firstJson["total"]?.jsonPrimitive?.intOrNull ?: 0
        if (total == 0 && firstHtml.isBlank()) return@withContext emptyList()

        val firstDoc = firstHtml.asJsoup()
        val allChapters = chaptersParse(firstDoc).toMutableList()

        if (!firstHasMore || total <= perPage) return@withContext allChapters

        val maxPage = (total + perPage - 1) / perPage
        val deferred = (2..maxPage).map { page ->
            async { fetchPage(page) }
        }

        allChapters.addAll(deferred.awaitAll().flatten())
        return@withContext allChapters
    }

    private fun extractChapterNumberFromUrl(url: String): Float {
        val match = Regex("""/(\d+)/?$""").find(url)
        return match?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    }
}
