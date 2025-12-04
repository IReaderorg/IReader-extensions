package ireader.pandanovel

import android.util.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class PandaNovel(private val deps: Dependencies) : SourceFactory(
    deps = deps,
) {

    override val lang: String
        get() = "en"
    override val baseUrl: String
        get() = "https://www.panda-novel.com"
    override val id: Long
        get() = 29
    override val name: String
        get() = "PandaNovel"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "All",
                endpoint = "/browsenovel/all/all/all/all/all/{page}",
                selector = ".novel-ul .novel-li",
                nameSelector = "i",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                addBaseUrlToLink = true,
                coverSelector = "i",
                coverAtt = "data-src",
                nextPageSelector = "#pagination > ul > li:nth-child(7) > span",
                nextPageValue = "..."
            ),

            )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".novel-desc h1",
            coverSelector = ".header-content > .novel-cover > i",
            coverAtt = "data-src",
            authorBookSelector = ".icon-user + a",
            categorySelector = ".tags-list a",
            descriptionSelector = "#detailsApp > div:nth-child(4) p",
            statusSelector = ".novel-attr li:last-child strong",
            onStatus = fun (status): Long {
                Log.d("PandaNovel", status)
                return when(status) {
                    "Ongoing" -> MangaInfo.ONGOING
                    "Completed" -> MangaInfo.COMPLETED
                    else -> MangaInfo.UNKNOWN
                }
            }
        )

    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = ".chapter-list > ul:first-child li",
            nameSelector = "span",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    private fun isCloudflareProtection(page: String): Boolean {
        return page.contains("challenges.cloudflare.com")
    }

    override suspend fun getMangaDetailsRequest(
        manga: MangaInfo, commands: List<Command<*>>
    ): Document {
        val resp = deps.httpClients.browser.fetch(
            manga.key
        ).responseBody
        if (isCloudflareProtection(resp)) {
            throw Exception("CloudFlare protection detected, Use Webview to Fetch Chapter List")
        }
        val doc = resp.asJsoup()
        val text = doc.text()
        if (text == "" || text == " " || text == "null") {
            throw Exception("CloudFlare protection detected, Use Webview to Fetch Chapter List")
        }

        return doc
    }

    override suspend fun getChapterListRequest(
        manga: MangaInfo, commands: List<Command<*>>
    ): Document {
        val url = manga.key + "/chapters"
        val resp = deps.httpClients.browser.fetch(url).responseBody

        if (isCloudflareProtection(resp)) {
            throw Exception("CloudFlare protection detected, Use Webview to Fetch Chapter List")
        }
        val doc = resp.asJsoup()
        val text = doc.text()
        if (text == "" || text == " " || text == "null") {
            throw Exception("CloudFlare protection detected, Use Webview to Fetch Chapter List")
        }
        return doc
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { command ->
            return chaptersParse(Ksoup.parse(command.html))
        }
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {
                return@withContext chaptersParse(
                    getChapterListRequest(manga, commands),
                )
            }
        }.getOrThrow()
    }

    override val contentFetcher: Content
        get() = Content(
            pageTitleSelector = ".novel-content h2", pageContentSelector = "#novelArticle2 > p"
        )

    override suspend fun getContentRequest(
        chapter: ChapterInfo, commands: List<Command<*>>
    ): Document {
        var response = deps.httpClients.browser.fetch(
            chapter.key, timeout = 50000
        ).responseBody

        if (isCloudflareProtection(response)) {
            response =
                """<div id="novelArticle2"><p>Cloudflare Issue, Open in Webview and Fetch Chapter 
                |from context menu or Complete Couldflare challenge and refetch chapter</p></div>""".trimMargin()
        }

        return response.asJsoup()
    }
}