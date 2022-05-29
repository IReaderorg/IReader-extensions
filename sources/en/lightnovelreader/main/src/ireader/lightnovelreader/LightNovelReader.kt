package ireader.lightnovelreader

import android.util.Log
import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.ireader.core_api.http.okhttp
import org.ireader.core_api.source.*
import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension
import java.text.SimpleDateFormat
import java.util.*


@Extension
abstract class LightNovelReader(deps: Dependencies) : SourceFactory(
        lang = "en",
        baseUrl = "https://lightnovelreader.org",
        id = 20,
        name = "LightNovelReader",
        deps = deps,
        filterList = listOf(
                Filter.Title(),
        ),
        commandList = listOf(
                Command.Detail.Fetch(),
                Command.Content.Fetch(),
                Command.Chapter.Fetch(),
        ),
        exploreFetchers = listOf(
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
                ),
        detailFetcher = SourceFactory.Detail(
                nameSelector = ".novel-title",
                coverSelector = ".novels-detail-left img",
                coverAtt = "src",
                descriptionSelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(5) > div p",
                authorBookSelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(2) > div > div.novels-detail-right > ul > li:nth-child(6) > div.novels-detail-right-in-right > a",
                categorySelector ="body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(2) > div > div.novels-detail-right > ul > li:nth-child(3) > div.novels-detail-right-in-right > a",
                status = mapOf(
                        Pair("Ongoing",MangaInfo.ONGOING),
                        Pair("Completed",MangaInfo.COMPLETED),
                ),
                statusSelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(2) > div > div.novels-detail-right > ul > li:nth-child(2) > div.novels-detail-right-in-right"
        ),
        chapterFetcher = SourceFactory.Chapters(
                selector = ".cm-tabs-content > ul > li",
                nameSelector = "a",
                linkSelector = "a",
                linkAtt = "href",
                reverseChapterList = false,
        ),
        contentFetcher = SourceFactory.Content(
                pageTitleSelector = "body > section:nth-child(4) > div > div > div.col-12.col-xl-9 > div > div:nth-child(1) > div > div.section-header-title.me-auto > span",
                pageContentSelector = "#chapterText p",
        ),
) {

    override val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        if (query != null) {
           client.submitForm(url = "$baseUrl/detailed-search-lnr",
                    formParameters = Parameters.build {
                        append("keyword",query)
                    }
            ).body<SearchResponse>().results.let {
                return MangasPageInfo(
                        mangas = it.map { result ->
                            MangaInfo(
                                    key= result.link,
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