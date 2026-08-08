package ireader.azora

import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

/**
 * 🌙 Azora - Arabic Madara source with custom chapter fetching
 * 
 * This source needs custom chapter handling because it uses
 * a different AJAX endpoint for fetching chapters.
 * 
 * NOTE: Can't use @MadaraSource because of custom getChapterList()
 */
@Extension
@AutoSourceId(seed = "azora")
abstract class Azora(val deps: Dependencies) : Madara(
    deps,
    key = "https://azorago.com",
    sourceName = "Azora",
    sourceId = 66,  // Changed from 65 to avoid collision with ArNovel
    language = "ar",
) {
    /**
     * Custom chapter fetching - uses POST to ajax/chapters/ endpoint
     */
    override suspend fun getChapterList(
        manga: ireader.core.source.model.MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        // Handle webview fetch command
        commands.findInstance<Command.Chapter.Fetch>()?.let {
            return chaptersParse(Ksoup.parse(it.html)).reversed()
        }
        
        // Fetch chapters via AJAX POST
        val html = client.post("${manga.key}ajax/chapters/").bodyAsText()
        return chaptersParse(html.asJsoup()).reversed()
    }

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").text()
        val dateUploaded = element.select("i").text()
        return ChapterInfo(name = name, key = link, dateUpload = parseChapterDate(dateUploaded))
    }
}
