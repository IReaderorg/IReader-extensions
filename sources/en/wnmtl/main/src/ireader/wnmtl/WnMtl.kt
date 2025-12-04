package ireader.wnmtl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import ireader.core.source.SourceFactory
import ireader.wnmtl.chapter.ChapterDTO
import ireader.wnmtl.content.ContentDTO
import ireader.wnmtl.explore.ExploreDTO
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class WnMtl(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val id: Long
        get() = 22
    override val name: String
        get() = "WnMtl"
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.wnmtl.org"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),

    )

    override fun getUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36"
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
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
            nameSelector = ".book-name span",
            authorBookSelector = ".author-name",
            categorySelector = ".gnere-name",
            descriptionSelector = "#about-panel .content",
            coverSelector = ".cover-container .cover img",
            coverAtt = "src"
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapters ul li a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            onLink = { link ->
                baseUrl + link
            }
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "#chapterContentTitle",
            pageContentSelector = "#chapterContent",
        )

    override suspend fun getMangaDetailsRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return deps.httpClients.browser.fetch(
            manga.key,
            selector = detailFetcher.nameSelector
        ).responseBody.asJsoup()
    }

    private fun clientBuilder(): HeadersBuilder.() -> Unit = {
//        append("host", "https://www.wnmtl.org")
//        append("referer", "https://www.wnmtl.org/")
//        append("origin", "https://www.wnmtl.org")
        append("site-domain", "www.wnmtl.org")
//        append("accept", "application/json, text/plain, */*")
//        append("user-agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
    }

    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return client to HttpRequestBuilder().apply {
            url(url)
            headersBuilder()
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val books =
            client.get("https://api.mystorywave.com/story-wave-backend/api/v1/content/books?pageNumber=$page&pageSize=20") {
                headers(clientBuilder())
            }.body<ExploreDTO>()

        return MangasPageInfo(
            mangas = books.data.list.map { data ->
                MangaInfo(
                    key = "https://www.wnmtl.org/book/${data.id}-${data.title.replace(" ", "-")}",
                    title = data.title,
                    author = data.authorPseudonym,
                    description = data.synopsis,
                    genres = listOf(data.genreName),
                    status = when (data.status) {
                        0 -> MangaInfo.ONGOING
                        else -> MangaInfo.COMPLETED
                    },
                    cover = data.coverImgUrl
                )
            },
            hasNextPage = books.data.list.isNotEmpty()
        )
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value

        if (query != null) {
            val books: ExploreDTO = client.get(
                "https://api.mystorywave.com/story-wave-backend/api/v1/content/books/search?keyWord=${
                query.replace(
                    " ",
                    "+"
                )
                }&pageNumber=$page&pageSize=50"
            ) {
                headers(clientBuilder())
            }.body()
            return MangasPageInfo(
                mangas = books.data.list.map { data ->
                    MangaInfo(
                        key = "https://www.wnmtl.org/book/${data.id}-${
                        data.title.replace(
                            " ",
                            "-"
                        )
                        }",
                        title = data.title,
                        author = data.authorPseudonym,
                        description = data.synopsis,
                        genres = listOf(data.genreName),
                        status = when (data.status) {
                            0 -> MangaInfo.ONGOING
                            else -> MangaInfo.COMPLETED
                        },
                        cover = data.coverImgUrl
                    )
                },
                hasNextPage = books.data.list.isNotEmpty()
            )
        }
        val books: ExploreDTO =
            client.get("https://api.mystorywave.com/story-wave-backend/api/v1/content/books?pageNumber=1&pageSize=20") {
                headers(clientBuilder())
            }.body()

        return MangasPageInfo(
            mangas = books.data.list.map { data ->
                MangaInfo(
                    key = "https://www.wnmtl.org/book/${data.id}-${data.title.replace(" ", "-")}",
                    title = data.title,
                    author = data.authorPseudonym,
                    description = data.synopsis,
                    genres = listOf(data.genreName),
                    status = when (data.status) {
                        0 -> MangaInfo.ONGOING
                        else -> MangaInfo.COMPLETED
                    },
                    cover = data.coverImgUrl
                )
            },
            hasNextPage = books.data.list.isNotEmpty()
        )
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if (commands.isEmpty()) {
            val bookId = manga.key.substringAfter("/book/").substringBefore("-")
            var page = 1
            var maxPage = 2
            val chapters = mutableListOf<ChapterInfo>()
            while (page < maxPage) {
                client.get("https://api.mystorywave.com/story-wave-backend/api/v1/content/chapters/page?sortDirection=ASC&bookId=$bookId&pageNumber=$page&pageSize=100") {
                    headers(clientBuilder())
                }.body<ChapterDTO>().also {
                    maxPage = it.data.totalPages
                    page++
                }.data.list.map {
                    ChapterInfo(
                        key = "https://www.wnmtl.org/chapter/${it.id}-${
                        it.title.replace(
                            " ",
                            "-"
                        )
                        }",
                        name = it.title,
                        dateUpload = it.updateTime,
                        number = it.chapterOrder.toFloat()
                    )
                }.let {
                    chapters.addAll(it)
                }
                delay(200)
            }

            return chapters
        }
        return super.getChapterList(manga, commands)
    }

    override suspend fun getContents(
        chapter: ChapterInfo,
        commands: List<Command<*>>
    ): List<Page> {
        val chapterId = chapter.key.substringAfter("/chapter/").substringBefore("-")
        val data: ContentDTO = client.get(
            "https://api.mystorywave.com/story-wave-backend/api/v1/content/chapters/$chapterId",
            block = { headers(clientBuilder()) }
        ).body()
        return listOf(data.data.title.toPage()) + data.data.content.split("\\n\\n").map { it.toPage() }
    }
}
