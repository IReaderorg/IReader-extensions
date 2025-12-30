package ireader.novelmania

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import tachiyomix.annotations.Extension

@Extension
abstract class NovelMania(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "pt"
    override val baseUrl: String get() = "https://novelmania.com.br"
    override val id: Long get() = 89L
    override val name: String get() = "Novel Mania"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Ordenar",
            arrayOf("Qualquer ordem", "Ordem alfabética", "Nº de Capítulos", "Popularidade", "Novidades")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val orderValues = arrayOf("", "1", "2", "3", "4")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/novels?titulo=&categoria=&status=&nacionalidade=&ordem=&page%5Bpage%5D=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim() ?: ""
        val sortFilter = filters.findInstance<Filter.Sort>()
        val sortIndex = sortFilter?.value?.index ?: 0
        val order = orderValues.getOrElse(sortIndex) { "" }

        val url = "$baseUrl/novels?titulo=${query.encodeURLParameter()}&categoria=&status=&nacionalidade=&ordem=$order&page%5Bpage%5D=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select("div.top-novels.dark.col-6 > div.row.mb-2").mapNotNull { element ->
            val name = element.selectFirst("a.novel-title > h5")?.text()?.trim() ?: return@mapNotNull null
            val cover = element.selectFirst("a > div.card.c-size-1.border > img.card-image")?.attr("src") ?: ""
            val href = element.selectFirst("a.novel-title")?.attr("href") ?: return@mapNotNull null

            if (name.isBlank() || href.isBlank()) return@mapNotNull null

            MangaInfo(
                key = href,
                title = name,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        document.select("b").remove()

        val title = document.selectFirst("div.col-md-8 > div.novel-info > div.d-flex.flex-row.align-items-center > h1")
            ?.text()?.trim() ?: manga.title
        val description = document.select("div.tab-pane.fade.show.active > div.text > p")
            .joinToString("\n\n") { it.text() }
        val cover = document.selectFirst("div.novel-img > img.img-responsive")?.attr("src") ?: manga.cover
        val author = document.selectFirst("div.novel-info > span.authors.mb-1")?.text()?.trim() ?: ""
        val genres = document.select("div.tags > ul.list-tags.mb-0 > li > a")
            .map { it.text().trim() }

        val statusText = document.selectFirst("div.novel-info > span.authors.mb-3")?.text()?.trim() ?: ""
        val status = when (statusText) {
            "Ativo" -> MangaInfo.ONGOING
            "Pausado" -> MangaInfo.ON_HIATUS
            "Completo" -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }

        return manga.copy(
            title = title,
            cover = cover,
            author = author,
            description = description,
            genres = genres,
            status = status,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${manga.key}")).asJsoup()
        }

        return document.select("div.accordion.capitulo > div.card > div.collapse > div.card-body.p-0 > ol > li").mapNotNull { element ->
            val linkElement = element.selectFirst("a") ?: return@mapNotNull null
            val href = linkElement.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val subVol = element.selectFirst("a > span.sub-vol")?.text()?.trim() ?: ""
            val chapterTitle = element.selectFirst("a > strong")?.text()?.trim() ?: ""
            val name = "$subVol - $chapterTitle".trim().removePrefix("- ").removeSuffix(" -")

            ChapterInfo(
                name = name,
                key = href,
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${chapter.key}")).asJsoup()
        }

        val content = document.selectFirst("div#chapter-content")?.html() ?: ""

        return content.split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/novels?titulo=&categoria=&status=&nacionalidade=&ordem=&page%5Bpage%5D={page}",
                selector = "div.top-novels.dark.col-6 > div.row.mb-2",
                nameSelector = "a.novel-title > h5",
                coverSelector = "img.card-image",
                coverAtt = "src",
                linkSelector = "a.novel-title",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "div.novel-info h1",
            coverSelector = "div.novel-img > img.img-responsive",
            coverAtt = "src",
            descriptionSelector = "div.tab-pane.fade.show.active > div.text > p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "div.accordion.capitulo ol > li",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "div#chapter-content",
        )
}
