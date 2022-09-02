package org.ireader.app.mock_components

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.BrowserUserAgent
import org.ireader.core_api.http.main.BrowseEngine
import org.ireader.core_api.http.main.HttpClients

class FakeHttpClients : HttpClients {
    override val browser: BrowseEngine
        get() = throw Exception("This test need to be run on real app")
    override val default: HttpClient
        get()  = HttpClient(OkHttp) {
            BrowserUserAgent()
        }
    override val cloudflareClient: HttpClient
        get() = HttpClient(OkHttp) {
            BrowserUserAgent()
        }
}