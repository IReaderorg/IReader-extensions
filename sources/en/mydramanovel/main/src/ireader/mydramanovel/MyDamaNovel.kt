package ireader.mydramanovel

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.SourceFactory.BaseExploreFetcher
import ireader.core.source.SourceFactory.Chapters
import ireader.core.source.SourceFactory.Content
import ireader.core.source.SourceFactory.Detail
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangaInfo.Companion.ON_HIATUS
import tachiyomix.annotations.Extension

/**
 * ☀️ MyDamaNovel - Arabic Novel Source
 */
@Extension
abstract class MyDamaNovel(deps: Dependencies) : SourceFactory(deps = deps) {

    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO
    // ═══════════════════════════════════════════════════════════════
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://mydramanovel.com"
    override val id: Long get() = 42
    override val name: String get() = "MyDramanovel"

    // ═══════════════════════════════════════════════════════════════
    // 🔍 FILTERS & COMMANDS
    // ═══════════════════════════════════════════════════════════════
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort("Sort By:", arrayOf("Latest")),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // ═══════════════════════════════════════════════════════════════
    // 📚 EXPLORE FETCHERS
    // ═══════════════════════════════════════════════════════════════
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novels/",
                selector = ".td-ct-wrap a",
                nameSelector = ".td-ct-item-name",
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = false,
                addBaseurlToCoverLink = false
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/page/{page}/?s={query}",
                selector = ".td-image-container a",
                nameSelector = "a",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "span",
                coverAtt = "data-img-url",
                addBaseUrlToLink = false,
                addBaseurlToCoverLink = false,
                type = SourceFactory.Type.Search
            )
        )

    // ═══════════════════════════════════════════════════════════════
    // 📖 DETAIL FETCHER (with custom Arabic status parsing)
    // ═══════════════════════════════════════════════════════════════
    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = ".tdb-title-text",
            coverSelector = ".td-module-thumb a span",
            coverAtt = "data-img-url",
            descriptionSelector = ".tdb_category_description p",
        )

    // ═══════════════════════════════════════════════════════════════
    // 📚 CHAPTER FETCHER
    // ═══════════════════════════════════════════════════════════════
    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = "h3.entry-title a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    // ═══════════════════════════════════════════════════════════════
    // 📄 CONTENT FETCHER (with watermark removal)
    // ═══════════════════════════════════════════════════════════════
    override val contentFetcher: Content
        get() = Content(
            pageContentSelector = ".td-fix-index p",
        )
}
