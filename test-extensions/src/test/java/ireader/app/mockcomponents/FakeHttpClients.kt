package ireader.app.mockcomponents

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import ireader.core.http.BrowserEngine
import ireader.core.http.HttpClientsInterface

class FakeHttpClients : HttpClientsInterface {
    override val browser: BrowserEngine
        get() = throw Exception("This test need to be run on real app")
    override val default: HttpClient
        get() = HttpClient(OkHttp) {
            BrowserUserAgent()
        }
    override val cloudflareClient: HttpClient
        get() = HttpClient(OkHttp) {
            BrowserUserAgent()
        }
}
