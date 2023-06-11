package ireader.lnmtl

import com.google.gson.Gson
import io.ktor.client.request.get
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.SourceFactory
import ireader.lnmtl.chapters.LNMTLResponse
import ireader.lnmtl.volume.LNMTLVolumeResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import tachiyomix.annotations.Extension

@Extension
abstract class LnMtl(deps: Dependencies) : SourceFactory(
    deps = deps,
) {
    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://lnmtl.com/"
    override val id: Long
        get() = 82
    override val name: String
        get() = "LnMtl"

    override fun getFilters(): FilterList = listOf(

    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/novel?orderBy=date&order=desc&filter=all&page={page}",
                selector = ".media",
                nameSelector = ".media-title a",
                linkSelector = ".media-title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 80,
            ),
            BaseExploreFetcher(
                "Trending",
                endpoint = "/novel?orderBy=favourites&order=desc&filter=all&page={page}",
                selector = ".media",
                nameSelector = ".media-title a",
                linkSelector = ".media-title a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
                maxPage = 80,
            ),

        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "span.novel-name",
            coverSelector = ".media-left img",
            coverAtt = "src",
            descriptionSelector = ".description p",
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".table",
            nameSelector = "a .chapter-link",
            linkSelector = "a .chapter-link",
            linkAtt = "href",
            reverseChapterList = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = ".chapter-title",
            pageContentSelector = ".translated",
        )

    val jsonDecoder = Json {
        ignoreUnknownKeys = true
    }
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        if (commands.isEmpty()) {
            val detailHtml =  client.get(requestBuilder(manga.key)).asJsoup()
            val scripts = detailHtml.select("script").html()
            val chapters = mutableListOf<ChapterInfo>()
            val volumeJson = scripts.parseScriptTagVolumne()
            val gson = Gson()
            val chaptersJson = scripts.parseScriptTagChapter()
//            val firstPageChapterJson =jsonDecoder.decodeFromString<LNMTLResponse>(chaptersJson)
//            val volume = jsonDecoder.decodeFromString<LNMTLVolumeResponse>(volumeJson)
            val firstPageChapterJson =gson.fromJson(chaptersJson,LNMTLResponse::class.java)
            val volume = gson.fromJson(volumeJson,LNMTLVolumeResponse::class.java)
            chapters.addAll(firstPageChapterJson.parseResponse())
            val allChapters = volume.map { item ->
                var currentPage = 1
                var maxPage = 1
                val volumeChapters = mutableListOf<ChapterInfo>()
                while (currentPage <=maxPage) {
                    val json = client.get(requestBuilder("https://lnmtl.com/chapter?page=$currentPage&volumeId=${item.id}")).asJsoup().body().html()
                    Log.error { json.toString() }
                    val parsedJson = Gson().fromJson(json, LNMTLResponse::class.java)
                    maxPage = parsedJson.last_page
                    volumeChapters.addAll(parsedJson.parseResponse())
                    currentPage++
                }
                volumeChapters
            }
            return allChapters.flatten()
        }
        return super.getChapterList(manga, commands)
    }
    fun String.parseScriptTagChapter():String {
        return this.substringBefore(";lnmtl.volumes").substringAfter("lnmtl.firstResponse = ")
    }
    fun String.parseScriptTagVolumne():String {
        return this.substringAfter("lnmtl.volumes = ").substringBefore(";lnmtl.route")
    }

    fun LNMTLResponse.parseResponse() : List<ChapterInfo> {
        return this.data.map {
            ChapterInfo(
                key = it.url,
                name = it.title,
                //   dateUpload = it.updated_at,
                number = it.number.toFloat(),
            )
        }
    }
}
