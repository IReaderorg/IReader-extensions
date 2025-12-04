package ireader.realmnovel

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
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
import ireader.core.source.model.Text
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
class RealmNovel(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://www.realmnovel.com"
    override val id: Long get() = 6766631659005856663L
    override val name: String get() = "RealmNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort", arrayOf("Latest", "Popular"))
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novel-list?sort=latest&page={{page}}",
                selector = ".book-item",
                nameSelector = ".novel-title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".cover img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            ),
             BaseExploreFetcher(
                "Popular",
                endpoint = "/novel-list?sort=popular&page={{page}}",
                selector = ".book-item",
                nameSelector = ".novel-title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".cover img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            ),
             BaseExploreFetcher(
                "Search",
                endpoint = "/search?keyword={{query}}&page={{page}}",
                selector = ".book-item",
                nameSelector = ".novel-title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".cover img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".novel-title",
            coverSelector = ".cover img",
            coverAtt = "src",
            descriptionSelector = ".desc",
            authorBookSelector = ".author",
            statusSelector = null, // No status selector provided
            categorySelector = null, // No category selector provided
            addBaseurlToCoverLink = true
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = ".chapter-title",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = ".chapter-content",
        )

    override fun pageContentParse(document: Document): List<Page> {
        val content = document.select(".chapter-content").first() ?: return emptyList()
        content.select("script, style, .ads").remove()
        return content.select("p").mapNotNull { element ->
            val text = element.text().trim()
            if (text.isNotEmpty()) Text(text) else null
        }.ifEmpty {
            listOf(Text(content.text()))
        }
    }
}