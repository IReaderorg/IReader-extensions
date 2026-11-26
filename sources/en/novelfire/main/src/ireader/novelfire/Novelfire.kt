package ireader.novelfire

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.coroutines.delay
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

internal const val NOVELFIRE_POPULAR_ENDPOINT =
    "/search-adv?ctgcon=and&totalchapter=0&ratcon=min&rating=0&status=-1&sort=rank-top&page={page}"
internal const val NOVELFIRE_LATEST_ENDPOINT =
    "/search-adv?ctgcon=and&totalchapter=0&ratcon=min&rating=0&status=-1&sort=date&tagcon=and&page={page}"
internal const val NOVELFIRE_SEARCH_ENDPOINT = "/search?keyword={query}&page={page}"

internal const val NOVELFIRE_CHAPTERS_PER_PAGE = 100
internal const val NOVELFIRE_MAX_ATTEMPTS = 4
internal const val NOVELFIRE_DEFAULT_RETRY_MS = 10_000L
internal const val NOVELFIRE_MAX_RETRY_MS = 30_000L

internal fun novelfireChapterPages(totalChapters: Int): Int =
    (totalChapters + NOVELFIRE_CHAPTERS_PER_PAGE - 1) / NOVELFIRE_CHAPTERS_PER_PAGE

internal fun novelfireChapterPageUrls(baseUrl: String, novelPath: String, totalChapters: Int): List<String> =
    (novelfireChapterPages(totalChapters) downTo 1).map { page ->
        "$baseUrl/${novelPath.trim('/')}/chapters?page=$page"
    }

internal fun novelfireRetryDelayMs(retryAfter: String?, defaultMs: Long, maxMs: Long): Long {
    val seconds = retryAfter?.trim()?.toLongOrNull()
    return (seconds?.times(1_000L) ?: defaultMs).coerceIn(1_000L, maxMs)
}

internal fun parseNovelfireChapters(html: String, baseUrl: String): List<ChapterInfo> =
    Ksoup.parse(html).select(".chapter-list li").mapNotNull { element ->
        val link = element.selectFirst("a") ?: return@mapNotNull null
        val href = link.attr("href").takeIf { it.isNotBlank() && it != "#" } ?: return@mapNotNull null
        val name = link.attr("title").takeIf { it.isNotBlank() } ?: "No Title Found"
        val key = when {
            href.startsWith("http", ignoreCase = true) -> href
            href.startsWith("/") -> "$baseUrl$href"
            else -> "$baseUrl/$href"
        }
        ChapterInfo(name = name, key = key)
    }

/**
 * 🔥 NovelFire Source - Declarative with SourceFactory
 * 
 * Uses @AutoSourceId for automatic ID generation.
 * Uses SourceFactory's declarative fetchers for minimal boilerplate.
 */
