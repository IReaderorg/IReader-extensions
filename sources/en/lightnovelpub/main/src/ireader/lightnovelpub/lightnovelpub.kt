package ireader.lightnovelpub

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil


@Extension
abstract class LightNovelPub(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.lightnovelpub.com"
    override val id: Long
        get() = 24
    override val name: String
        get() =  "LightNovelPub"
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
                nextPageValue = ">>",
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.novel-title",
            coverSelector = "figure.cover > img",
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

    val dateFormatter =  SimpleDateFormat("MMM dd,yyyy", Locale.US)
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = ".chapter-title",
            linkSelector = "a",
            linkAtt = "href",
            numberSelector = ".chapter-no",
            uploadDateSelector = ".chapter-update",
            uploadDateParser = { date ->
                if (date.contains("ago")) {
                    val value = date.split(' ')[0].toInt()
                    when {
                        "min" in date -> Calendar.getInstance().apply {
                            add(Calendar.MINUTE, value * -1)
                        }.timeInMillis
                        "hour" in date -> Calendar.getInstance().apply {
                            add(Calendar.HOUR_OF_DAY, value * -1)
                        }.timeInMillis
                        "day" in date -> Calendar.getInstance().apply {
                            add(Calendar.DATE, value * -1)
                        }.timeInMillis
                        "week" in date -> Calendar.getInstance().apply {
                            add(Calendar.DATE, value * 7 * -1)
                        }.timeInMillis
                        "month" in date -> Calendar.getInstance().apply {
                            add(Calendar.MONTH, value * -1)
                        }.timeInMillis
                        "year" in date -> Calendar.getInstance().apply {
                            add(Calendar.YEAR, value * -1)
                        }.timeInMillis
                        else -> {
                            0L
                        }
                    }
                } else {
                    try {
                        dateFormatter.parse(date)?.time ?: 0
                    } catch (_: Exception) {
                        0L
                    }
                }
            },
            addBaseUrlToLink = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "h2",
            pageContentSelector = "#chapter-container p",
        )

    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
        BrowserUserAgent()
    }

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
                    client.get(requestBuilder(manga.key + "/chapters/page-${page}")).asJsoup()
                chapters.addAll(chaptersParse(html))
            }
            return chapters
        }
        return super.getChapterList(manga, commands).reversed()
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value

        if (query != null) {
            val response: SearchResponse =
                client.submitForm {
                    url("https://www.lightnovelpub.com/lnsearchlive")
                    parameter("inputContent",query)
                            headers( clientBuilder())
                }.body<SearchResponse>()
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


}