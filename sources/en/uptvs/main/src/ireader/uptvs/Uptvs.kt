package ireader.uptvs

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class Uptvs(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "fa"
    override val baseUrl: String
        get() = "https://www.uptvs.com"
    override val id: Long
        get() = 47
    override val name: String
        get() = "Uptv"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sorts:",
            options = arrayOf(
                "Movies",
                "Animations",
            )
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    val baseFormat = BaseExploreFetcher(
        "Movies",
        endpoint = "/category/movie/page/{page}",
        selector = ".post-layout",
        nameSelector = "a",
        nameAtt = "title",
        linkSelector = "a",
        linkAtt = "href",
        coverSelector = "img",
        coverAtt = "src",
        infinitePage = true
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            baseFormat, baseFormat.copy(endpoint = "/category/animations", key = "Animations"),
            BaseExploreFetcher(
                "Movies",
                endpoint = "/wp-admin/admin-ajax.php",
                selector = ".align-items-md-center",
                nameSelector = "h1 a",
                nameAtt = "title",
                linkSelector = "h1 a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                infinitePage = false,
                maxPage = 1,
                type = Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".container-sm a",
            nameAtt = "title",
            coverSelector = ".container-sm a img",
            coverAtt = "src",
            descriptionSelector = ".show-read-more",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".post-content-download-box a",
            nameSelector = "a span:nth-child(1)",
            onName = {
                     it.replace("دانلود","")
            },
            linkSelector = "a",
            linkAtt = "href",
        )


    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return super.getChapterList(manga, commands).map { it.copy(type = ChapterInfo.MOVIE) }
    }

    override suspend fun getContents(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return listOf(MovieUrl(chapter.key))
    }


    override suspend fun getListRequest(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String
    ): Document {
        if (baseExploreFetcher.type == Type.Search) {
            return client.submitForm(
                url = "https://www.uptvs.com/wp-admin/admin-ajax.php",
                formParameters = Parameters.build {
                    append("action", "data_fetch")
                    append("search", query)
                }
            ).asJsoup()
        }
        return super.getListRequest(baseExploreFetcher, page, query)
    }

}
