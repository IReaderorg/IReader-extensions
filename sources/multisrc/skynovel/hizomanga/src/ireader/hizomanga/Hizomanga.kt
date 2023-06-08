package ireader.hizomanga

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import ireader.core.source.Dependencies
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.model.ImageUrl
import ireader.core.source.model.Page
import ireader.skynovelmodel.SkyNovelModel
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class Hizomanga(deps: Dependencies) : SkyNovelModel(deps,) {

    override val id: Long
    get() = 53

    override val name: String
    get() = "Hizomanga"
    override val lang: String
    get() = "ar"

    override val baseUrl: String
    get() = "https://hizomanga.com"

    override val mainEndpoint: String
    get() = "book"

    override val descriptionSelector: String
    get() = "summary__content p"

    override val contentSelector: String
    get() = ".reading-content p"

    override fun pageContentParse(document: Document): List<Page> {
        val par = selectorReturnerListType(
            document,
            selector = contentFetcher.pageContentSelector,
            contentFetcher.pageContentAtt
        ).let {
            contentFetcher.onContent(it)
        }
        val images =selectorReturnerListType(
            document,
            selector = ".reading-content img",
            contentFetcher.pageContentAtt
        ).let {
            contentFetcher.onContent(it)
        }
        val head = selectorReturnerStringType(
            document,
            selector = contentFetcher.pageTitleSelector,
            contentFetcher.pageTitleAtt
        ).let {
            contentFetcher.onTitle(it)
        }

        return listOf(head.toPage()) + par.map { it.toPage() } + images.map { ImageUrl(it) }
    }
}