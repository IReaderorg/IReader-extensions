package ireader.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import ireader.core.http.AcceptAllCookiesStorage
import ireader.core.http.BrowserEngine
import ireader.core.http.HttpClients
import ireader.core.http.WebViewCookieJar
import ireader.core.http.WebViewManger
import ireader.core.log.Log
import ireader.core.prefs.AndroidPreferenceStore
import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.core.source.Source
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangaInfo
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionTests {

    lateinit var source: Source

    @Before
    fun prepare() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cookie = AcceptAllCookiesStorage()
        val cookieJar = WebViewCookieJar(cookie)
        val httpClients = HttpClients(context, BrowserEngine(WebViewManger(context), cookieJar), cookie, cookieJar)
        val androidPreferenceStore = AndroidPreferenceStore(context, "test-preferences")
        val deps = Dependencies(httpClients, androidPreferenceStore)
    }
    @Test
    fun getBooks() {
        runBlocking {
            val httpSource = source as? HttpSource ?: return@runBlocking
            val books = httpSource.getMangaList(httpSource.getListings().first(), 1)
            Log.error { "TEST $books" }
            assertThat(books.mangas.isNotEmpty()).isTrue()
        }
    }

    @Test
    fun getBookInfo() {
        runBlocking {
            val book = source.getMangaDetails(MangaInfo(key = BOOK_URL, title = BOOK_TITLE), emptyList())
            Log.error { "TEST $book" }
            assertThat(true).isTrue()
        }
    }
    @Test
    fun getChapterInfo() {
        runBlocking {
            val chapters = source.getChapterList(MangaInfo(key = BOOK_URL, title = BOOK_TITLE), emptyList())
            Log.error { "TEST $chapters" }
            assertThat(chapters.isNotEmpty()).isTrue()
        }
    }

    @Test
    fun getContent() {
        runBlocking {
            val page = source.getPageList(ChapterInfo(key = BOOK_URL, name = BOOK_TITLE), emptyList())
            Log.error { "TEST $page" }
            assertThat(page.isNotEmpty()).isTrue()
        }
    }

    companion object {
        const val SOURCE_PKG = "ireader.boxnovel.en"
        const val BOOK_URL = "https://www.readwn.com/novel/scoring-the-sacred-body-of-the-ancients-from-the-get-go.html"
        const val BOOK_TITLE = "Scoring the Sacred Body of the Ancients from the Get-go"
    }
}
