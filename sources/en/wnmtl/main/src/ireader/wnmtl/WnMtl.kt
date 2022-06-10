package ireader.wnmtl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import ireader.wnmtl.chapter.ChapterDTO
import ireader.wnmtl.explore.ExploreDTO
import kotlinx.serialization.json.Json
import org.ireader.core_api.log.Log
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class WnMtl(private val deps: Dependencies) : SourceFactory(
        lang = "en",
        baseUrl = "https://www.wnmtl.org",
        id = 22,
        name = "WnMtl",
        deps = deps,
        filterList = listOf(
                Filter.Title(),
        ),
        commandList = listOf(
                Command.Detail.Fetch(),
                Command.Content.Fetch(),
        ),
        exploreFetchers = listOf(
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
        ),
        detailFetcher = SourceFactory.Detail(),
        contentFetcher = SourceFactory.Content(
                pageTitleSelector = "#chapterContentTitle",
                pageContentSelector = "#chapterContent",
        ),
) {
    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }


    private fun clientBuilder(): HeadersBuilder.() -> Unit = {
//        append("host", "https://www.wnmtl.org")
//        append("referer", "https://www.wnmtl.org/")
//        append("origin", "https://www.wnmtl.org")
        append("site-domain", "www.wnmtl.org")
//        append("accept", "application/json, text/plain, */*")
//        append("user-agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")

    }


    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val books = client.get("https://api.mystorywave.com/story-wave-backend/api/v1/content/books?pageNumber=${page}&pageSize=20") {
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
            val books: ExploreDTO = client.get("https://api.mystorywave.com/story-wave-backend/api/v1/content/books/search?keyWord=${query.replace(" ","+")}&pageNumber=${page}&pageSize=50") {
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
        val books: ExploreDTO = client.get("https://api.mystorywave.com/story-wave-backend/api/v1/content/books?pageNumber=1&pageSize=20") {
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

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val bookId = Regex("[0-9]+").findAll(manga.key)
                .map(MatchResult::value)
                .toList().firstOrNull() ?: throw Exception("failed to find bookId")

        Log.error { "bookId $bookId" }
//        val bookId = Regex.fromLiteral(manga.key).find("/(d*)-")?.groupValues?.firstOrNull()
//                ?: throw Exception("failed to find bookId")
        val chapters: ChapterDTO = client.get("https://api.mystorywave.com/story-wave-backend/api/v1/content/chapters/page?sortDirection=ASC&bookId=${bookId}&pageNumber=1&pageSize=10000") {
           headers( clientBuilder())
        }.body()
        return chapters.data.list.map {
            ChapterInfo(
                    key = "https://www.wnmtl.org/chapter/${it.id}-${it.title.replace(" ", "-")}",
                    name = it.title,
                    dateUpload = it.updateTime,
                    number = it.chapterOrder.toFloat()
            )
        }
    }

    override suspend fun getContentRequest(chapter: ChapterInfo, commands: List<Command<*>>): Document {
        return deps.httpClients.browser.fetch(chapter.key,"#chapterContent").responseBody.asJsoup()
        //return client.get(chapter.key, block = {headers(clientBuilder())}).asJsoup()
    }

    override fun pageContentParse(document: Document): List<String> {
        document.select(contentFetcher.pageContentSelector!!).html().split("<br>")
        val par =  document.select(contentFetcher.pageContentSelector!!).html().split("<br>").map { Jsoup.parse(it) .text()}
        val head = selectorReturnerStringType(
                document,
                selector = contentFetcher.pageTitleSelector,
                contentFetcher.pageTitleAtt
        )

        return listOf(head) + par
    }


}