package org.ireader.app.tests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import ireader.wnmtl.WnMtl
import org.ireader.app.BOOK_NAME
import org.ireader.app.BOOK_URL
import org.ireader.app.extension
import org.ireader.core_api.http.AcceptAllCookiesStorage
import org.ireader.core_api.http.WebViewCookieJar
import org.ireader.core_api.http.WebViewManger
import org.ireader.core_api.http.impl.BrowseEngineImpl
import org.ireader.core_api.http.impl.HttpClientsImpl
import org.ireader.core_api.prefs.AndroidPreferenceStore
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.Source
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.MangaInfo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterChecker {
    var chepters: List<ChapterInfo> = emptyList()
    lateinit var extension:Source
    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext<Context>()
            val cookie = AcceptAllCookiesStorage()
            val cookieJar = WebViewCookieJar(cookie)
            val httpClients = HttpClientsImpl(context, BrowseEngineImpl(WebViewManger(context), cookieJar),cookie,cookieJar)
            val androidPreferenceStore = AndroidPreferenceStore(context, "test-preferences")
            val dependencies = Dependencies(httpClients, androidPreferenceStore)
            extension = object : WnMtl(dependencies) {

            }
            chepters =  extension.getChapterList(MangaInfo(key = BOOK_URL, title = BOOK_NAME), emptyList())
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