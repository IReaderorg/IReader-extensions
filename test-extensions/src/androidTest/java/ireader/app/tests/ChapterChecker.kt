package ireader.app.tests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import ireader.armtl.ArMtl
import ireader.app.BOOK_NAME
import ireader.app.BOOK_URL
import ireader.core.http.AcceptAllCookiesStorage
import ireader.core.http.BrowserEngine
import ireader.core.http.HttpClients
import ireader.core.http.WebViewCookieJar
import ireader.core.http.WebViewManger
import ireader.core.prefs.AndroidPreferenceStore
import ireader.core.source.Dependencies
import ireader.core.source.Source
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangaInfo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterChecker {
    var chepters: List<ChapterInfo> = emptyList()
    lateinit var extension: Source
    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext<Context>()
            val cookie = AcceptAllCookiesStorage()
            val cookieJar = WebViewCookieJar(cookie)
            val httpClients = HttpClients(context, BrowserEngine(WebViewManger(context), cookieJar), cookie, cookieJar)
            val androidPreferenceStore = AndroidPreferenceStore(context, "test-preferences")
            val dependencies = Dependencies(httpClients, androidPreferenceStore)
            extension = object : ArMtl(dependencies) {
            }
            chepters = extension.getChapterList(MangaInfo(key = BOOK_URL, title = BOOK_NAME), emptyList())
            print(chepters)
        }
    }

    @Test
    fun checkChapterList() {
        assertThat(chepters.isNotEmpty()).isTrue()
    }
    @Test
    fun checkName() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.name.isNotBlank() }).isTrue()
    }
    @Test
    fun checkKey() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.key.isNotBlank() }).isTrue()
    }
    @Test
    fun checkDateUploaded() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.dateUpload != 0L }).isTrue()
    }
    @Test
    fun checkChapterNumber() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.number != -1f }).isTrue()
    }
    @Test
    fun checkTranstor() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.scanlator.isNotBlank() }).isTrue()
    }
}