@Extension
@AutoSourceId(seed = "Novel Fire")
abstract class Novelfire(deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://novelfire.net"
    override val id: Long get() = 7165539527173321330L
    override val name: String get() = "Novel Fire"

    // ═══════════════════════════════════════════════════════════════
    // 🔍 FILTERS
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest", "Popular")),
    )

    // ═══════════════════════════════════════════════════════════════
    // ⚡ COMMANDS
    // ═══════════════════════════════════════════════════════════════
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // ═══════════════════════════════════════════════════════════════
    // 📚 EXPLORE FETCHERS (Declarative)
    // ═══════════════════════════════════════════════════════════════
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = NOVELFIRE_POPULAR_ENDPOINT,
                selector = ".novel-item",
                nameSelector = "h4.novel-title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".novel-cover img",
                coverAtt = "data-src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Latest",
                endpoint = NOVELFIRE_LATEST_ENDPOINT,
                selector = ".novel-item",
                nameSelector = "h4.novel-title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".novel-cover img",
                coverAtt = "data-src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = NOVELFIRE_SEARCH_ENDPOINT,
                selector = ".novel-list.chapters .novel-item",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".novel-cover > img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            ),
        )

    // ═══════════════════════════════════════════════════════════════
    // 📖 DETAIL FETCHER (Declarative)
    // ═══════════════════════════════════════════════════════════════
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".novel-title",
            coverSelector = ".cover > img",
            coverAtt = "data-src",
            descriptionSelector = ".summary .content",
            authorBookSelector = ".author .property-item > span",
            categorySelector = ".categories .property-item",
            statusSelector = ".header-stats .ongoing, .header-stats .completed, .header-stats .hiatus, .header-stats span:last-child strong",
            onStatus = { status ->
                when {
                    status.contains("ongoing", ignoreCase = true) -> MangaInfo.ONGOING
                    status.contains("hiatus", ignoreCase = true) -> MangaInfo.ON_HIATUS
                    status.contains("cancelled", ignoreCase = true) ||
                        status.contains("canceled", ignoreCase = true) -> MangaInfo.CANCELLED
                    status.contains("complete", ignoreCase = true) -> MangaInfo.COMPLETED
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CHAPTER FETCHER (Declarative)
    // ═══════════════════════════════════════════════════════════════
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true
        )

    // ═══════════════════════════════════════════════════════════════
    // 📄 CONTENT FETCHER (Declarative)
    // ═══════════════════════════════════════════════════════════════
    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#content"
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CUSTOM: Paginated Chapter Fetching
    // ═══════════════════════════════════════════════════════════════
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val novelPath = manga.key.removePrefix(baseUrl).removePrefix("/")

        val detailHtml = try {
            fetchHtml(manga.key)
        } catch (e: Exception) {
            Log.error { "Error fetching novel details for ${manga.key}: ${e.message}" }
            null
        }
        if (detailHtml == null) {
            Log.error { "Unable to load chapter count for ${manga.key}" }
            return emptyList()
        }
        val totalChapters = Ksoup.parse(detailHtml)
            .selectFirst(".header-stats .icon-book-open")
            ?.parent()
            ?.text()
            ?.trim()
            ?.replace("[^\\d]".toRegex(), "")
            ?.toIntOrNull() ?: 0
        val pages = novelfireChapterPages(totalChapters)

        // Newest chapters live on the last page. Fetch backwards so a rate limit
        // can never hide the chapters a refresh is actually looking for.
        val chaptersByPage = mutableMapOf<Int, List<ChapterInfo>>()
        novelfireChapterPageUrls(baseUrl, novelPath, totalChapters).forEachIndexed { index, url ->
            val page = pages - index
            val html = try {
                fetchHtml(url)
            } catch (e: Exception) {
                Log.error { "Error fetching chapter page $page: ${e.message}" }
                null
            }
            if (html == null) {
                Log.error { "Skipping rate limited/unavailable chapter page $page" }
                return@forEachIndexed
            }
            chaptersByPage[page] = parseNovelfireChapters(html, baseUrl)
        }

        val ascending = (1..pages).flatMap { chaptersByPage[it].orEmpty() }
        return applyChapterSorting(ascending)
    }

    private suspend fun fetchHtml(url: String): String? {
        repeat(NOVELFIRE_MAX_ATTEMPTS) { attempt ->
            val response = client.get(requestBuilder(url))
            val body = response.bodyAsText()
            val rateLimited = response.status.value == 429 ||
                body.contains("you are being rate limited", ignoreCase = true)
            if (!rateLimited) return body

            Log.error { "Rate limited by Novelfire for $url (attempt ${attempt + 1})" }
            if (attempt == NOVELFIRE_MAX_ATTEMPTS - 1) return null
            delay(
                novelfireRetryDelayMs(
                    response.headers["Retry-After"],
                    NOVELFIRE_DEFAULT_RETRY_MS,
                    NOVELFIRE_MAX_RETRY_MS,
                )
            )
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════════
    // 📄 CUSTOM: Content Parsing (Remove bloat)
    // ═══════════════════════════════════════════════════════════════
    override fun pageContentParse(document: Document): List<Page> {
        document.select(".box-ads, .box-notification").remove()
        document.select("*").forEach { if (it.tagName().startsWith("nf")) it.remove() }
        return document.select("#content p").map { Text(it.text()) }
    }
}
