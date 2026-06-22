package ireader.riwyat

import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.post
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.Page
import ireader.core.source.SourceFactory
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

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novel/page/{page}",
                selector = ".page-item-detail",
                nameSelector = ".h5 > a",
                linkSelector = ".h5 > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".orw-pagination a.next, a.next.page-numbers",
            ),
            BaseExploreFetcher(
                "Most Views",
                endpoint = "/novel/page/{page}/?m_orderby=views",
                selector = ".page-item-detail",
                nameSelector = ".h5 > a",
                linkSelector = ".h5 > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".orw-pagination a.next, a.next.page-numbers",
            ),
            BaseExploreFetcher(
                "New",
                endpoint = "/novel/page/{page}/?m_orderby=new-manga",
                selector = ".page-item-detail",
                nameSelector = ".h5 > a",
                linkSelector = ".h5 > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".orw-pagination a.next, a.next.page-numbers",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}&post_type=wp-manga",
                selector = ".c-tabs-item__content",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "a img",
                coverAtt = "src",
                nextPageSelector = ".nav-previous",
                nextPageValue = "Older Posts",
                type = SourceFactory.Type.Search
            ),
        )
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".manga-title h2",
            coverSelector = ".summary_image img",
            coverAtt = "src",
            descriptionSelector = ".description-summary p",
            statusSelector = ".manga-status .nhv-meta-value, .genres",
            onStatus = { status ->
                when {
                    status.contains("مستمرة") || status.contains("Ongoing", true) -> MangaInfo.ONGOING
                    status.contains("مكتملة") || status.contains("Completed", true) -> MangaInfo.COMPLETED
                    else -> MangaInfo.UNKNOWN
                }
            },
        )
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li.wp-manga-chapter a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h3.chapter-name",
            pageContentSelector = ".reading-content .text-left p",
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
        if (commands.isEmpty()) {
            return chaptersParse(
                client.post(requestBuilder(manga.key + "ajax/chapters/")).asJsoup(),
            ).reversed()
        }
        return super.getChapterList(manga, commands)
    }
}
