package ireader.pandanovel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import ireader.pandanovel.chapter.ChapterDTO
import ireader.sourcefactory.SourceFactory
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.Command
import org.ireader.core_api.source.model.CommandList
import org.ireader.core_api.source.model.Filter
import org.ireader.core_api.source.model.FilterList
import org.ireader.core_api.source.model.MangaInfo
import org.ireader.core_api.source.model.Page
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.Locale

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

    @OptIn(ExperimentalSerializationApi::class)
    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    allowSpecialFloatingPointValues = true
                    allowStructuredMapKeys = true
                    explicitNulls = true
                }
            )
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
            addBaseUrlToLink = true,
            reverseChapterList = true
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

    override suspend fun getListRequest(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String
    ): Document {
        return deps.httpClients.browser.fetch(
            baseUrl + baseExploreFetcher.endpoint?.replace(
                "{page}",
                page.toString()
            )?.replace("{query}", query.replace(" ", "+")),
            baseExploreFetcher.selector,
            timeout = 50000
        ).responseBody.asJsoup()
    }

    override suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return deps.httpClients.browser.fetch(
            manga.key,
            chapterFetcher.nameSelector,
        ).responseBody.asJsoup()
    }

    override suspend fun getMangaDetailsRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return deps.httpClients.browser.fetch(
            manga.key,
            detailFetcher.nameSelector,
        ).responseBody.asJsoup()
    }

    private val jsonFormatter = Json {
        ignoreUnknownKeys = true
    }
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if (commands.isEmpty()) {
            val bookId = manga.key.substringAfterLast("-")
            val time = Clock.System.now().toEpochMilliseconds()
            val chapters = mutableListOf<ChapterInfo>()
            val json: String = deps.httpClients.browser.fetch(
                "https://www.panda-novel.com/api/book/chapters/$bookId/1?_=$time",
                detailFetcher.nameSelector,
            ).responseBody.asJsoup().text().replace(" ", "")
            val chapter: ChapterDTO = jacksonObjectMapper().readValue<ChapterDTO>(json)
            chapters.addAll(chapter.parseChapters())

            for (page in 1..chapter.data.pages) {
                val json2: String = deps.httpClients.browser.fetch(
                    "https://www.panda-novel.com/api/book/chapters/$bookId/$page?_=$time",
                    detailFetcher.nameSelector,
                ).responseBody.asJsoup().text().replace(" ", "")
                val chapter2: ChapterDTO = jacksonObjectMapper().readValue<ChapterDTO>(json2)
                chapters.addAll(chapter2.parseChapters())
            }

            return chapters
        }
        return super.getChapterList(manga, commands).reversed()
    }

    fun ChapterDTO.parseChapters(): List<ChapterInfo> {
        return this.data.list.mapIndexed { index, info ->
            ChapterInfo(
                key = baseUrl + info.chapterUrl,
                name = info.name,
                dateUpload = dateFormat.parse(info.updatedAt)?.time ?: -1,
                number = (index + 1).toFloat(),
            )
        }
    }

    override suspend fun getContentRequest(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): Document {

        return deps.httpClients.browser.fetch(
            chapter.key,
            selector = "#novelArticle1",
            timeout = 50000
        ).responseBody.asJsoup()
    }

    override fun pageContentParse(document: Document): List<Page> {
        val par = document.select(contentFetcher.pageContentSelector).html().split("<br>")
            .map { it.asJsoup().text() }
        val head = selectorReturnerStringType(
            document,
            selector = contentFetcher.pageTitleSelector,
            contentFetcher.pageTitleAtt
        ).let {
            contentFetcher.onTitle(it)
        }

        return listOf(head.toPage()) + par.map { it.toPage() }
    }
}
