package ireader.zelluloza

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import com.fleeksoft.ksoup.Ksoup
import tachiyomix.annotations.Extension

@Extension
abstract class Zelluloza(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "ru"
    override val baseUrl: String get() = "https://zelluloza.ru"
    override val id: Long get() = 84
    override val name: String get() = "Целлюлоза"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort by",
            arrayOf("Rating", "Updated", "Reading Time", "Readers", "Popularity", "Best Sellers")
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value ?: ""
        val sortIndex = filters.findInstance<Filter.Sort>()?.value?.index ?: 0
        val sortValue = sortIndex.toString()
        
        val response = client.submitForm(
            url = "$baseUrl/ajaxcall/",
            formParameters = Parameters.build {
                append("op", "morebooks")
                append("par1", query)
                append("par2", "206:0:0:0.$sortValue.0.0.0.0.0.0.0.0.0.0.0..0..:$page")
                append("par4", "")
            }
        ).bodyAsText()
        
        val document = Ksoup.parse(response)
        val novels = document.select("div[style='display: flex;']").mapNotNull { element ->
            val link = element.select("a.txt")
            val name = link.attr("title")
            val href = link.attr("href").replace(Regex("\\D"), "")
            val cover = element.select("img.shadow").attr("src")
            
            if (name.isNotBlank() && href.isNotBlank()) {
                MangaInfo(
                    key = href,
                    title = name,
                    cover = "$baseUrl$cover"
                )
            } else null
        }
        
        return MangasPageInfo(novels, novels.size >= 20)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val detailFetch = commands.findInstance<Command.Detail.Fetch>()
        val document = if (detailFetch != null && detailFetch.html.isNotBlank()) {
            detailFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/books/${manga.key}")).asJsoup()
        }
        
        val name = document.select("h2.bookname").text().trim().ifBlank { manga.title }
        val cover = document.select("img.shadow").attr("src").let { 
            if (it.isNotBlank()) "$baseUrl$it" else manga.cover 
        }
        val genres = document.select(".gnres span[itemprop=genre]").map { it.text() }
        val summary = document.select("#bann_full").text().ifBlank { 
            document.select("#bann_short").text() 
        }
        val author = document.select(".author_link").text()
        
        val techDesc = document.select(".tech_decription").text()
        val status = if (techDesc.contains("Пишется")) MangaInfo.ONGOING else MangaInfo.COMPLETED
        
        return manga.copy(
            title = name,
            cover = cover,
            genres = genres,
            description = summary,
            author = author,
            status = status
        )
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        val document = if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            chapterFetch.html.asJsoup()
        } else {
            client.get(requestBuilder("$baseUrl/books/${manga.key}")).asJsoup()
        }
        
        val chapters = mutableListOf<ChapterInfo>()
        var chapterIndex = 0
        
        document.select("ul.g0 div.w800_m").forEach { element ->
            val isFree = element.select("div.chaptfree").isNotEmpty()
            if (isFree) {
                val chapterLink = element.select("a.chptitle")
                val name = chapterLink.text().trim()
                val href = chapterLink.attr("href")
                val path = href.split("/").drop(2).take(2).joinToString("/")
                
                if (name.isNotBlank() && path.isNotBlank()) {
                    chapters.add(ChapterInfo(
                        name = name,
                        key = path,
                        number = (chapterIndex + 1).toFloat(),
                        dateUpload = 0L
                    ))
                    chapterIndex++
                }
            }
        }
        
        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val parts = chapter.key.split("/")
        if (parts.size < 2) return emptyList()
        
        val bookId = parts[0]
        val chapterId = parts[1]
        
        val response = client.submitForm(
            url = "$baseUrl/ajaxcall/",
            formParameters = Parameters.build {
                append("op", "getbook")
                append("par1", bookId)
                append("par2", chapterId)
            }
        ).bodyAsText()
        
        val encrypted = response.split("<END>").firstOrNull()?.split("\n") ?: return emptyList()
        val decrypted = encrypted.mapNotNull { line ->
            if (line.isBlank()) null else decrypt(line)
        }.joinToString("")
            .replace("\r", "")
            .trim()
        
        // Apply cosmetic formatting
        val formatted = decrypted
            .replace(Regex("\\[\\*]([\\s\\S]*?)\\[/]"), "<b>$1</b>")
            .replace(Regex("\\[_]([\\s\\S]*?)\\[/]"), "<u>$1</u>")
            .replace(Regex("\\[-]([\\s\\S]*?)\\[/]"), "<s>$1</s>")
            .replace(Regex("\\[~]([\\s\\S]*?)\\[/]"), "<i>$1</i>")
        
        return listOf(Text(formatted))
    }

    private val alphabet = mapOf(
        '~' to '0', 'H' to '1', '^' to '2', '@' to '3', 'f' to '4',
        '0' to '5', '5' to '6', 'n' to '7', 'r' to '8', '=' to '9',
        'W' to 'a', 'L' to 'b', '7' to 'c', ' ' to 'd', 'u' to 'e', 'c' to 'f'
    )

    private fun decrypt(encrypted: String): String {
        if (encrypted.isBlank()) return ""
        
        val hexArray = mutableListOf<String>()
        var j = 0
        while (j < encrypted.length - 1) {
            val firstChar = encrypted[j]
            val secondChar = encrypted[j + 1]
            val first = alphabet[firstChar] ?: return ""
            val second = alphabet[secondChar] ?: return ""
            hexArray.add("$first$second")
            j += 2
        }
        
        val decoded = hexArray.joinToString("") { hex ->
            val code = hex.toIntOrNull(16) ?: return@joinToString ""
            code.toChar().toString()
        }
        
        return "<p>$decoded</p>"
    }

    override val exploreFetchers: List<BaseExploreFetcher> get() = emptyList()
    override val detailFetcher: Detail get() = SourceFactory.Detail()
    override val chapterFetcher: Chapters get() = SourceFactory.Chapters()
    override val contentFetcher: Content get() = SourceFactory.Content()
}
