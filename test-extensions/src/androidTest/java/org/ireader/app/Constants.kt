package org.ireader.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.ireader.core_api.http.AcceptAllCookiesStorage
import org.ireader.core_api.http.WebViewCookieJar
import org.ireader.core_api.http.WebViewManger
import org.ireader.core_api.http.impl.BrowseEngineImpl
import org.ireader.core_api.http.impl.HttpClientsImpl
import org.ireader.core_api.prefs.AndroidPreferenceStore
import org.ireader.core_api.source.CatalogSource
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.TestSource

val context: Context = ApplicationProvider.getApplicationContext<Context>()
val cookie = AcceptAllCookiesStorage()
val cookieJar = WebViewCookieJar(cookie)
val httpClients = HttpClientsImpl(context, BrowseEngineImpl(WebViewManger(context), cookieJar), cookie, cookieJar)
val androidPreferenceStore = AndroidPreferenceStore(context, "test-preferences")
val dependencies = Dependencies(httpClients, androidPreferenceStore)

val extension: CatalogSource = TestSource()
