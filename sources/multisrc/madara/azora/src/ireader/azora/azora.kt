package ireader.azora


import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import ireader.core.log.Log
import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class Azora(val deps: Dependencies) : Madara(
    deps,
    key = "https://azorago.com",
    sourceName = "azora",
    sourceId = 65,
    language = "ar",
){
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let {
            return chaptersParse(Jsoup.parse(it.html)).reversed()
        }
    val html = client.post(
        "${manga.key}ajax/chapters/"
    ).bodyAsText()
        val chapters =
            chaptersParse(
                html.asJsoup(),
            )
        return chapters.reversed()
    }

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = baseUrl + element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").text()
        val dateUploaded = element.select("i").text()

        return ChapterInfo(name = name, key = link, dateUpload = parseChapterDate(dateUploaded))
    }
}
