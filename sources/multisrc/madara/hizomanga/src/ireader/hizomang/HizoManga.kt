package ireader.hizomanga

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.get
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.Filter
import ireader.core.source.model.MangaInfo
import ireader.madara.Madara
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class HizoManga(val deps: Dependencies) : Madara(
    deps,
    key = "https://hizomanga.net",
    sourceName = "HizoManga",
    sourceId = 48,
    language = "ar",
    paths = Path(novel = "novel", novels = "series", chapter = "novel"),
) {

    override fun novelsOrderBy(sort: Filter.Sort.Selection?): String = when (sort?.index) {
        1 -> "alphabet"
        2 -> "rating"
        3 -> "trending"
        4 -> "views"
        else -> "new-manga"
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let {
            return chaptersParse(Ksoup.parse(it.html)).reversed()
        }

        val document = client.get(requestBuilder(manga.key)).asJsoup()
        return chaptersParse(document).reversed()
    }

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.select("a").attr("href").substringAfter(baseUrl)
        val name = element.select("a").text().trim()
        val dateUploaded = element.select("i").text()

        return ChapterInfo(
            name = name,
            key = "$baseUrl$link",
            dateUpload = parseChapterDate(dateUploaded),
        )
    }

    override fun detailParse(document: Document, manga: MangaInfo): MangaInfo {
        var title = document.select("div.manga-title h2").text().trim()
        if (title.isBlank()) {
            title = document.select("div.post-title>h1").text().trim()
        }
        var cover = document.select("div.summary_image a img").attr("src")
        if (cover.isBlank() || cover.contains("data:image/svg+xml", ignoreCase = true)) {
            cover = document.select("div.summary_image a img").attr("data-src")
        }
        var author = document.select("div.manga-author a").text().trim()
        if (author.isBlank()) {
            author = document.select("div.author-content>a").attr("title")
        }
        var description = document.select("div.manga-excerpt .excerpt-content p").eachText()
            .joinToString("\n\n")
        if (description.isBlank()) {
            description = document.select("div.description-summary div.summary__content").text()
        }
        val category = document.select("div.genres-content a").eachText()
        val statusText = document.select("div.manga-status").firstOrNull()
            ?.select("span")?.lastOrNull()?.text()

        return MangaInfo(
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = category,
            key = manga.key,
            status = statusText?.let { parseStatus(it) } ?: MangaInfo.UNKNOWN,
        )
    }

    override fun pageContentParse(document: Document): List<String> {
        val paragraphs = document.select(".reading-content .text-left p").eachText()
        var heading = document.select(".reading-content h3.chapter-name").text().trim()
        if (heading.isBlank()) {
            heading = document.select(".text-center").text()
        }

        return listOf(heading) + paragraphs
    }
}
