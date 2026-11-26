package ireader.novelfire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NovelfireTest {

    @Test
    fun `explore endpoints use placeholders the source factory can replace`() {
        val endpoints = listOf(
            NOVELFIRE_POPULAR_ENDPOINT,
            NOVELFIRE_LATEST_ENDPOINT,
            NOVELFIRE_SEARCH_ENDPOINT,
        )

        endpoints.forEach { endpoint ->
            assertFalse("Double braces are never replaced: $endpoint", endpoint.contains("{{"))
            val resolved = endpoint.replace("{page}", "1").replace("{query}", "shadow")
            assertFalse("Unreplaced placeholder in $resolved", resolved.contains("{"))
        }
    }

    @Test
    fun `chapter pages are requested newest first`() {
        val urls = novelfireChapterPageUrls("https://novelfire.net", "book/shadow-slave", 3256)

        assertEquals(33, urls.size)
        assertEquals("https://novelfire.net/book/shadow-slave/chapters?page=33", urls.first())
        assertEquals("https://novelfire.net/book/shadow-slave/chapters?page=1", urls.last())
    }

    @Test
    fun `growing chapter count moves newest page beyond page one`() {
        val baseUrl = "https://novelfire.net"
        val novelPath = "book/shadow-slave"

        val beforeUrls = novelfireChapterPageUrls(baseUrl, novelPath, 3256)
        val afterUrls = novelfireChapterPageUrls(baseUrl, novelPath, 3301)

        assertEquals(33, novelfireChapterPages(3256))
        assertEquals(34, novelfireChapterPages(3301))
        assertEquals("$baseUrl/$novelPath/chapters?page=33", beforeUrls.first())
        assertEquals("$baseUrl/$novelPath/chapters?page=34", afterUrls.first())
        assertEquals("$baseUrl/$novelPath/chapters?page=1", afterUrls.last())
    }

    @Test
    fun `rate limit delay honors Retry-After and clamps invalid values`() {
        assertEquals(10_000L, novelfireRetryDelayMs("10", 10_000L, 30_000L))
        assertEquals(1_000L, novelfireRetryDelayMs("0", 10_000L, 30_000L))
        assertEquals(30_000L, novelfireRetryDelayMs("600", 10_000L, 30_000L))
        assertEquals(10_000L, novelfireRetryDelayMs(null, 10_000L, 30_000L))
        assertEquals(10_000L, novelfireRetryDelayMs("nope", 10_000L, 30_000L))
    }

    @Test
    fun `chapter list HTML is parsed with absolute keys`() {
        val html = """
            <ul class="chapter-list">
                <li><a href="/book/shadow-slave/chapter-3255" title="Chapter 3255 - Near the End">3255</a></li>
                <li><a href="https://novelfire.net/book/shadow-slave/chapter-3256" title="Chapter 3256 - Final">3256</a></li>
                <li><a href="#" title=""></a></li>
            </ul>
        """.trimIndent()

        val chapters = parseNovelfireChapters(html, "https://novelfire.net")

        assertEquals(2, chapters.size)
        assertEquals("Chapter 3255 - Near the End", chapters[0].name)
        assertEquals("https://novelfire.net/book/shadow-slave/chapter-3255", chapters[0].key)
        assertEquals("https://novelfire.net/book/shadow-slave/chapter-3256", chapters[1].key)
    }
}
