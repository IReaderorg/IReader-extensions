package com.ireader.rewayatclub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class RewayatClubSource {
    val id: Long = 17072024
    val name: String = "Rewayat Club"
    val baseUrl: String = "https://rewayat.club"
    val lang: String = "ar"
    val supportsLatest: Boolean = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    private fun getDocument(url: String): Document {
        return Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .referrer(baseUrl)
            .timeout(15000)
            .ignoreHttpErrors(true)
            .get()
    }

    fun searchManga(query: String, page: Int): MangasPage {
        val url = "$baseUrl/?s=${query.replace(" ", "+")}&post_type=wp-manga&paged=$page"
        val doc = getDocument(url)

        val mangas = doc.select("div.row.c-tabs-item__content").mapNotNull { element ->
            val linkEl = element.selectFirst("a")
            val imgEl = element.selectFirst("img")
            
            if (linkEl != null) {
                Manga(
                    id = extractMangaId(linkEl.attr("href")),
                    title = linkEl.attr("title").ifEmpty { linkEl.text() },
                    thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: "",
                    author = element.selectFirst("div.post-content_item:contains(المؤلف) div.summary-content")?.text() ?: "غير معروف",
                    genre = element.select("div.genres-content a").eachText().joinToString(),
                    status = parseStatus(element.selectFirst("div.post-content_item:contains(الحالة) div.summary-content")?.text() ?: "")
                )
            } else {
                null
            }
        }

        val hasNextPage = doc.select("a.next.page-numbers").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/manga/?page=$page&order=popular"
        val doc = getDocument(url)

        val mangas = doc.select("div.page-item-detail.manga").mapNotNull { element ->
            val linkEl = element.selectFirst("a")
            val imgEl = element.selectFirst("img")
            
            if (linkEl != null) {
                Manga(
                    id = extractMangaId(linkEl.attr("href")),
                    title = element.selectFirst("h3 a")?.text() ?: linkEl.text(),
                    thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: "",
                    author = element.selectFirst("div.authors a")?.text() ?: "غير معروف",
                    genre = element.select("div.genres a").eachText().joinToString(),
                    status = parseStatus(element.selectFirst("span.manga-status")?.text() ?: "")
                )
            } else {
                null
            }
        }

        val hasNextPage = doc.select("a.next.page-numbers").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/manga/?page=$page&order=latest"
        val doc = getDocument(url)

        val mangas = doc.select("div.page-item-detail.manga").mapNotNull { element ->
            val linkEl = element.selectFirst("a")
            val imgEl = element.selectFirst("img")
            
            if (linkEl != null) {
                Manga(
                    id = extractMangaId(linkEl.attr("href")),
                    title = element.selectFirst("h3 a")?.text() ?: linkEl.text(),
                    thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: "",
                    author = element.selectFirst("div.authors a")?.text() ?: "غير معروف",
                    genre = element.select("div.genres a").eachText().joinToString(),
                    status = parseStatus(element.selectFirst("span.manga-status")?.text() ?: "")
                )
            } else {
                null
            }
        }

        val hasNextPage = doc.select("a.next.page-numbers").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    fun getMangaDetails(manga: Manga): Manga {
        val url = "$baseUrl/novel/${manga.id}/"
        val doc = getDocument(url)

        return manga.copy(
            description = doc.selectFirst("div.description-summary")?.text() ?: "",
            author = doc.selectFirst("div.author-content a")?.text() ?: "غير معروف",
            genre = doc.select("div.genres-content a").eachText().joinToString(),
            status = parseStatus(doc.selectFirst("div.post-status span.summary-content")?.text() ?: ""),
            thumbnail = doc.selectFirst("div.summary_image img")?.attr("data-src") 
                ?: doc.selectFirst("div.summary_image img")?.attr("src") 
                ?: manga.thumbnail
        )
    }

    fun getChapterList(manga: Manga): List<Chapter> {
        val url = "$baseUrl/novel/${manga.id}/"
        val doc = getDocument(url)

        val chapters = doc.select("li.wp-manga-chapter").mapIndexed { index, element ->
            val chapterLink = element.selectFirst("a") ?: return@mapIndexed null
            val chapterUrl = chapterLink.attr("href")
            
            Chapter(
                id = extractChapterId(chapterUrl),
                name = chapterLink.text(),
                chapterNumber = index.toFloat(),
                dateUpload = parseDate(element.selectFirst("span.chapter-release-date")?.text() ?: ""),
                scanlator = "RewayatClub",
                url = chapterUrl
            )
        }.filterNotNull()

        return chapters.reversed()
    }

    fun getChapterContent(chapter: Chapter): ChapterContent {
        val doc = getDocument(chapter.url)

        var content = doc.selectFirst("div.reading-content, div.text-left, div.entry-content")
        
        if (content == null || content.text().length < 100) {
            content = doc.selectFirst("div#novel-content, div.chapter-content")
        }

        if (content != null) {
            content.select("div.ads, script, ins, iframe, .code-block, .advertisement, .sharedaddy, .social-share, .wp-block-buttons, .novel-buttons, .chapter-nav, div[style*='display:none'], div[class*='ad']").remove()
            
            content.select("p, div").forEach { el ->
                if (el.text().isBlank() || el.text().length < 5) {
                    el.remove()
                }
            }

            val text = content.html()
            return ChapterContent(text, emptyList())
        }

        return ChapterContent("لم يتم العثور على محتوى الفصل", emptyList())
    }

    private fun extractMangaId(url: String): String {
        return url.removePrefix("$baseUrl/")
            .removePrefix("novel/")
            .removeSuffix("/")
            .takeWhile { it != '?' && it != '#' }
    }

    private fun extractChapterId(url: String): String {
        return url.substringAfter("$baseUrl/")
            .substringBefore("?")
            .substringBefore("#")
    }

    private fun parseStatus(statusString: String): Int {
        return when {
            statusString.contains("مكتمل", true) -> 2
            statusString.contains("مستمر", true) -> 1
            statusString.contains("موقف", true) -> 3
            else -> 0
        }
    }

    private fun parseDate(dateString: String): Long {
        return try {
            val cleanDate = dateString.trim().takeIf { it.isNotBlank() } ?: return 0L
            dateFormat.parse(cleanDate)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

data class Manga(
    val id: String,
    val title: String,
    val thumbnail: String = "",
    val author: String = "",
    val genre: String = "",
    val status: Int = 0,
    val description: String = ""
)

data class Chapter(
    val id: String,
    val name: String,
    val chapterNumber: Float,
    val dateUpload: Long = 0L,
    val scanlator: String = "",
    val url: String = ""
)

data class ChapterContent(
    val text: String,
    val images: List<String>
)

data class MangasPage(
    val mangas: List<Manga>,
    val hasNextPage: Boolean
)

class RewayatClubFactory {
    fun createSources(): List<RewayatClubSource> = listOf(
        RewayatClubSource()
    )
}
