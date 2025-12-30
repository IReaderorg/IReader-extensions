package ireader.bakainua

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
abstract class BakaInUA(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "uk"
    override val baseUrl: String get() = "https://baka.in.ua"
    override val id: Long get() = 90L
    override val name: String get() = "BakaInUA"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Жанр",
            arrayOf("Всі жанри", "BL", "GL", "Авторське", "Бойовик", "Вуся", "Гарем", "Детектив", "Драма", "Жахи", "Ісекай", "Історичне", "Комедія", "ЛГБТ", "Містика", "Омегаверс", "Повсякденність", "Пригоди", "Психологія", "Романтика", "Спорт", "Сюаньхвань", "Сянься", "Трагедія", "Трилер", "Фантастика", "Фанфік", "Фентезі", "Школа")
        )
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val genreValues = arrayOf("", "19", "20", "32", "2", "16", "5", "22", "12", "10", "13", "15", "11", "3", "18", "30", "17", "7", "28", "1", "9", "27", "26", "24", "21", "8", "23", "4", "6")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/fictions/alphabetical?page=$page"
        return fetchNovelsFromPage(url)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val sortFilter = filters.findInstance<Filter.Sort>()
        val sortIndex = sortFilter?.value?.index ?: 0
        val genre = genreValues.getOrElse(sortIndex) { "" }

        if (!query.isNullOrBlank()) {
            return searchNovels(query)
        }

        val url = buildString {
            append("$baseUrl/fictions/alphabetical?page=$page")
            if (genre.isNotBlank()) append("&genre=$genre")
        }

        return fetchNovelsFromPage(url)
    }

    private suspend fun fetchNovelsFromPage(url: String): MangasPageInfo {
        val document = client.get(requestBuilder(url)).asJsoup()

        // Get fiction IDs
        val fictionIds = document.select("div#fiction-list-page > div > div > div > img")
            .mapNotNull { it.attr("data-fiction-picker-id-param").takeIf { id -> id.isNotBlank() } }

        // Fetch details for each fiction
        val novels = fictionIds.mapNotNull { id ->
            try {
                val detailResponse = client.get("$baseUrl/fictions/$id/details") {
                    headers {
                        append(HttpHeaders.Accept, "text/vnd.turbo-stream.html")
                    }
                }.bodyAsText()

                val detailDoc = detailResponse.asJsoup()
                val linkElement = detailDoc.selectFirst("a") ?: return@mapNotNull null
                val href = linkElement.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = detailDoc.selectFirst("h3")?.text()?.trim() ?: return@mapNotNull null
                val cover = linkElement.selectFirst("img")?.attr("src")?.let { "$baseUrl$it" } ?: ""

                MangaInfo(
                    key = href.removePrefix("/"),
                    title = name,
                    cover = cover,
                )
            } catch (e: Exception) {
                null
            }
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    private suspend fun searchNovels(searchTerm: String): MangasPageInfo {
        val url = "$baseUrl/search?search%5B%5D=${searchTerm.encodeURLParameter()}&only_fictions=true"
        val document = client.get(requestBuilder(url)).asJsoup()

        val novels = document.select("ul > section").mapNotNull { element ->
            val href = element.selectFirst("a")?.attr("href")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.selectFirst("a > h2")?.text()?.trim() ?: return@mapNotNull null
            val cover = element.selectFirst("img")?.attr("src")?.let { "$baseUrl$it" } ?: ""

            MangaInfo(
                key = href.removePrefix("/"),
                title = name,
                cover = cover,
            )
        }

        return MangasPageInfo(novels, false)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/${manga.key}")).asJsoup()
        }

        val title = document.selectFirst("main div > h1")?.text()?.trim() ?: manga.title
        val author = document.selectFirst("button#fictions-author-search")?.text()?.trim() ?: ""
        val cover = document.selectFirst("main div > img")?.attr("src")?.let { "$baseUrl$it" } ?: manga.cover
        val description = document.selectFirst("main div > h3")?.parent()?.selectFirst("div")?.text()?.trim() ?: ""
        val genres = document.selectFirst("h4:contains(Жанри)")?.parent()?.selectFirst("div")
            ?.select("span")?.map { it.text().trim() } ?: emptyList()

        val statusText = document.selectFirst("div:contains(Статус)")?.nextElementSibling()?.text()?.trim() ?: ""
        val status = when (statusText) {
            "Видаєт." -> MangaInfo.ONGOING
            "Заверш." -> MangaInfo.COMPLETED
            "Покину." -> MangaInfo.ON_HIATUS
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
            client.get(requestBuilder("$baseUrl/${manga.key}")).asJsoup()
        }

        return document.select("li.group a").mapNotNull { element ->
            val href = element.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = element.select("span").getOrNull(1)?.text()?.trim() ?: "Chapter"
            val numberText = element.select("span").getOrNull(0)?.text()?.trim() ?: ""
            val number = numberText.toIntOrNull() ?: 0

            ChapterInfo(
                name = name,
                key = href.removePrefix("/"),
                number = number.toFloat(),
            )
        }.reversed()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/${chapter.key}")).asJsoup()
        }

        val content = document.selectFirst("#user-content")?.html() ?: ""

        return content.split("<br>", "</p>", "\n")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/fictions/alphabetical?page={page}",
                selector = "div#fiction-list-page > div",
                nameSelector = "h3",
                coverSelector = "img",
                coverAtt = "src",
                linkSelector = "a",
                linkAtt = "href",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "main div > h1",
            coverSelector = "main div > img",
            coverAtt = "src",
            authorBookSelector = "button#fictions-author-search",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "li.group a",
            nameSelector = "span:nth-child(2)",
            linkSelector = "a",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#user-content",
        )
}
