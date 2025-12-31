package ireader.realmnovel

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tachiyomix.annotations.Extension

@Extension
abstract class RealmNovel(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String = "ar"
    override val baseUrl: String = "https://www.realmnovel.com"
    override val id: Long = 44L
    override val name: String = "RealmNovel"

    private val apiUrl = "https://www.realmnovel.com/api"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun getFilters() = listOf(Filter.Title())

    override fun getCommands() = listOf(Command.Detail.Fetch(), Command.Content.Fetch(), Command.Chapter.Fetch())

    override suspend fun getMangaList(page: Int): MangasPageInfo {
        val url = "$apiUrl/novels?page=$page&limit=18"
        val response = client.get(requestBuilder(url)).bodyAsText()
        return parseNovelList(response)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim()
        val url = if (!query.isNullOrBlank()) {
            "$apiUrl/novels/search?page=$page&q=$query"
        } else {
            "$apiUrl/novels?page=$page&limit=18"
        }
        val response = client.get(requestBuilder(url)).bodyAsText()
        return parseNovelList(response)
    }

    private fun parseNovelList(jsonText: String): MangasPageInfo {
        val data = json.parseToJsonElement(jsonText).jsonObject["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
        val novels = data.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cover = obj["cover"]?.jsonPrimitive?.contentOrNull ?: ""
            MangaInfo(
                key = "$baseUrl/novel/$slug",
                title = title,
                cover = if (cover.startsWith("http")) cover else "$baseUrl$cover"
            )
        }
        val hasNext = json.parseToJsonElement(jsonText).jsonObject["next"]?.jsonPrimitive?.contentOrNull != null
        return MangasPageInfo(novels, hasNext)
    }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        val document = client.get(requestBuilder(manga.key)).asJsoup()
        val title = document.selectFirst("h1")?.text()?.trim() ?: manga.title
        val author = document.selectFirst(".author")?.text()?.trim() ?: ""
        val description = document.selectFirst(".synopsis, .description")?.text()?.trim() ?: ""
        val cover = document.selectFirst(".novel-cover img")?.attr("src")?.let {
            if (it.startsWith("http")) it else "$baseUrl$it"
        } ?: manga.cover
        val statusText = document.selectFirst(".status, .novel-status")?.text()?.trim()?.lowercase() ?: ""
        val status = when {
            statusText.contains("مكتملة") || statusText.contains("completed") -> MangaInfo.COMPLETED
            statusText.contains("مستمرة") || statusText.contains("ongoing") -> MangaInfo.ONGOING
            statusText.contains("متوقفة") || statusText.contains("hiatus") -> MangaInfo.ON_HIATUS
            else -> MangaInfo.UNKNOWN
        }
        return manga.copy(title = title, author = author, description = description, cover = cover, status = status)
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val slug = manga.key.substringAfter("/novel/")
        val chapters = mutableListOf<ChapterInfo>()
        var skip = 0
        while (true) {
            val url = "$apiUrl/chapters/$slug/all?skip=$skip&limit=40&sort=asc"
            val response = client.get(requestBuilder(url)).bodyAsText()
            val data = json.parseToJsonElement(response).jsonObject["data"]?.jsonArray ?: break
            if (data.isEmpty()) break
            data.forEachIndexed { index, element ->
                val obj = element.jsonObject
                val number = obj["number"]?.jsonPrimitive?.intOrNull ?: index + 1
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "فصل $number"
                chapters.add(ChapterInfo(name = title, key = "$baseUrl/chapter/$slug/$number", number = number.toFloat()))
            }
            skip += 40
            if (data.size < 40) break
        }
        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo): List<Page> {
        val path = chapter.key.substringAfter("/chapter/")
        val url = "$apiUrl/chapters/$path/content"
        val content = json.parseToJsonElement(client.get(requestBuilder(url)).bodyAsText()).jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
        return content
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .split("\n\n")
            .mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }
            .map { Text(it) }
    }

    override fun HttpRequestBuilder.headersBuilder() {
        headers {
            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            append(HttpHeaders.Referrer, baseUrl)
            append(HttpHeaders.Accept, "application/json")
        }
    }
}
