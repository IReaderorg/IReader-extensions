package ireader.rewayatfans

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class RewayatFans(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://rewayatfans.com"
    override val id: Long
        get() = 42
    override val name: String
        get() = "روايات فانز"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "الترتيب حسب:",
            arrayOf(
                "الأحدث",
                "الشائعة",
                "المكتملة",
                "الجديدة"
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "الأحدث",
                endpoint = "/",
                selector = "div.post-list li, ul.recent-posts li, .novel-item",
                nameSelector = "h2 a, .post-title a, h3 a",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".pagination .next, a[rel='next'], .page-numbers.next",
                onLink = { link -> if (!link.startsWith("http")) baseUrl + link else link }
            ),
            BaseExploreFetcher(
                "البحث",
                endpoint = "/?s={query}",
                selector = "div.search-results li, ul li:has(a), .post-item",
                nameSelector = "h2 a, .entry-title a",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".pagination .next, a[rel='next']",
                type = SourceFactory.Type.Search,
                onLink = { link -> if (!link.startsWith("http")) baseUrl + link else link }
            ),
            BaseExploreFetcher(
                "الشائعة",
                endpoint = "/popular-novels/",
                selector = "div.popular-list li, ul li.popular, .trending-item",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".pagination .next",
                onLink = { link -> if (!link.startsWith("http")) baseUrl + link else link }
            ),
            BaseExploreFetcher(
                "المكتملة",
                endpoint = "/completed/",
                selector = "div.completed-list li, ul li.completed, .post-item:has(.status:contains(مكتمل))",
                nameSelector = "h2 a, .entry-title a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".pagination .next",
                onLink = { link -> if (!link.startsWith("http")) baseUrl + link else link }
            ),
            BaseExploreFetcher(
                "الجديدة",
                endpoint = "/new-novels/",
                selector = "div.new-list li, ul li.new, .recent-item",
                nameSelector = "a",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".pagination .next",
                onLink = { link -> if (!link.startsWith("http")) baseUrl + link else link }
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.entry-title, .novel-title, h1.post-title, title",
            coverSelector = "div.seriethumb img, .cover-image img, .entry-thumb img, img[src*='cover']",
            coverAtt = "src",
            descriptionSelector = "div.entry-content p:first-of-type, .novel-summary, .description p, div.seriessyn",
            authorBookSelector = ".post-meta .author a, div:contains(الكاتب) a, .novel-author a",
            categorySelector = ".post-tags a, .genres a, .tags a, div.genres a",
            statusSelector = ".post-meta .status, .novel-status span, div.seriestat, .status",
            onStatus = { status ->
                val lowerStatus = status.lowercase()
                when {
                    lowerStatus.contains("مكتمل") || lowerStatus.contains("النهاية") || lowerStatus.contains("completed") || lowerStatus.contains("the end") -> MangaInfo.COMPLETED
                    lowerStatus.contains("مستمر") || lowerStatus.contains("ongoing") || lowerStatus.contains("جاري") -> MangaInfo.ONGOING
                    lowerStatus.contains("توقف") || lowerStatus.contains("hiatus") || lowerStatus.contains("معلق") -> MangaInfo.ON_HIATUS
                    else -> MangaInfo.UNKNOWN
                }
            },
            onDescription = { desc ->
                desc.replace(Regex("<[^>]*>"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        )

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.AcceptLanguage, "ar-SA,ar;q=0.9,en-US;q=0.8,en;q=0.7")
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            append(HttpHeaders.AcceptEncoding, "gzip, deflate, br")
        }
    }

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ul.chapter-list li, div.chapters-list li, .wp-block-list li, ul li:has(a:contains(فصل)), .chapter-item",
            nameSelector = "a.chapter-title, a, span.chap-title",
            nameIncludeChapterNumber = true,
            linkSelector = "a",
            linkAtt = "href",
            dateSelector = ".chapter-date, time",
            reverseChapterList = false,
            onChapter = { chapter ->
                chapter.copy(
                    url = if (!chapter.url.startsWith("http")) baseUrl + chapter.url else chapter.url,
                    name = chapter.name.replace(Regex("فصل\\s*\\d+\\s*:?\\s*"), "فصل ")
                )
            }
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".entry-header h1, .chapter-title, h1.post-title, title",
            pageContentSelector = "div.entry-content > p, .chapter-content p, div.reading-content p, ol li p, div#content p",
            nextContentSelector = ".next-chapter a, .chapter-nav .next",
            onContent = { contents: List<String> ->
                contents.mapNotNull { text ->
                    val cleaned = text
                        .replace(Regex("(?i)(new|النهاية|the end|اقرأ فقط على rewayatfans|إعلان|discord|twitter|facebook|.*rewayatfans\\.com.*)"), "")
                        .replace(Regex("<[^>]*>"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (cleaned.length > 50 && !cleaned.contains("تعليق", ignoreCase = true) && !cleaned.matches(Regex("^\\*+$"))) {
                        cleaned
                    } else null
                }.filter { it.isNotEmpty() }
            }
        )
}
