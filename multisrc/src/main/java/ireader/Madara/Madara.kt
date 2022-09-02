package ireader.Madara

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

abstract class Madara(
    private val deps: Dependencies,
    private val sourceId: Long,
    private val key: String,
    private val sourceName: String,
    private val language: String,
    private val paths: Path = Path("novel", "novel", "novel")
) : HttpSource(deps) {

    override val name = sourceName


    override val id: Long
        get() = sourceId
    override val baseUrl = key

    override val lang = language


    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            sorts,
        )
    }

    override fun getCommands(): CommandList {
        return listOf(
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
            Command.Detail.Fetch()
        )
    }


    val sorts = Filter.Sort(
        "Sort By:", arrayOf(
            "Latest",
            "A-Z",
            "Rating",
            "Trending",
            "Most Views",
            "New",
        )
    )

    class Latest() : Listing("Latest")

    override fun getListings(): List<Listing> {
        return listOf(
            Latest(),
        )
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        val sort = filters.findInstance<Filter.Sort>()?.value

        return if (query != null && query.isNotBlank()) {
            getSearch(query, filters, page)
        } else {
            getNovels(page, sort = sort)
        }

    }

    private suspend fun getSearch(query: String, filters: FilterList, page: Int): MangasPageInfo {

        return bookListParse(
            client.get("$baseUrl/?s=$query&post_type=wp-manga&op=&author=&artist=&release=&adult="){
                headersBuilder()
            }.asJsoup(),
            "div.c-tabs-item__content",
            null
        ) { searchFromElement(it) }
    }

    private fun searchFromElement(element: Element): MangaInfo {
        val title = element.select("div.post-title h3.h4 a").text()
        val url = element.select("div.post-title h3.h4 a").attr("href")
        val thumbnailUrl = element.select("img").attr("data-src")
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }


    private fun bookListParse(
        document: Document,
        elementSelector: String,
        nextPageSelector: String?,
        parser: (element: Element) -> MangaInfo
    ): MangasPageInfo {
        val books = document.select(elementSelector).map { element ->
            parser(element)
        }

        val hasNextPage = nextPageSelector?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPageInfo(books, hasNextPage)
    }

    open fun HttpRequestBuilder.headersBuilder(
        block: HeadersBuilder.() -> Unit = {
            append(HttpHeaders.UserAgent, getUserAgent())
            append(HttpHeaders.CacheControl, "max-age=0")
            append("referer", baseUrl)
        }
    ) {
        headers(block)
    }


    override fun getCoverRequest(url: String): Pair<HttpClient, HttpRequestBuilder> {
        return client to HttpRequestBuilder(url).apply {
            url(url)
            headers {
                append(
                    HttpHeaders.UserAgent,
                    getUserAgent()
                )
                append(HttpHeaders.CacheControl, "max-age=0")
                append(HttpHeaders.Referrer, baseUrl)
            }
        }
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return getNovels(page, sort = sorts.value)
    }

    private suspend fun getNovels(page: Int, sort: Filter.Sort.Selection?): MangasPageInfo {
        val req = when (sort?.index) {
            1 -> "$baseUrl/${paths.novels}/page/$page/?m_orderby=alphabet"
            2 -> "$baseUrl/${paths.novels}/page/$page/?m_orderby=raing"
            3 -> "$baseUrl/${paths.novels}/page/$page/?m_orderby=trending"
            4 -> "$baseUrl/${paths.novels}/page/$page/?m_orderby=views"
            else  -> "$baseUrl/${paths.novels}/page/$page/?m_orderby=latest"
        }
        val request = client.get(requestBuilder(req)).asJsoup()

        return novelsParse(request, page)
    }

    private fun novelsParse(document: Document, page: Int): MangasPageInfo {
        print(document)
        val books = document.select(".page-item-detail").map { element ->
            booksFromElement(element)
        }

        val hasNextPage = "div.nav-previous>a".let { selector ->
            document.select(selector).first()
        } != null

        return MangasPageInfo(books, hasNextPage)
    }

    private fun booksFromElement(element: Element): MangaInfo {
        val title = element.select(".post-title").text().trim()
        val url = element.select(".post-title a").attr("href")
        val image = element.select("img")
        val thumbnailUrl = if (image.hasAttr("data-src")) {
            image.attr("data-src")
        } else {
            image.attr("src")
        }
        return MangaInfo(key = url, title = title, cover = thumbnailUrl)
    }


    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let {
            return detailParse(Jsoup.parse(it.html)).copy(key = it.url)
        }

        return detailParse(client.get(detailRequest(manga)).asJsoup())
    }


    private fun detailParse(document: Document): MangaInfo {
        val title = document.select("div.post-title>h1").text()
        var cover = document.select("div.summary_image a img").attr("data-src")
        if (cover.isBlank()) {
            cover = document.select("div.summary_image a img").attr("src")
        }
        val link = baseUrl + document.select("div.cur div.wp a:nth-child(5)").attr("href")
        var authorBookSelector = document.select("div.author-content>a").attr("title")
        if (authorBookSelector.isBlank()) {
            authorBookSelector = document.select("div.author-content>a").text()
        }
        var description =
            document.select("div.description-summary div.summary__content p").eachText()
                .joinToString("\n\n")
        if (description.isBlank()) {
            description = document.select("div.description-summary div.summary__content").text()
        }
        val category = document.select("div.genres-content a").eachText()
        val rating = document.select("div.post-rating span.score").text()
        val status = document.select("div.post-status div.summary-content").text()


        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = authorBookSelector,
            genres = category,
            key = link,
            status = parseStatus(status)
        )
    }

    private fun parseStatus(string: String): Int {
        return when {
            "OnGoing" in string -> MangaInfo.ONGOING
            "مستمرة" in string -> MangaInfo.ONGOING
            "Completed" in string -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }
    }

    fun detailRequest(manga: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(manga.key)
            headers { headersBuilder() }
        }
    }


    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let {
            return chaptersParse(Jsoup.parse(it.html)).reversed()
        }
        val html = client.get(requestBuilder(manga.key)).asJsoup()
        val bookId = html.select(".rating-post-id").attr("value")


        val chapters = chaptersParse(
            client.submitForm(
                url = "${baseUrl}/wp-admin/admin-ajax.php",
                formParameters = Parameters.build {
                    append("action", "manga_get_chapters")
                    append("manga", bookId)
                }) {
                headersBuilder()
            }.asJsoup(),
        )
        return chapters.reversed()
    }

    open fun chaptersParse(document: Document): List<ChapterInfo> {
        return document.select("li.wp-manga-chapter").map { chapterFromElement(it) }
    }

    fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").text()
        val dateUploaded = element.select("i").text()

        return ChapterInfo(name = name, key = link, dateUpload = parseChapterDate(dateUploaded))
    }

    fun getUserAgent() =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36"

    fun requestBuilder(
        url: String,
        block: HeadersBuilder.() -> Unit = {
            append(HttpHeaders.UserAgent, getUserAgent())
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
//            append("sec-fetch-dest", "document")
//            append("sec-fetch-mode", "navigate")
//            append("sec-ch-ua-platform", "\"Android\"")
//            append("sec-fetch-site", "same-origin")
//            append("sec-fetch-user", "?1")
//            append("sec-fetch-user", "?1")
//            append("upgrade-insecure-requests", "1")
//            append("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
//            append("accept-encoding", "*")
        }
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(url)
            headers(block)
        }
    }

    fun parseChapterDate(date: String): Long {
        return if (date.contains("ago")) {
            val value = date.split(' ')[0].toInt()
            when {
                "min" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "hour" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "day" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "week" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "month" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "year" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                }.timeInMillis
                else -> {
                    0L
                }
            }
        } else {
            try {
                dateFormat.parse(date)?.time ?: 0
            } catch (_: Exception) {
                0L
            }
        }
    }

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return getContents(chapter).map { Text(it) }
    }

    private suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get(contentRequest(chapter)).asJsoup())
    }


    private fun pageContentParse(document: Document): List<String> {
        val par = document.select(".text-left p, .text-right p").eachText()
            .map { it.replace("Read latest Chapters at", "") }
        var head = document.select(".text-center").text()
        if (head.isBlank()) {
            head = document.select("#chapter-heading").text()

        }

        return listOf(head) + par
    }

    private fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headersBuilder()
        }
    }


}