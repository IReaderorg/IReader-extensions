package ireader.realmnovel

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
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangaInfo.Companion.ON_HIATUS
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
// // import java.util.Locale - Not needed for KMP - Not needed for KMP

@Extension
abstract class RealmNovel(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"  // الموقع يدعم المحتوى العربي
    
    override val baseUrl: String
        get() = "https://www.realmnovel.com"
    
    override val id: Long
        get() = 44
    override val name: String
        get() = "RealmNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf(
                "Latest",
                "Popular",
                "New",
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
                "Latest",
                endpoint = "/novels/?page={page}&sort=latest",  // افتراضي بناءً على هيكل الموقع
                selector = "div.novel-card, div.series-item, article.novel-item, .novel-grid .item",
                nameSelector = "h3 a, a.novel-title, .title a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail, .novel-cover img, .thumb img",
                coverAtt ="data-src",
                nextPageSelector = "a.next, .pagination .next, .page-numbers.next, .nav-links .next"
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/?s={query}&page={page}",  // endpoint بحث شائع للمواقع العربية
                selector = "div.novel-card, div.series-item, article.novel-item, .novel-grid .item",
                nameSelector = "h3 a, a.novel-title, .title a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail, .novel-cover img, .thumb img",
                coverAtt = "data-src",
                nextPageSelector = "a.next, .pagination .next, .page-numbers.next, .nav-links .next",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "Popular",
                endpoint = "/novels/?page={page}&sort=popular",
                selector = "div.novel-card, div.series-item, article.novel-item, .novel-grid .item",
                nameSelector = "h3 a, a.novel-title, .title a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail, .novel-cover img, .thumb img",
                coverAtt = "data-src",
                nextPageSelector = "a.next, .pagination .next, .page-numbers.next, .nav-links .next"
            ),
            BaseExploreFetcher(
                "New",
                endpoint = "/novels/?page={page}&sort=new",
                selector = "div.novel-card, div.series-item, article.novel-item, .novel-grid .item",
                nameSelector = "h3 a, a.novel-title, .title a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img.cover, img.thumbnail, .novel-cover img, .thumb img",
                coverAtt = "data-src",
                nextPageSelector = "a.next, .pagination .next, .page-numbers.next, .nav-links .next"
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.title, .novel-title, h1.entry-title, .post-title",
            coverSelector = "img.cover, .novel-cover img, .series-cover img, .thumb img",
            coverAtt = "data-src",
            descriptionSelector = "div.description, .synopsis, .summary, .post-content p:first-of-type",
            authorBookSelector = ".author a, span.author, .novel-author, .post-meta .author",
            categorySelector = ".genres a, .tags a, .categories a, .post-tags a",
            statusSelector = ".status span, .novel-status, .post-meta .status",
            onStatus = { status ->
                val lowerStatus = status.lowercase(Locale.ROOT)
                when {
                    lowerStatus.contains("ongoing") || lowerStatus.contains("مستمرة") || lowerStatus.contains("جارية") -> ONGOING
                    lowerStatus.contains("hiatus") || lowerStatus.contains("متوقفة") || lowerStatus.contains("معلقة") -> ON_HIATUS
                    lowerStatus.contains("completed") || lowerStatus.contains("مكتملة") || lowerStatus.contains("انتهت") -> COMPLETED
                    else -> ONGOING
                }
            },
        )

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            append(HttpHeaders.AcceptLanguage, "ar-SA,ar;q=0.9,en;q=0.8")  // دعم العربية
            append("Sec-Fetch-Mode", "navigate")
            append("Sec-Fetch-Site", "same-origin")
            append("Sec-Fetch-User", "?1")
            block()
        }
    }

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "ul.chapters li, .chapter-list li, div.chapter-list a, .chapters .chapter-item",
            nameSelector = "a.chapter-title, a, .chapter-link",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,  // الفصول عادةً من الأحدث إلى الأقدم في المواقع العربية
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title, h2, h1.entry-title, .post-title",
            pageContentSelector = "div.content p, div.reader p, article p, .chapter-content p, .post-content p",
            onContent = { contents: List<String> ->
                contents.map { htmlText ->
                    val cleaned = htmlText.replace(
                        Regex("(?i)(?:(?:إقرأ|اقرأ|Read)\\s*(?:فقط|only)\\s*(?:على|on)\\s*(?:realmnovel|الموقع|the site)\\.?\\s*(?:com|net)?(?:\\s*\\*?)+)"),
                        ""
                    ).replace(
                        Regex("<div class=\"[^\"]*ad[^\"]*\">[^<]*</div>"),  // إزالة الإعلانات الشائعة
                        ""
                    ).trim()
                    
                    if (cleaned.contains("<img", ignoreCase = true)) {
                        val doc = Ksoup.parseBodyFragment(cleaned)
                        doc.setBaseUri(baseUrl)
                        val images = doc.select("img")
                        images.forEach { img ->
                            var src = img.attr("src").takeIf { it.isNotEmpty() } ?: img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
                            if (src != null && !src.startsWith("http")) {
                                src = baseUrl.removeSuffix("/") + "/" + src.trimStart('/')
                            }
                            if (src != null) {
                                img.attr("src", src)
                            }
                            if (img.attr("alt").isEmpty()) {
                                img.attr("alt", "صورة من الرواية")
                            }
                        }
                        // إزالة العناصر غير المرغوبة مثل الإعلانات أو الروابط الخارجية
                        doc.select("div.ad, script, iframe, .donate").remove()
                        doc.body().html()
                    } else {
                        cleaned
                    }
                }.filter { it.isNotBlank() }
            }
        )
}
