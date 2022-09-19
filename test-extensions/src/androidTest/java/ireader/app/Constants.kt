package ireader.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ireader.core.http.AcceptAllCookiesStorage
import ireader.core.http.BrowserEngine
import ireader.core.http.HttpClients
import ireader.core.http.WebViewCookieJar
import ireader.core.http.WebViewManger
import ireader.core.prefs.AndroidPreferenceStore
import ireader.core.source.CatalogSource
import ireader.core.source.Dependencies
import ireader.core.source.TestSource

val context: Context = ApplicationProvider.getApplicationContext<Context>()
val cookie = AcceptAllCookiesStorage()
val cookieJar = WebViewCookieJar(cookie)
val httpClients = HttpClients(context, BrowserEngine(WebViewManger(context), cookieJar), cookie, cookieJar)
val androidPreferenceStore = AndroidPreferenceStore(context, "test-preferences")
val dependencies = Dependencies(httpClients, androidPreferenceStore)

val extension: CatalogSource = TestSource()
