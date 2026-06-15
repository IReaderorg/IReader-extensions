package ireader.readnovelfull

import org.junit.Test
import org.junit.Assert.*
import com.fleeksoft.ksoup.Ksoup

class ReadNovelFullTest {

    @Test
    fun `parseChaptersFromHtml should extract chapters with correct URL pattern`() {
        val html = """
            <html><body>
            <div id="list-chapter">
                <a href="/novel-slug/chapter-1-title.html"><span class="nchr-text">Chapter 1: Title</span></a>
                <a href="/novel-slug/chapter-10-title.html"><span class="nchr-text">Chapter 10: Title</span></a>
                <a href="/novel-slug/chapter-2-title.html"><span class="nchr-text">Chapter 2: Title</span></a>
                <a href="/novel-slug/chapter-20-title.html"><span class="nchr-text">Chapter 20: Title</span></a>
            </div>
            </body></html>
        """.trimIndent()
        
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<Pair<String, Int>>()
        doc.select("#list-chapter a[href*='/chapter-']").forEach { link ->
            val href = link.attr("href")
            val chapterMatch = Regex("/chapter-(\\d+)-").find(href)
            if (chapterMatch != null) {
                val num = chapterMatch.groupValues[1].toIntOrNull() ?: return@forEach
                chapters.add(link.text().trim() to num)
            }
        }
        
        assertEquals(4, chapters.size)
        val sorted = chapters.sortedBy { it.second }
        assertEquals(1, sorted[0].second)
        assertEquals(2, sorted[1].second)
        assertEquals(10, sorted[2].second)
        assertEquals(20, sorted[3].second)
    }

    @Test
    fun `parseChaptersFromHtml should use nchr-text for chapter names`() {
        val html = """
            <html><body>
            <div id="list-chapter">
                <a href="/test/chapter-1-awakening.html"><span class="nchr-text">Chapter 1: Class Awakening</span></a>
                <a href="/test/chapter-2-quest.html"><span class="nchr-text">Chapter 2: First Quest</span></a>
            </div>
            </body></html>
        """.trimIndent()
        
        val doc = Ksoup.parse(html)
        val chapters = mutableListOf<String>()
        doc.select("#list-chapter a[href*='/chapter-']").forEach { link ->
            val text = link.selectFirst(".nchr-text")?.text()?.trim() ?: link.text().trim()
            chapters.add(text)
        }
        
        assertEquals(2, chapters.size)
        assertEquals("Chapter 1: Class Awakening", chapters[0])
        assertEquals("Chapter 2: First Quest", chapters[1])
    }

    @Test
    fun `parseContentFromHtml should extract paragraphs from chr-content`() {
        val html = """
            <html><body>
            <div id="chr-content" class="chr-c">
                <p>First paragraph of the chapter.</p>
                <p>Second paragraph with more content.</p>
                <p>Third paragraph ending the scene.</p>
            </div>
            </body></html>
        """.trimIndent()
        
        val doc = Ksoup.parse(html)
        val contentDiv = doc.selectFirst("#chr-content")
        assertNotNull(contentDiv)
        
        val paragraphs = contentDiv!!.select("p").map { it.text() }.filter { it.isNotBlank() }
        assertEquals(3, paragraphs.size)
        assertEquals("First paragraph of the chapter.", paragraphs[0])
        assertEquals("Second paragraph with more content.", paragraphs[1])
        assertEquals("Third paragraph ending the scene.", paragraphs[2])
    }

    @Test
    fun `parseDetailsFromHtml should extract title and author from meta tags`() {
        val html = """
            <html><body>
            <meta property="og:novel:author" content="Test Author">
            <meta property="og:title" content="Test Novel Title">
            <meta property="og:description" content="Test description">
            <meta property="og:image" content="https://example.com/cover.jpg">
            </body></html>
        """.trimIndent()
        
        val doc = Ksoup.parse(html)
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
        val author = doc.selectFirst("meta[property=og:novel:author]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        val image = doc.selectFirst("meta[property=og:image]")?.attr("content")
        
        assertEquals("Test Novel Title", title)
        assertEquals("Test Author", author)
        assertEquals("Test description", description)
        assertEquals("https://example.com/cover.jpg", image)
    }

    @Test
    fun `novel URLs should use html extension`() {
        val url = "https://readnovelfull.com/my-longevity-simulation.html"
        val slug = url.removeSuffix(".html").substringAfterLast("/")
        assertEquals("my-longevity-simulation", slug)
    }
}
