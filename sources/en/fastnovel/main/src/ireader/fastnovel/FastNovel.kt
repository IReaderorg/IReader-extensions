package ireader.fastnovel

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class FastNovel(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://fastnovel.org"
    override val id: Long
        get() = 40
    override val name: String
        get() = "FastNovel"

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
                endpoint = "/sort/p?page={page}",
                selector = ".col-novel-main > .list-novel .row",
                nameSelector = ".novel-title",
                coverSelector = ".cover",
                coverAtt = "src",
                linkSelector = ".novel-title > a",
                linkAtt = "href",
                maxPage = 39,
                addBaseUrlToLink = false,
                onCover = { url, _ -> url.replace(Regex("/novel[\\s\\S]*/"), "/novel/") }
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?keyword={query}",
                selector = ".col-novel-main > .list-novel .row",
                nameSelector = ".novel-title",
                coverSelector = ".cover",
                coverAtt = "src",
                linkSelector = ".novel-title > a",
                linkAtt = "href",
                addBaseUrlToLink = false,
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".info-holder .title",
            coverSelector = ".book > img",
            coverAtt = "src",
            // We are using nth-last-child(x) because there are some
            // novels which can have alternative names that is added
            // on top of this .info list
            authorBookSelector = ".info > li:nth-last-child(4) a",
            categorySelector = ".info > li:nth-last-child(3) a",
            descriptionSelector = ".desc-text",
            statusSelector = ".info > li:nth-last-child(1) a",
            onStatus = { str ->
                when (str) {
                    "Completed" -> MangaInfo.COMPLETED
                    "Ongoing" -> MangaInfo.ONGOING
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".list-chapter > li",
            reverseChapterList = true,
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = false,
            numberSelector = "a",
            numberAtt = "title",
            onNumber = { str -> str.substringAfter("Chapter ").substringBefore(" ") }
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chr-title > .chr-text",
            pageContentSelector = "#chr-content > p",
        )

    override suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        val id = manga.key.substringAfterLast("/")
        val updatedManga = MangaInfo(
            key = "https://fastnovel.org/ajax/chapter-archive?novelId=$id",
            title = manga.title,
        )
        return super.getChapterListRequest(updatedManga, commands)
    }
}
