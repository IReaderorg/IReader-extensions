package ireader.rewayatclub

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
abstract class RewayatClub(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "ar"
    override val baseUrl: String
        get() = "https://rewayat.club"
    override val id: Long
        get() = 43 // يجب أن يكون فريدًا لكل مصدر
    override val name: String
        get() = "Rewayat Club"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "ترتيب حسب",
            arrayOf(
                "الأحدث",
                "الأكثر شيوعاً",
                "عدد الفصول",
                "الأقدم"
            )
        ),
        Filter.Select(
            "نوع الرواية",
            arrayOf(
                "جميع الروايات" to "0",
                "مترجمة" to "1",
                "مؤلفة" to "2",
                "مكتملة" to "3"
            )
        ),
        Filter.Group("الأنواع", listOf(
            Filter.Checkbox("كوميديا", "1"),
            Filter.Checkbox("أكشن", "2"),
            Filter.Checkbox("دراما", "3"),
            Filter.Checkbox("فانتازيا", "4"),
            Filter.Checkbox("مهارات القتال", "5"),
            Filter.Checkbox("مغامرة", "6"),
            Filter.Checkbox("رومانسي", "7"),
            Filter.Checkbox("خيال علمي", "8"),
            Filter.Checkbox("الحياة المدرسية", "9"),
            Filter.Checkbox("قوى خارقة", "10"),
            Filter.Checkbox("سحر", "11"),
            Filter.Checkbox("رياضة", "12"),
            Filter.Checkbox("رعب", "13"),
            Filter.Checkbox("حريم", "14")
        ))
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
                endpoint = "/api/chapters/weekly/list/?page={page}",
                selector = ".recent-list li, ul li",
                nameSelector = "a, .title",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next, .pagination a:contains(التالى)"
            ),
            BaseExploreFetcher(
                "البحث",
                endpoint = "/api/novels/?type=0&ordering=-num_chapters&page={page}&search={query}",
                selector = ".search-results li, ul li",
                nameSelector = "a, .title",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next",
                type = SourceFactory.Type.Search
            ),
            BaseExploreFetcher(
                "الروايات",
                endpoint = "/api/novels/?page={page}",
                selector = ".novel-list li, ul li",
                nameSelector = "a, .title",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next, .pagination a"
            ),
            BaseExploreFetcher(
                "المكتملة",
                endpoint = "/api/novels/?type=3&page={page}",
                selector = ".completed-list li, ul li",
                nameSelector = "a, .title",
                nameAtt = "text",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = "a.next"
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.primary--text span, h1.title",
            coverSelector = "img.cover, img.poster",
            coverAtt = "src",
            descriptionSelector = "div.text-pre-line span, .summary, .description",
            authorBookSelector = ".novel-author, .author",
            categorySelector = ".v-slide-group__content a, .genres a, .tags a",
            statusSelector = ".v-chip__content, .status",
            onStatus = { status ->
                when {
                    status.contains("مكتملة") || status.contains("Completed") -> MangaInfo.COMPLETED
                    status.contains("مستمرة") || status.contains("Ongoing") -> MangaInfo.ONGOING
                    status.contains("متوقفة") || status.contains("Hiatus") -> MangaInfo.ON_HIATUS
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.AcceptLanguage, "ar-SA,ar;q=0.9,en-US;q=0.8,en;q=0.7")
            append(HttpHeaders.Accept, "application/json, text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
    }

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li, ul li",
            nameSelector = "a, .chapter-title",
            linkSelector = "a",
            linkAtt = "href",
            chapterNumberSelector = ".chapter-number, .number",
            chapterNumberAtt = "text",
            reverseChapterList = false
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title, h1",
            pageContentSelector = ".chapter-content p, .content p",
            onContent = { contents: List<String> ->
                contents.map { text ->
                    text.replace(Regex("\\s+"), " ")
                        .trim()
                        .takeIf { it.isNotEmpty() } ?: ""
                }.filter { it.isNotEmpty() }
            }
        )
}
