package ireader.pandanovel

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import ireader.pandanovel.chapter.ChapterDTO
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class PandaNovel(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.panda-novel.com"
    override val id: Long
        get() = 29
    override val name: String
        get() = "PandaNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )
    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "All",
                endpoint = "/browsenovel/all/all/all/all/all/{page}",
                selector = ".novel-ul .novel-li",
                nameSelector = "i",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                coverSelector = "i",
                coverAtt = "data-src",
                nextPageSelector = "#pagination > ul > li:nth-child(7) > span",
                nextPageValue = "..."
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".novel-desc h1",
            coverSelector = ".novel-cover i",
            onCover = {
               it.substringAfter("background-image: url(\"").substringBefore("\");")
            },
            coverAtt = "style",
            authorBookSelector = ".novel-attr a",
            categorySelector = ".novel-labels a",
            descriptionSelector = ".synopsis-body .synopsis-content",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li a",
            nameSelector = "span",
            onName = {
                it.substringAfter(";")
            },
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true
        )

    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return deps.httpClients.cloudflareClient to HttpRequestBuilder().apply {
            url(url)
            headersBuilder()
        }
    }

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".novel-content h2",
            pageContentSelector = "#novelArticle1",
            onContent = { contents ->
                contents.joinToString("\n").split("\n")
            }
        )


    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if(commands.isEmpty()) {
            val bookId = manga.key.substringAfterLast("-")
            val time = Clock.System.now().toEpochMilliseconds()

            val json : String = client.get("https://www.panda-novel.com/api/book/chapters/$bookId/1?_=$time").body()
            val chapters = Gson().fromJson<ChapterDTO>(json,ChapterDTO::class.java)

            return chapters.data.list.mapIndexed { index, info ->
                ChapterInfo(
                    key = baseUrl+info.chapterUrl,
                    name = info.name,
                    dateUpload = Instant.parse(info.updatedAt).toEpochMilliseconds(),
                    number = (index + 1).toFloat(),
                )
            }.reversed()
        }
        return super.getChapterList(manga, commands).reversed()

    }

    override suspend fun getContentRequest(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): Document {
        //return deps.httpClients.cloudflareClient.get(requestBuilder(chapter.key)).asJsoup()
        return deps.httpClients.browser.fetch(
            chapter.key,
            selector = "#novelArticle1",
            timeout = 50000
        ).responseBody.asJsoup()
    }


    override fun pageContentParse(document: Document): List<String> {

        Log.error { document.html() }
        val par =   document.select(contentFetcher.pageContentSelector).html().split("<br>").map { it.asJsoup().text() }
        val head = selectorReturnerStringType(
            document,
            selector = contentFetcher.pageTitleSelector,
            contentFetcher.pageTitleAtt
        ).let {
            contentFetcher.onTitle(it)
        }

        return listOf(head) + par
    }

}