/**
 * IReader Sources - Self-contained JavaScript bundle
 * 
 * This file provides the main entry point and exports for the sources bundle.
 * All sources and their dependencies are bundled into a single JS file.
 * 
 * Usage in browser:
 * ```html
 * <script src="sources-bundle.js"></script>
 * <script>
 *   // Access sources via global IReaderSources object
 *   const sources = IReaderSources.getAllSources();
 *   const source = IReaderSources.getSource("freewebnovel");
 * </script>
 * ```
 * 
 * Usage in Node.js:
 * ```javascript
 * const IReaderSources = require('./sources-bundle.js');
 * const sources = IReaderSources.getAllSources();
 * ```
 */

package ireader.sources

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Registry of all available sources.
 * Sources register themselves when the bundle is loaded.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object SourceRegistry {
    private val sources = mutableMapOf<String, Any>()
    private val sourceFactories = mutableMapOf<String, () -> Any>()
    
    /**
     * Register a source factory function.
     * Called by generated init functions for each source.
     */
    @JsName("register")
    fun register(id: String, name: String, lang: String, factory: () -> Any) {
        sourceFactories[id] = factory
        println("Registered source: $name ($id) [$lang]")
    }
    
    /**
     * Get all registered source IDs.
     */
    @JsName("getSourceIds")
    fun getSourceIds(): Array<String> = sourceFactories.keys.toTypedArray()
    
    /**
     * Get a source instance by ID.
     * Creates a new instance if not already cached.
     */
    @JsName("getSource")
    fun getSource(id: String): Any? {
        return sources.getOrPut(id) {
            sourceFactories[id]?.invoke() ?: return null
        }
    }
    
    /**
     * Get all source instances.
     */
    @JsName("getAllSources")
    fun getAllSources(): Array<Any> {
        return sourceFactories.keys.mapNotNull { getSource(it) }.toTypedArray()
    }
    
    /**
     * Check if a source is registered.
     */
    @JsName("hasSource")
    fun hasSource(id: String): Boolean = sourceFactories.containsKey(id)
    
    /**
     * Get the number of registered sources.
     */
    @JsName("getSourceCount")
    fun getSourceCount(): Int = sourceFactories.size
}

/**
 * Shared HTTP client for all sources.
 * Uses Ktor JS engine for browser/Node.js compatibility.
 */
private val sharedHttpClient: HttpClient by lazy {
    HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 30000
        }
        defaultRequest {
            headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
    }
}

/**
 * Get the shared HTTP client instance.
 * Exported for use by iOS/Web runtime.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("getHttpClient")
fun getHttpClient(): HttpClient = sharedHttpClient

/**
 * Parse HTML string using Ksoup.
 * Exported for use by iOS/Web runtime.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("parseHtml")
fun parseHtml(html: String): Document = Ksoup.parse(html)

/**
 * Parse HTML from URL using Ksoup.
 * Exported for use by iOS/Web runtime.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("parseHtmlFromUrl")
fun parseHtmlFromUrl(html: String, baseUrl: String): Document = Ksoup.parse(html, baseUrl)

/**
 * Main entry point - called when bundle is loaded.
 * Initializes all sources and the HTTP client.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("initializeBundle")
fun initializeBundle() {
    // Force initialization of HTTP client to ensure it's bundled
    val client = sharedHttpClient
    println("IReader Sources Bundle initialized")
    println("HTTP Client: ${client.engine}")
    println("Available sources: ${SourceRegistry.getSourceCount()}")
}
