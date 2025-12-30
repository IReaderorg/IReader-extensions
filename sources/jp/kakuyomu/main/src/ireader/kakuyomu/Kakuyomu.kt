package ireader.kakuyomu

import io.ktor.client.request.*
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
abstract class Kakuyomu(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "jp"
    override val baseUrl: String get() = "https://kakuyomu.jp"
    override val id: Long get() = 92L
    override val name: String get() = "Kakuyomu"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Genre",
            arrayOf(
                "総合", "異世界ファンタジー", "現代ファンタジー", "SF",
                "恋愛", "ラブコメ", "現代ドラマ", "ホラー",
                "ミステリー", "エッセイ・ノンフィクション", "歴史・時代・伝奇",
                "創作論・評論", "詩・童話・その他"
            )
        ),
        Filter.Select(
            "Period",
            arrayOf("累計", "日間", "週間", "月間", "年間")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    private val genreValues = arrayOf(
        "all", "fantasy", "action", "sf",
        "love_story", "romance", "drama", "horror",
        "mystery", "nonfiction", "history",
        "criticism", "others"
    )

    private val periodValues = arrayOf("entire", "daily", "weekly", "monthly", "yearly")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/rankings/all/entire" + if (page > 1) "?page=$page" else ""
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()

        if (!query.isNullOrBlank()) {
            val searchUrl = "$baseUrl/search?q=${query.encodeURLParameter()}" + if (page > 1) "&page=$page" else ""
            val document = client.get(requestBuilder(searchUrl)).asJsoup()
            return parseSearchResults(document)
        }

        val sortFilter = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val selectFilters = filters.filterIsInstance<Filter.Select>()
        val periodIndex = selectFilters.firstOrNull()?.value ?: 0

        val genre = genreValues.getOrElse(sortFilter) { "all" }
        val period = periodValues.getOrElse(periodIndex) { "entire" }

        val url = "$baseUrl/rankings/$genre/$period" + if (page > 1) "?page=$page" else ""
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseNovelList(document)
    }

    private fun parseNovelList(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        val novels = document.select(".widget-media-genresWorkList-right > .widget-work").mapNotNull { element ->
            val anchor = element.selectFirst("a.widget-workCard-titleLabel")
            val path = anchor?.attr("href")
            val name = anchor?.text()?.trim()

            if (path != null && !name.isNullOrBlank()) {
                MangaInfo(
                    key = path,
                    title = name,
                    cover = "",
                )
            } else null
        }

        return MangasPageInfo(novels, novels.isNotEmpty())
    }

    private fun parseSearchResults(document: com.fleeksoft.ksoup.nodes.Document): MangasPageInfo {
        // Parse __NEXT_DATA__ JSON
        val nextDataScript = document.selectFirst("script#__NEXT_DATA__[type='application/json']")?.html()
            ?: return MangasPageInfo(emptyList(), false)

        val nextData = json.parseToJsonElement(nextDataScript).jsonObject
        val apolloState = nextData["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("__APOLLO_STATE__")?.jsonObject
            ?: return MangasPageInfo(emptyList(), false)

        val novels = apolloState.entries
            .filter { (_, value) ->
                value.jsonObject["__typename"]?.jsonPrimitive?.contentOrNull == "Work"
            }
            .mapNotNull { (_, value) ->
                val work = value.jsonObject
                val id = work["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val title = work["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val cover = work["adminCoverImageUrl"]?.jsonPrimitive?.contentOrNull ?: ""

                MangaInfo(
                    key = "/works/$id",
                    title = title,
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

        // Parse __NEXT_DATA__ JSON
        val nextDataScript = document.selectFirst("script#__NEXT_DATA__[type='application/json']")?.html()
            ?: return manga

        val nextData = json.parseToJsonElement(nextDataScript).jsonObject
        val apolloState = nextData["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("__APOLLO_STATE__")?.jsonObject
            ?: return manga

        val workId = manga.key.replace("/works/", "")
        
        // Find the Work object
        val work = apolloState.entries
            .firstOrNull { (_, value) ->
                value.jsonObject["__typename"]?.jsonPrimitive?.contentOrNull == "Work" &&
                value.jsonObject["id"]?.jsonPrimitive?.contentOrNull == workId
            }?.value?.jsonObject ?: return manga

        val title = work["title"]?.jsonPrimitive?.contentOrNull ?: manga.title
        val cover = work["adminCoverImageUrl"]?.jsonPrimitive?.contentOrNull ?: manga.cover
        val introduction = work["introduction"]?.jsonPrimitive?.contentOrNull ?: ""
        val tagLabels = work["tagLabels"]?.jsonArray?.mapNotNull { 
            it.jsonPrimitive.contentOrNull 
        } ?: emptyList()

        val serialStatus = work["serialStatus"]?.jsonPrimitive?.contentOrNull
        val status = if (serialStatus == "COMPLETED") MangaInfo.COMPLETED else MangaInfo.ONGOING

        // Find author
        val authorRef = work["author"]?.jsonObject?.get("__ref")?.jsonPrimitive?.contentOrNull
        val author = if (authorRef != null) {
            apolloState[authorRef]?.jsonObject?.get("activityName")?.jsonPrimitive?.contentOrNull ?: ""
        } else ""

        return manga.copy(
            title = title,
            cover = cover,
            description = introduction,
            author = author,
            genres = tagLabels,
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

        // Parse __NEXT_DATA__ JSON
        val nextDataScript = document.selectFirst("script#__NEXT_DATA__[type='application/json']")?.html()
            ?: return emptyList()

        val nextData = json.parseToJsonElement(nextDataScript).jsonObject
        val apolloState = nextData["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("__APOLLO_STATE__")?.jsonObject
            ?: return emptyList()

        // Find all episodes
        val episodes = apolloState.entries
            .filter { (_, value) ->
                value.jsonObject["__typename"]?.jsonPrimitive?.contentOrNull == "Episode"
            }
            .map { (_, value) -> value.jsonObject }

        // Find all chapters
        val chapters = apolloState.entries
            .filter { (_, value) ->
                value.jsonObject["__typename"]?.jsonPrimitive?.contentOrNull == "Chapter"
            }
            .associate { (key, value) -> 
                key to value.jsonObject["title"]?.jsonPrimitive?.contentOrNull 
            }

        // Find TableOfContentsChapter to map episodes to chapters
        val tocChapters = apolloState.entries
            .filter { (_, value) ->
                value.jsonObject["__typename"]?.jsonPrimitive?.contentOrNull == "TableOfContentsChapter"
            }
            .map { (_, value) -> value.jsonObject }

        // Build chapter list
        return episodes.mapNotNull { episode ->
            val episodeId = episode["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val episodeTitle = episode["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val publishedAt = episode["publishedAt"]?.jsonPrimitive?.contentOrNull ?: ""

            // Find chapter title for this episode
            val chapterTitle = tocChapters.firstOrNull { toc ->
                toc["episodeUnions"]?.jsonArray?.any { union ->
                    union.jsonObject["__ref"]?.jsonPrimitive?.contentOrNull == "Episode:$episodeId"
                } == true
            }?.let { toc ->
                val chapterRef = toc["chapter"]?.jsonObject?.get("__ref")?.jsonPrimitive?.contentOrNull
                chapters[chapterRef]
            }

            val name = if (!chapterTitle.isNullOrBlank()) {
                "$chapterTitle - $episodeTitle"
            } else {
                episodeTitle
            }

            ChapterInfo(
                name = name,
                key = "${manga.key}/episodes/$episodeId",
            )
        }.mapIndexed { index, chapter ->
            chapter.copy(number = (index + 1).toFloat())
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val contentFetch = commands.findInstance<Command.Content.Fetch>()
        val document = if (contentFetch != null && contentFetch.html.isNotBlank()) {
            contentFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl${chapter.key}")).asJsoup()
        }

        val chapterTitle = document.selectFirst(".chapterTitle")?.text() ?: ""
        val episodeTitle = document.selectFirst(".widget-episodeTitle")?.html() ?: ""
        val episodeBody = document.selectFirst(".widget-episodeBody")?.html() ?: ""

        val fullContent = buildString {
            if (chapterTitle.isNotBlank()) append("<h1>$chapterTitle</h1>\n")
            append("<h2>$episodeTitle</h2>\n")
            append(episodeBody)
        }

        return fullContent.split("\n", "</p>", "<p>", "<br>", "<br/>", "<br />")
            .map { it.replace(Regex("<[^>]+>"), "").trim() }
            .filter { it.isNotBlank() }
            .map { Text(it) }
    }

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/rankings/all/entire",
                selector = ".widget-media-genresWorkList-right > .widget-work",
                nameSelector = "a.widget-workCard-titleLabel",
                linkSelector = "a.widget-workCard-titleLabel",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "h1",
            coverSelector = "img",
            coverAtt = "src",
            descriptionSelector = "p",
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
            pageContentSelector = ".widget-episodeBody p",
        )
}
