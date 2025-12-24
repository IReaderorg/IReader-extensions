package ireader.lightnovelpub

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.Parameters
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension
import kotlin.math.ceil

@Extension
abstract class LightNovelPub(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.webnovelpub.me"
    override val id: Long
        get() = 24
    override val name: String
        get() = "LightNovelPub"

    override fun getFilters(): FilterList = listOf(
        Filter.Title()
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
                "Popular",
                endpoint = "/browse/all/popular/all/{page}",
                selector = ".novel-item",
                nameSelector = ".novel-title",
                linkSelector = ".novel-title > a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                coverSelector = "img",
                coverAtt = "data-src",
                nextPageSelector = ".pagination li",
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.novel-title",
            coverSelector = "figure.cover img",
            coverAtt = "data-src",
            categorySelector = "div.categories > ul > li",
            statusSelector = "div.header-stats > span",
            onStatus = { status ->
                if (status.contains("Complete")) {
                    MangaInfo.COMPLETED
                } else {
                    MangaInfo.ONGOING
                }
            },
            authorBookSelector = ".author > a > span",
            descriptionSelector = ".summary > .content"
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = ".chapter-title",
            linkSelector = "a",
            linkAtt = "href",
            numberSelector = ".chapter-no",
            uploadDateSelector = ".chapter-update",
            addBaseUrlToLink = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "#chapter-article > section.page-in.content-wrap > div.titles > h1 > span.chapter-title",
            pageContentSelector = "#chapter-container p",
            onContent = { content ->
                content.filter { !it.contains("lightnovelpub", true) || !it.contains("no_vel_read_ing") }
            }
        )

    private fun clientBuilder(): HeadersBuilder.() -> Unit = {
        append("site-domain", "www.lightnovelpub.com")
        append("Sec-Fetch-Site", "same-origin")
        append("Sec-Fetch-Mode", "cors")
        append("Sec-Fetch-Dest", "empty")
        append("X-Requested-With", "XMLHttpRequest")
        append("accept", "*/*")
        append(
            "user-agent",
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
        )
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if (commands.isEmpty()) {
            var lastPage = 1
            kotlin.runCatching {
                lastPage = client.get(requestBuilder(manga.key)).asJsoup()
                    .select("#novel > header > div.header-body.container > div.novel-info > div.header-stats > span:nth-child(1) > strong")
                    .text().trim().let {
                        ceil(it.toDouble() / 100).toInt()
                    }
            }
            val chapters = mutableListOf<ChapterInfo>()
            for (page in 1..lastPage) {
                val html =
                    client.get(requestBuilder(manga.key + "/chapters/page-$page")).asJsoup()
                chapters.addAll(chaptersParse(html))
            }
            return chapters
        }
        return super.getChapterList(manga, commands)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value

        if (query != null) {
            val response: SearchResponse =
                client.submitForm(
                    "https://www.lightnovelpub.com/lnsearchlive",
                    formParameters = Parameters.build {
                        append("inputContent", query)
                    }
                ) { headersBuilder() }.body<SearchResponse>()
            val mangas = response.resultview.asJsoup().select(".novel-item").map { html ->
                val name = html.select("h4.novel-title").text().trim()
                val cover = html.select("img").attr("src")
                val url = baseUrl + html.select("a").attr("href")
                MangaInfo(
                    key = url,
                    title = name,
                    cover = cover
                )
            }

            return MangasPageInfo(
                mangas = mangas,
                hasNextPage = false
            )
        }
        return super.getMangaList(filters, page)
    }

    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return client to HttpRequestBuilder().apply {
            url(url)
            headersBuilder()
        }
    }
}
