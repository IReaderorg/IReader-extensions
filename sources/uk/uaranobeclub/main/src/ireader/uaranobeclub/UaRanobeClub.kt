package ireader.uaranobeclub

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension

@Extension
abstract class UaRanobeClub(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "uk"
    override val baseUrl: String get() = "https://uaranobe.club"
    override val id: Long get() = 88L
    override val name: String get() = "UA Ranobe Club"

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

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return fetchNovels(page, "")
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim() ?: ""
        return fetchNovels(page, query)
    }

    private suspend fun graphqlRequest(graphqlQuery: String, variables: JsonObject): String {
        val requestBody = buildJsonObject {
            put("query", graphqlQuery)
            put("variables", variables)
        }

        return client.post("$baseUrl/graphql") {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }.bodyAsText()
    }

    private suspend fun fetchNovels(page: Int, search: String): MangasPageInfo {
        val skip = (page - 1) * 10

        val query = """
            query Writings(${"$"}skip: Int!, ${"$"}search: String) {
              writingsCount(search: ${"$"}search)
              writings(skip: ${"$"}skip, search: ${"$"}search) {
                id
                title
                image
                slug
                __typename
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("skip", skip)
            put("search", search)
        }

        val response = graphqlRequest(query, variables)

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val data = jsonObj["data"]?.jsonObject ?: return MangasPageInfo(emptyList(), false)
        val writings = data["writings"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)

        val novels = writings.mapNotNull { element ->
            val writing = element.jsonObject
            val title = writing["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val slug = writing["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val image = writing["image"]?.jsonPrimitive?.contentOrNull ?: ""

            MangaInfo(
                key = slug,
                title = title,
                cover = image,
            )
        }

        return MangasPageInfo(novels, novels.size >= 10)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val slug = manga.key

        val query = """
            query Writing(${"$"}slug: String!) {
              writingBySlug(slug: ${"$"}slug) {
                id
                title
                originalTitle
                image
                slug
                description
                genres {
                  genreId
                  genre {
                    id
                    name
                    __typename
                  }
                  __typename
                }
                __typename
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("slug", slug)
        }

        val response = graphqlRequest(query, variables)

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val data = jsonObj["data"]?.jsonObject ?: return manga
        val writing = data["writingBySlug"]?.jsonObject ?: return manga

        val title = writing["title"]?.jsonPrimitive?.contentOrNull ?: manga.title
        val image = writing["image"]?.jsonPrimitive?.contentOrNull ?: manga.cover
        val description = writing["description"]?.jsonPrimitive?.contentOrNull ?: ""
        
        val genres = writing["genres"]?.jsonArray?.mapNotNull { element ->
            element.jsonObject["genre"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        } ?: emptyList()

        return manga.copy(
            title = title,
            cover = image,
            description = description,
            genres = genres,
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val slug = manga.key

        val query = """
            query Writing(${"$"}slug: String!) {
              writingBySlug(slug: ${"$"}slug) {
                id
                title
                slug
                scanlators {
                  scanlator {
                    scanlatorName
                    username
                    episodes(oldestFirst: false, slug: ${"$"}slug) {
                      id
                      subId
                      seqTitle
                      title
                      slug
                      __typename
                    }
                    __typename
                  }
                  __typename
                }
                __typename
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("slug", slug)
        }

        val response = graphqlRequest(query, variables)

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val data = jsonObj["data"]?.jsonObject ?: return emptyList()
        val writing = data["writingBySlug"]?.jsonObject ?: return emptyList()
        val scanlators = writing["scanlators"]?.jsonArray ?: return emptyList()

        if (scanlators.isEmpty()) return emptyList()

        val firstScanlator = scanlators[0].jsonObject
        val scanlator = firstScanlator["scanlator"]?.jsonObject ?: return emptyList()
        val episodes = scanlator["episodes"]?.jsonArray ?: return emptyList()

        return episodes.mapNotNull { element ->
            val episode = element.jsonObject
            val episodeTitle = episode["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val seqTitle = episode["seqTitle"]?.jsonPrimitive?.contentOrNull ?: ""
            val episodeSlug = episode["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val subId = episode["subId"]?.jsonPrimitive?.contentOrNull

            val chapterName = if (seqTitle.isNotBlank()) "$seqTitle. $episodeTitle" else episodeTitle

            ChapterInfo(
                name = chapterName,
                key = "$episodeSlug#$slug",
                number = subId?.toFloatOrNull() ?: 0f,
            )
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val parts = chapter.key.split("#")
        if (parts.size != 2) return listOf(Text("Error: Invalid chapter path"))
        
        val chapterSlug = parts[0]
        val writingSlug = parts[1]

        val query = """
            query EpisodeBySlug(${"$"}slug: String!, ${"$"}writingSlug: String!) {
                episodeBySlug(slug: ${"$"}slug, writingSlug: ${"$"}writingSlug) {
                    text
                }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("slug", chapterSlug)
            put("writingSlug", writingSlug)
        }

        val response = graphqlRequest(query, variables)

        val jsonObj = json.parseToJsonElement(response).jsonObject
        val data = jsonObj["data"]?.jsonObject ?: return listOf(Text("Error: No data"))
        val episode = data["episodeBySlug"]?.jsonObject ?: return listOf(Text("Error: No episode"))
        val text = episode["text"]?.jsonPrimitive?.contentOrNull ?: return listOf(Text("Error: No text"))

        return text.split("\n", "</p>", "<p>", "<br>", "<br/>", "<br />")
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
            pageContentSelector = "p",
        )
}
