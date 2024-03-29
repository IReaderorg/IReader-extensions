package ireader.readwn

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.Parameters
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class ReaderWnScraper(private val deps: Dependencies, private val sourceId: Long,private val key: String,private val sourceName: String, private val language:String) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = language
    override val baseUrl: String
        get() = key
    override val id: Long
        get() = sourceId
    override val name: String
        get() = sourceName


    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Trending",
                endpoint = "/list/all/all-onclick-{page}.html",
                selector = "li.novel-item",
                nameSelector = "h4",
                coverSelector = ".novel-cover > img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                maxPage = 998

            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search/index.php",
                selector = "li.novel-item",
                nameSelector = "h4",
                coverSelector = "img",
                coverAtt = "data-src",
                addBaseurlToCoverLink = true,
                linkSelector = "a'",
                linkAtt = "href",
                addBaseUrlToLink = true,
                type = SourceFactory.Type.Search
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.novel-title",
            coverSelector = "figure.cover > img",
            coverAtt = "data-src",
            addBaseurlToCoverLink = true,
            authorBookSelector = "span[itemprop=author]",
            categorySelector = "div.categories > ul > li",
            descriptionSelector = ".summary",
            statusSelector = "div.header-stats > spa",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".titles h2",
            pageContentSelector = ".chapter-content p",
        )

    override fun pageContentParse(document: Document): List<Page> {
        val par = selectorReturnerListType(
            document,
            selector = contentFetcher.pageContentSelector,
            contentFetcher.pageContentAtt
        ).filter { it.isNotBlank() }.let { par ->
            par.ifEmpty {
                document.select(".chapter-content").html().split("<br>")
                    .map { Jsoup.parse(it).text() }
            }
        }.let {
            contentFetcher.onContent(it)
        }

        val head = selectorReturnerStringType(
            document,
            selector = contentFetcher.pageTitleSelector,
            contentFetcher.pageTitleAtt
        ).let {
            contentFetcher.onTitle(it)
        }

        return listOf(head.toPage()) + par.map { it.toPage() }
    }
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if (commands.isEmpty()) {
            val lastChapterNo = client.get(manga.key).asJsoup().select(".header-stats span > strong").first()?.text()?.toIntOrNull() ?: 0
            val chapters = mutableListOf<ChapterInfo>()
            val novelId = manga.key.replace(".html", "")
            for (i in 1..lastChapterNo) {
                val chapterName = "Chapter $i"
                val url = "${novelId}_$i.html"
                chapters.add(ChapterInfo(key = url, name = chapterName, number = i.toFloat()))
            }
            return chapters
        }
        return super.getChapterList(manga, commands)
    }

    override suspend fun getListRequest(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String
    ): Document {
        if (baseExploreFetcher.key == "Search") {
            return client.submitForm(
                url = "$baseUrl/e/search/index.php",
                formParameters = Parameters.build {
                    append("show", "title")
                    append("tempid", "1")
                    append("tbname", "news")
                    append("keyboard", query)
                }
            ) {
                headersBuilder {
                    append("Content-Type", "application/x-www-form-urlencoded")
                    append("Referer", "$baseUrl/search.html")
                    append("Origin", baseUrl)
                    append("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 Safari/537.36")
                }
            }.asJsoup()
        }
        return super.getListRequest(baseExploreFetcher, page, query)
    }
}
