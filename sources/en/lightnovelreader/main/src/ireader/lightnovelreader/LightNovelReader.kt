package ireader.lightnovelreader

import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import ireader.core.source.Dependencies
import ireader.core.source.findInstance
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import tachiyomix.annotations.Extension

@Extension
abstract class LightNovelReader(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://lightnovelreader.org"
    override val id: Long
        get() = 21
    override val name: String
        get() = "LightNovelReader"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Ranking",
                endpoint = "/ranking/top-rated/{page}",
                selector = ".category-items.ranking-category.cm-list > ul > li",
                nameSelector = ".category-name a",
                addBaseUrlToLink = true,
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".category-img img",
                coverAtt = "src",
                nextPageSelector = ".cm-pagination",
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/detailed-search-lnr",
                selector = ".category-items.cm-list li",
                addBaseUrlToLink = true,
                nameSelector = ".category-name",
                linkSelector = "'.category-name > a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                nextPageSelector = ".cm-pagination",
                type = SourceFactory.Type.Search
            ),
        )
    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".novel-title",
            coverSelector = ".novels-detail-left img",
            coverAtt = "src",
            descriptionSelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(5) > div p",
            authorBookSelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(2) > div > div.novels-detail-right > ul > li:nth-child(6) > div.novels-detail-right-in-right > a",
            categorySelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(2) > div > div.novels-detail-right > ul > li:nth-child(3) > div.novels-detail-right-in-right > a",
            onStatus = { status ->
                if (status.contains("Completed")) {
                    MangaInfo.COMPLETED
                } else {
                    MangaInfo.ONGOING
                }
            },
            statusSelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(2) > div > div.novels-detail-right > ul > li:nth-child(2) > div.novels-detail-right-in-right"
        )
    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".cm-tabs-content > ul > li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = false,
        )
    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(1) > div > div.section-header-title.me-auto > span",
            pageContentSelector = "#chapterText p",
        )



    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        if (query != null) {
            client.submitForm(
                url = "$baseUrl/detailed-search-lnr",
                formParameters = Parameters.build {
                    append("keyword", query)
                }
            ).body<SearchResponse>().results.let {
                return MangasPageInfo(
                    mangas = it.map { result ->
                        MangaInfo(
                            key = result.link,
                            title = result.original_title,
                            cover = result.image
                        )
                    },
                    hasNextPage = false
                )
            }
        }
        return super.getMangaList(filters, page)
    }
}
