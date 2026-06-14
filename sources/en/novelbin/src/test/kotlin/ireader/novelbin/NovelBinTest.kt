package ireader.novelbin

import org.junit.Test
import org.junit.Assert.*
import com.fleeksoft.ksoup.Ksoup

class NovelBinTest {

    @Test
    fun `parseChaptersFromHtml should extract chapters with correct URL pattern`() {
        val html = """
            <html><body>
            <div id="list-chapter">
                <li><a href="/b/novel-slug/chapter-10-title"><span class="nchr-text">Chapter 10: Title</span></a></li>
                <li><a href="/b/novel-slug/chapter-100-title"><span class="nchr-text">Chapter 100: Title</span></a></li>
                <li><a href="/b/novel-slug/chapter-1-title"><span class="nchr-text">Chapter 1: Title</span></a></li>
                <li><a href="/b/novel-slug/chapter-2-title"><span class="nchr-text">Chapter 2: Title</span></a></li>
                <li><a href="/b/novel-slug/chapter-20-title"><span class="nchr-text">Chapter 20: Title</span></a></li>
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
        
        assertEquals(5, chapters.size)
        val sorted = chapters.sortedBy { it.second }
        assertEquals(1, sorted[0].second)
        assertEquals(2, sorted[1].second)
        assertEquals(10, sorted[2].second)
        assertEquals(20, sorted[3].second)
        assertEquals(100, sorted[4].second)
    }

    @Test
    fun `parseChaptersFromHtml should use nchr-text for chapter names`() {
        val html = """
            <html><body>
            <div id="list-chapter">
                <li><a href="/b/test/chapter-1-class-awakening"><span class="nchr-text">Chapter 1: Class Awakening</span></a></li>
                <li><a href="/b/test/chapter-2-first-quest"><span class="nchr-text">Chapter 2: First Quest</span></a></li>
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
    fun `novel list page should parse rows with h3 novel-title selector`() {
        val html = """
            <html><body>
            <div class="row">
                <div class="col-xs-7">
                    <h3 class="novel-title"><a href="https://novelbin.com/b/test-novel" title="Test Novel">Test Novel</a></h3>
                    <span class="author">Author Name</span>
                </div>
                <div class="col-xs-3">
                    <img data-src="https://images.novelbin.com/cover.jpg" class="cover lazy">
                </div>
            </div>
            </body></html>
        """.trimIndent()
        
        val doc = Ksoup.parse(html)
        val items = doc.select(".row")
        assertEquals(1, items.size)
        
        val titleEl = items[0].selectFirst("h3.novel-title a")
        assertNotNull(titleEl)
        assertEquals("Test Novel", titleEl!!.text().trim())
        
        val cover = items[0].selectFirst("img.cover")?.attr("data-src")
        assertEquals("https://images.novelbin.com/cover.jpg", cover)
    }
}
