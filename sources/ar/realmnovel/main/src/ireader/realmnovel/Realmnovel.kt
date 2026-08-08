package ireader.realmnovel

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import ireader.core.http.noCache
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.SourceFactory
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangaInfo.Companion.ON_HIATUS
import ireader.core.source.model.MangasPageInfo
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension
import tachiyomix.annotations.GenerateTests
import tachiyomix.annotations.TestExpectations
import tachiyomix.annotations.TestFixture

@Extension
@AutoSourceId(seed = "RealmNovel")
@GenerateTests(
    unitTests = true,
    integrationTests = false,
    searchQuery = "سيد",
    minSearchResults = 1,
)
@TestFixture(
    novelUrl = "https://realmnovel.com/novel/69edb1e4d53949a7ab4927eb",
    chapterUrl = "https://realmnovel.com/novel/69edb1e4d53949a7ab4927eb/chapter/1",
    expectedTitle = "إله الشعب: التضحية بتريليونات من أجل الصعود",
    expectedAuthor = "",
    expectedMinChapters = 930,
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 930,
)
abstract class RealmNovel(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://realmnovel.com"
    override val id: Long
        get() = RealmNovelSourceId.ID
    override val name: String
        get() = "RealmNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override fun requestBuilder(url: String, block: HeadersBuilder.() -> Unit): HttpRequestBuilder {
        return super.requestBuilder(url, block).apply {
            noCache()
        }
    }

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            append(HttpHeaders.AcceptLanguage, "ar-SA,ar;q=0.9,en;q=0.8")
            block()
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = if (page <= 1) "$baseUrl/" else "$baseUrl/?page=$page"
        return fetchNovelList(url)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        if (query.isNullOrBlank()) {
            return getMangaList(sort = null, page = page)
        }
        val encodedQuery = query.encodeURLParameter()
        val url = if (page <= 1) {
            "$baseUrl/?q=$encodedQuery"
        } else {
            "$baseUrl/?q=$encodedQuery&page=$page"
        }
        return fetchNovelList(url)
    }

    private suspend fun fetchNovelList(url: String): MangasPageInfo {
        return try {
            val response = client.get(requestBuilder(url)).bodyAsText()
            val document = response.asJsoup()
            val novels = document.select("a.g3card").mapNotNull { element ->
                runCatching {
                    val title = element.selectFirst(".g3title")?.text()?.trim()
                        ?: return@runCatching null
                    val key = element.attr("href").trim()
                        ?: return@runCatching null
                    val cover = element.selectFirst(".g3cover img")?.attr("src")?.trim()
                        ?: ""
                    val statusText = element.selectFirst(".g3status")?.text()?.trim() ?: ""
                    MangaInfo(
                        key = buildAbsoluteUrl(key),
                        title = title,
                        cover = if (cover.startsWith("/")) "$baseUrl$cover" else cover,
                        status = parseStatus(statusText),
                    )
                }.getOrNull()
            }
            val hasNext = document.selectFirst("nav.pager.seo-pager a[rel=\"next\"]") != null
            MangasPageInfo(novels, hasNext)
        } catch (e: Exception) {
            MangasPageInfo(emptyList(), false)
        }
    }

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "article.novel-head h1.h1",
            coverSelector = "article.novel-head img.cover",
            coverAtt = "src",
            addBaseurlToCoverLink = true,
            descriptionSelector = "article.novel-head p.desc",
            categorySelector = "article.novel-head a.tag",
            statusSelector = "article.novel-head .badge:first-child",
            onStatus = { status -> parseStatus(status) },
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a.chapter-row",
            reverseChapterList = true,
        )

    override fun chaptersParse(document: Document): List<ChapterInfo> {
        return document.select("a.chapter-row")
            .mapNotNull { element ->
                runCatching {
                    val key = element.attr("href").trim()
                        ?: return@runCatching null
                    val number = key.substringAfterLast("/chapter/").toIntOrNull()
                        ?: return@runCatching null
                    ChapterInfo(
                        key = buildAbsoluteUrl(key),
                        name = "الفصل $number",
                        number = number.toFloat(),
                    )
                }.getOrNull()
            }
    }

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h1.h1",
            pageContentSelector = ".chapter-content p",
            onContent = { paragraphs ->
                paragraphs.map { it.trim() }
                    .filter { line ->
                        line.length >= 3 &&
                            !line.contains("اقرأ فقط على", ignoreCase = true) &&
                            !line.contains("إقرأ فقط على", ignoreCase = true) &&
                            !line.contains("تابع القراءة", ignoreCase = true) &&
                            !line.contains("الفصل السابق", ignoreCase = true) &&
                            !line.contains("الفصل التالي", ignoreCase = true)
                    }
            },
        )

    private fun parseStatus(status: String): Long {
        return when {
            status.contains("مكتملة") || status.contains("completed", ignoreCase = true) -> COMPLETED
            status.contains("متوقفة") || status.contains("معلقة") || status.contains("hiatus", ignoreCase = true) -> ON_HIATUS
            status.contains("مستمرة") || status.contains("جارية") || status.contains("ongoing", ignoreCase = true) -> ONGOING
            else -> MangaInfo.UNKNOWN
        }
    }
}
