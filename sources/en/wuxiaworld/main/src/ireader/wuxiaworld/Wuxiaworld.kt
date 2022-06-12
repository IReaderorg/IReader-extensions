package ireader.wuxiaworld

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.serialization.kotlinx.json.*
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import tachiyomix.annotations.Extension


@Extension
abstract class Wuxiaworld(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.wuxiaworld.com"
    override val id: Long
        get() = 26
    override val name: String
        get() = "Wuxiaworld"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )
    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
//        engine {
//            preconfigured = clientBuilder()
//        }
        BrowserUserAgent()
    }

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1.MuiTypography-root",
            coverSelector = ".MuiGrid-container .MuiGrid-root img",
            coverAtt = "src",
            descriptionSelector = ".fr-view.prose p:nth-child(1)",
            authorBookSelector = "div.mx-auto.text-center.my-0:nth-child(2)",
            categorySelector = ".flex-wrap.justify-center a",
            statusSelector = "#full-width-tabpanel-0  div  p:nth-child(2)",
            onStatus = { status ->
                if (status.contains("Complete")) {
                    MangaInfo.COMPLETED
                } else {
                    MangaInfo.ONGOING
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.MuiCollapse-root.MuiCollapse-vertical.MuiCollapse-entered.ww-c4sutr > div > div > div > div > div > div > a",
            nameSelector = "span span",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
            addBaseUrlToLink = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = " h4.MuiTypography-root.MuiTypography-h4 > span:nth-child(1)",
            pageContentSelector = ".max-w-none p",
        )


    suspend fun getPopular() : MangasPageInfo {
        val books: PopularDTO = client.get(requestBuilder("$baseUrl/api/novels")).body()

        return parseBooks(books)
    }

    fun parseBooks(books: PopularDTO) : MangasPageInfo {
        return MangasPageInfo(books.items.map { book ->
            MangaInfo(
                key = "https://www.wuxiaworld.com/novel/${book.slug}",
                title = book.name,
                author = book.authorName?:"",
                description = book.synopsis.let { Jsoup.parse(it?:"").text() },
                status = if (book.description.contains(
                        "Complete",
                        true
                    )
                ) MangaInfo.COMPLETED else MangaInfo.ONGOING,
                cover = book.coverUrl,
                genres = book.genres
            )
        }, hasNextPage = false)
    }

    suspend fun getSearch(searchQuery:String) : MangasPageInfo {
        val books: PopularDTO = client.get(requestBuilder("https://www.wuxiaworld.com/api/novels/search?query=$searchQuery")).body()
        return parseBooks(books)
    }



    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getPopular()
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value
        if (query != null) {
            return getSearch(query)
        }

        return getPopular()
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