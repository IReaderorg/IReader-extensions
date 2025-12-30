package ireader.agitoon

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension

@Extension
abstract class Agitoon(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "kr"
    override val baseUrl: String get() = "https://agit664.xyz"
    override val id: Long get() = 91L
    override val name: String get() = "Agitoon"

    private var resolvedUrl: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private suspend fun checkUrl(): String {
        if (resolvedUrl == null) {
            try {
                val response = client.get(requestBuilder(baseUrl))
                resolvedUrl = response.request.url.toString().trimEnd('/')
            } catch (e: Exception) {
                resolvedUrl = baseUrl
            }
        }
        return resolvedUrl!!
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return fetchNovels(page, false, "")
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim() ?: ""
        
        if (query.isNotBlank()) {
            return searchNovels(query)
        }
        
        return fetchNovels(page, false, "")
    }

    private suspend fun fetchNovels(page: Int, showLatest: Boolean, searchTerm: String): MangasPageInfo {
        val url = checkUrl()
        
        // Get current day of week (0 = Sunday, 6 = Saturday)
        val dayOfWeek = ((System.currentTimeMillis() / 86400000L + 4) % 7).toInt()
        
        val response = client.submitForm(
            url = "$url/novel/index.update.php",
            formParameters = Parameters.build {
                append("mode", "get_data_novel_list_p")
                append("novel_menu", if (showLatest) "1" else "3")
                append("np_day", dayOfWeek.toString())
                append("np_rank", "1")
                append("np_distributor", "0")
                append("np_genre", "00")
                append("np_order", "1")
                append("np_genre_ex_1", "00")
                append("np_genre_ex_2", "00")
                append("list_limit", (20 * (page - 1)).toString())
                append("is_query_first", if (page == 1) "true" else "false")
            }
        ) {
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }.bodyAsText()

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val list = jsonObj["list"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)

        val novels = list.mapNotNull { element ->
            val novel = element.jsonObject
            val wrId = novel["wr_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val wrSubject = novel["wr_subject"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val npDir = novel["np_dir"]?.jsonPrimitive?.contentOrNull ?: ""
            val npThumbnail = novel["np_thumbnail"]?.jsonPrimitive?.contentOrNull ?: ""

            val cover = if (npDir.isNotBlank() && npThumbnail.isNotBlank()) {
                "$url$npDir/thumbnail/$npThumbnail"
            } else ""

            MangaInfo(
                key = wrId,
                title = wrSubject,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    private suspend fun searchNovels(searchTerm: String): MangasPageInfo {
        val url = checkUrl()
        
        val response = client.submitForm(
            url = "$url/novel/search.php",
            formParameters = Parameters.build {
                append("mode", "get_data_novel_list_p_sch")
                append("search_novel", searchTerm)
                append("list_limit", "0")
            }
        ) {
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }.bodyAsText()

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val list = jsonObj["list"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)

        val novels = list.mapNotNull { element ->
            val novel = element.jsonObject
            val wrId = novel["wr_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val wrSubject = novel["wr_subject"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val npDir = novel["np_dir"]?.jsonPrimitive?.contentOrNull ?: ""
            val npThumbnail = novel["np_thumbnail"]?.jsonPrimitive?.contentOrNull ?: ""

            val cover = if (npDir.isNotBlank() && npThumbnail.isNotBlank()) {
                "$url/$npDir/thumbnail/$npThumbnail"
            } else ""

            MangaInfo(
                key = wrId,
                title = wrSubject,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, false)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val url = checkUrl()
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$url/novel/list/${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("h5.pt-2")?.text()?.trim() ?: manga.title
        val cover = document.selectFirst("div.col-5.pr-0.pl-0 img")?.attr("src") ?: manga.cover
        val description = document.selectFirst(".pt-1.mt-1.pb-1.mb-1")?.text()?.trim() ?: ""
        
        val author = document.selectFirst(".post-item-list-cate-v")?.text()
            ?.split(" : ")?.getOrNull(1)?.trim() ?: ""
        
        val genres = document.select(".col-7 > .post-item-list-cate > span")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        return manga.copy(
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = genres,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val url = checkUrl()
        
        val response = client.submitForm(
            url = "$url/novel/list.update.php",
            formParameters = Parameters.build {
                append("mode", "get_data_novel_list_c")
                append("wr_id_p", manga.key)
                append("page_no", "1")
                append("cnt_list", "10000")
                append("order_type", "Asc")
            }
        ) {
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }.bodyAsText()

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val list = jsonObj["list"]?.jsonArray ?: return emptyList()

        return list.mapIndexedNotNull { index, element ->
            val chapter = element.jsonObject
            val wrId = chapter["wr_id"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val wrSubject = chapter["wr_subject"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val wrDatetime = chapter["wr_datetime"]?.jsonPrimitive?.contentOrNull ?: ""

            ChapterInfo(
                name = wrSubject,
                key = "$wrId/2",
                number = (index + 1).toFloat(),
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val url = checkUrl()
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$url/novel/view/${chapter.key}")).asJsoup()
        }

        var content = document.selectFirst("#id_wr_content")?.html() ?: ""
        
        // Remove popup text
        content = content.replace("팝업메뉴는 빈공간을 더치하거나 스크룰시 사라집니다", "").trim()

        return content.split("\n", "</p>", "<p>", "<br>", "<br/>", "<br />")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/",
                selector = "div",
                nameSelector = "h1",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h5.pt-2",
            coverSelector = "div.col-5.pr-0.pl-0 img",
            coverAtt = "src",
            descriptionSelector = ".pt-1.mt-1.pb-1.mb-1",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#id_wr_content",
        )
}
