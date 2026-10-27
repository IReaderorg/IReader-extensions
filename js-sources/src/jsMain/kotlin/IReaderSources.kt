/**
 * IReader Sources - Self-contained JavaScript bundle
 * 
 * This file provides the main entry point and exports for the sources bundle.
 * All sources and their dependencies are bundled into a single JS file.
 * 
 * Usage in browser/iOS JavaScriptCore:
 * ```javascript
 *   // Access sources via global IReaderSources or SourceBridge
 *   const results = await SourceBridge.search("freewebnovel", "query", 1);
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import com.fleeksoft.ksoup.Ksoup
import ireader.core.prefs.Preference
import ireader.core.prefs.PreferenceStore
import ireader.core.source.CatalogSource
import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Filter
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.promise
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.Promise

/**
 * Metadata for a registered source across the JS bridge.
 */
@Serializable
data class SourceBridgeInfo(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String = ""
)

/**
 * In-memory PreferenceStore implementation for the JS runtime.
 */
class InMemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, Any>()

    private class MemPreference<T>(
        private val key: String,
        private val defaultValue: T,
        private val map: MutableMap<String, Any>
    ) : Preference<T> {
        override fun key(): String = key
        @Suppress("UNCHECKED_CAST")
        override fun get(): T = map[key] as? T ?: defaultValue
        override fun set(value: T) { map[key] = value as Any }
        override fun isSet(): Boolean = map.containsKey(key)
        override fun delete() { map.remove(key) }
        override fun defaultValue(): T = defaultValue
        override fun changes(): Flow<T> = MutableStateFlow(get())
        override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
    }

    override fun getString(key: String, defaultValue: String): Preference<String> =
        MemPreference(key, defaultValue, values)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        MemPreference(key, defaultValue, values)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        MemPreference(key, defaultValue, values)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        MemPreference(key, defaultValue, values)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        MemPreference(key, defaultValue, values)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        MemPreference(key, defaultValue, values)

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T
    ): Preference<T> {
        val strPref = getString(key, serializer(defaultValue))
        return object : Preference<T> {
            override fun key(): String = key
            override fun get(): T = try { deserializer(strPref.get()) } catch (_: Exception) { defaultValue }
            override fun set(value: T) { strPref.set(serializer(value)) }
            override fun isSet(): Boolean = strPref.isSet()
            override fun delete() { strPref.delete() }
            override fun defaultValue(): T = defaultValue
            override fun changes(): Flow<T> = strPref.changes().map { try { deserializer(it) } catch (_: Exception) { defaultValue } }
            override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
        }
    }

    override fun <T> getJsonObject(
        key: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule
    ): Preference<T> {
        val json = Json { this.serializersModule = serializersModule; ignoreUnknownKeys = true }
        return getObject(key, defaultValue, { json.encodeToString(serializer, it) }, { json.decodeFromString(serializer, it) })
    }
}

/**
 * Default Dependencies instance for sources running in JS without custom host injection.
 */
private val defaultDeps: Dependencies by lazy {
    Dependencies(
        httpClients = ireader.core.http.HttpClients(),
        preferences = InMemoryPreferenceStore()
    )
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("getDefaultDependencies")
fun getDefaultDependencies(): Dependencies = defaultDeps

/**
 * Bridge object that exposes source functionality to iOS JavaScriptCore and Web environments.
 * All async methods return Promises resolving to JSON strings, preventing Kotlin internal
 * naming/mangling from breaking host calls.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object SourceBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadedSources = mutableMapOf<String, CatalogSource>()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    init {
        js("""
            if (typeof globalThis !== 'undefined') {
                globalThis.SourceBridge = this;
            }
        """)
    }

    @JsName("registerSource")
    fun registerSource(id: String, source: Any?) {
        if (source is CatalogSource) {
            loadedSources[id] = source
            loadedSources[id.lowercase()] = source
            loadedSources[source.id.toString()] = source
            loadedSources[source.name.lowercase()] = source
            try {
                val sId = source.id.toString()
                js("""
                    if (source && typeof source === 'object') {
                        if (typeof source.getMangaList !== 'function') {
                            source.getMangaList = function(sortOrFilters, page) {
                                return SourceBridge.search(sId, '', page || 1);
                            };
                        }
                        if (typeof source.search !== 'function') {
                            source.search = function(query, page) {
                                return SourceBridge.search(sId, query || '', page || 1);
                            };
                        }
                        if (typeof source.getMangaDetails !== 'function') {
                            source.getMangaDetails = function(book, commands) {
                                var bJson = typeof book === 'string' ? book : JSON.stringify(book);
                                return SourceBridge.getBookDetails(sId, bJson);
                            };
                        }
                        if (typeof source.getChapterList !== 'function') {
                            source.getChapterList = function(book, commands) {
                                var bJson = typeof book === 'string' ? book : JSON.stringify(book);
                                return SourceBridge.getChapters(sId, bJson);
                            };
                        }
                        if (typeof source.getPageList !== 'function') {
                            source.getPageList = function(chapter, commands) {
                                var cJson = typeof chapter === 'string' ? chapter : JSON.stringify(chapter);
                                return SourceBridge.getContent(sId, cJson);
                            };
                        }
                    }
                """)
            } catch (e: Exception) {}
            println("SourceBridge: Registered source '$id' (${source.name}) [id=${source.id}]")
        }
    }

    @JsName("getMangaList")
    fun getMangaList(sourceId: String, page: Int): Promise<String> = search(sourceId, "", page)

    @JsName("unregisterSource")
    fun unregisterSource(id: String) {
        loadedSources.remove(id)
        loadedSources.remove(id.lowercase())
    }

    @JsName("getRegisteredSourceIds")
    fun getRegisteredSourceIds(): Array<String> = loadedSources.keys.distinct().toTypedArray()

    @JsName("getSource")
    fun getSource(id: String): CatalogSource? {
        val found = loadedSources[id] ?: loadedSources[id.lowercase()]
        if (found != null) return found

        // Fall back to SourceRegistry if registered there
        val fromRegistry = SourceRegistry.getSource(id) as? CatalogSource
            ?: SourceRegistry.getSource(id.lowercase()) as? CatalogSource
        if (fromRegistry != null) {
            registerSource(id, fromRegistry)
            return fromRegistry
        }
        return null
    }

    @JsName("getSourceInfo")
    fun getSourceInfo(sourceId: String): String {
        val source = getSource(sourceId) ?: return "{}"
        val info = SourceBridgeInfo(
            id = source.id,
            name = source.name,
            lang = source.lang,
            baseUrl = (source as? HttpSource)?.baseUrl ?: ""
        )
        return json.encodeToString(info)
    }

    @JsName("getAllSourcesInfo")
    fun getAllSourcesInfo(): String {
        val infos = loadedSources.values.distinctBy { it.id }.map { source ->
            SourceBridgeInfo(
                id = source.id,
                name = source.name,
                lang = source.lang,
                baseUrl = (source as? HttpSource)?.baseUrl ?: ""
            )
        }
        return json.encodeToString(infos)
    }

    @JsName("search")
    fun search(sourceId: String, query: String, page: Int): Promise<String> = scope.promise {
        val source = getSource(sourceId)
            ?: return@promise json.encodeToString(MangasPageInfo.empty())
        try {
            val result = if (query.isNotBlank()) {
                val filters = source.getFilters().toMutableList()
                filters.filterIsInstance<Filter.Title>().firstOrNull()?.let {
                    it.value = query
                }
                source.getMangaList(filters = filters, page = page)
            } else {
                val listings = source.getListings()
                if (listings.isNotEmpty()) {
                    source.getMangaList(listings.firstOrNull(), page)
                } else {
                    source.getMangaList(filters = emptyList(), page = page)
                }
            }
            json.encodeToString(result)
        } catch (e: Exception) {
            println("SourceBridge: Search error - ${e.message}")
            json.encodeToString(MangasPageInfo.empty())
        }
    }

    @JsName("getPopular")
    fun getPopular(sourceId: String, page: Int): Promise<String> = scope.promise {
        val source = getSource(sourceId)
            ?: return@promise json.encodeToString(MangasPageInfo.empty())
        try {
            val listings = source.getListings()
            val popularListing = listings.firstOrNull { it.name.equals("popular", ignoreCase = true) }
                ?: listings.firstOrNull()
            val result = source.getMangaList(popularListing, page)
            json.encodeToString(result)
        } catch (e: Exception) {
            println("SourceBridge: GetPopular error - ${e.message}")
            json.encodeToString(MangasPageInfo.empty())
        }
    }

    @JsName("getLatest")
    fun getLatest(sourceId: String, page: Int): Promise<String> = scope.promise {
        val source = getSource(sourceId)
            ?: return@promise json.encodeToString(MangasPageInfo.empty())
        try {
            val listings = source.getListings()
            val latestListing = listings.firstOrNull { it.name.equals("latest", ignoreCase = true) }
                ?: listings.drop(1).firstOrNull()
                ?: listings.firstOrNull()
            val result = source.getMangaList(latestListing, page)
            json.encodeToString(result)
        } catch (e: Exception) {
            println("SourceBridge: GetLatest error - ${e.message}")
            json.encodeToString(MangasPageInfo.empty())
        }
    }

    @JsName("getBookDetails")
    fun getBookDetails(sourceId: String, bookJson: String): Promise<String> = scope.promise {
        val source = getSource(sourceId) ?: return@promise "{}"
        try {
            val book = json.decodeFromString<MangaInfo>(bookJson)
            val result = source.getMangaDetails(book, emptyList())
            json.encodeToString(result)
        } catch (e: Exception) {
            println("SourceBridge: GetBookDetails error - ${e.message}")
            "{}"
        }
    }

    @JsName("getChapters")
    fun getChapters(sourceId: String, bookJson: String): Promise<String> = scope.promise {
        val source = getSource(sourceId) ?: return@promise "[]"
        try {
            val book = json.decodeFromString<MangaInfo>(bookJson)
            val result = source.getChapterList(book, emptyList())
            json.encodeToString(result)
        } catch (e: Exception) {
            println("SourceBridge: GetChapters error - ${e.message}")
            "[]"
        }
    }

    @JsName("getContent")
    fun getContent(sourceId: String, chapterJson: String): Promise<String> = scope.promise {
        val source = getSource(sourceId) ?: return@promise "[]"
        try {
            val chapter = json.decodeFromString<ChapterInfo>(chapterJson)
            val result = source.getPageList(chapter, emptyList())
            json.encodeToString(result)
        } catch (e: Exception) {
            println("SourceBridge: GetContent error - ${e.message}")
            "[]"
        }
    }

    @JsName("getContentText")
    fun getContentText(sourceId: String, chapterJson: String): Promise<String> = scope.promise {
        val source = getSource(sourceId) ?: return@promise "[]"
        try {
            val chapter = json.decodeFromString<ChapterInfo>(chapterJson)
            val pages = source.getPageList(chapter, emptyList())
            val textContent = pages.mapNotNull { page ->
                when (page) {
                    is Text -> page.text
                    else -> null
                }
            }
            json.encodeToString(textContent)
        } catch (e: Exception) {
            println("SourceBridge: GetContentText error - ${e.message}")
            "[]"
        }
    }
}

/**
 * Registry of all available sources.
 * Supports both 2-argument (id, factory) and 4-argument (id, name, lang, factory) registrations.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object SourceRegistry {
    private val sources = mutableMapOf<String, Any>()
    private val sourceFactories = mutableMapOf<String, (Any?) -> Any>()

    init {
        js("""
            if (typeof globalThis !== 'undefined') {
                globalThis.SourceRegistry = this;
            }
        """)
    }

    /**
     * Register a source factory function.
     * Can be invoked from JS as `SourceRegistry.register(id, factory)` or `SourceRegistry.register(id, name, lang, factory)`.
     */
    @JsName("register")
    fun register(id: String, nameOrFactory: Any?, lang: String? = null, factory: Any? = null) {
        val actualFactory: (Any?) -> Any = when {
            factory != null -> {
                val fn = factory.asDynamic()
                val lambda: (Any?) -> Any = { deps -> fn(deps) }
                lambda
            }
            nameOrFactory != null -> {
                val fn = nameOrFactory.asDynamic()
                val lambda: (Any?) -> Any = { deps -> fn(deps) }
                lambda
            }
            else -> {
                val lambda: (Any?) -> Any = { Unit }
                lambda
            }
        }
        val name = if (factory != null) nameOrFactory?.toString() ?: id else id
        sourceFactories[id] = actualFactory
        sourceFactories[id.lowercase()] = actualFactory
        println("Registered source factory: $name ($id) [${lang ?: "unknown"}]")
    }

    @JsName("getSourceIds")
    fun getSourceIds(): Array<String> = sourceFactories.keys.distinct().toTypedArray()

    @JsName("getSource")
    fun getSource(id: String): Any? {
        val cached = sources[id] ?: sources[id.lowercase()]
        if (cached != null) return cached
        val factory = sourceFactories[id] ?: sourceFactories[id.lowercase()] ?: return null
        val created = try {
            factory(null)
        } catch (e: Exception) {
            println("SourceRegistry: error creating source $id: ${e.message}")
            return null
        }
        sources[id] = created
        sources[id.lowercase()] = created
        if (created is CatalogSource) {
            SourceBridge.registerSource(id, created)
            SourceBridge.registerSource(id.lowercase(), created)
            SourceBridge.registerSource(created.id.toString(), created)
        }
        return created
    }

    @JsName("getAllSources")
    fun getAllSources(): Array<Any> {
        return sourceFactories.keys.distinct().mapNotNull { getSource(it) }.distinct().toTypedArray()
    }

    @JsName("hasSource")
    fun hasSource(id: String): Boolean =
        sourceFactories.containsKey(id) || sourceFactories.containsKey(id.lowercase())

    @JsName("getSourceCount")
    fun getSourceCount(): Int = sourceFactories.keys.distinct().size

    @JsName("initSource")
    fun initSource(sourceId: String): Boolean = getSource(sourceId) != null

    @JsName("initAllSources")
    fun initAllSources(): Int = getAllSources().size
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
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("parseHtml")
fun parseHtml(html: String): String = Ksoup.parse(html).html()

/**
 * Parse HTML with a base URL, returning the normalized document HTML.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("parseHtmlFromUrl")
fun parseHtmlFromUrl(html: String, baseUrl: String): String = Ksoup.parse(html, baseUrl).html()

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("getSourceBridge")
fun getSourceBridge(): SourceBridge = SourceBridge

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("getSourceRegistry")
fun getSourceRegistry(): SourceRegistry = SourceRegistry

/**
 * Main entry point - called when bundle is loaded.
 * Initializes all sources and the HTTP client.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("initializeBundle")
fun initializeBundle() {
    val client = sharedHttpClient
    js("""
        if (typeof globalThis !== 'undefined') {
            globalThis.SourceBridge = SourceBridge;
            globalThis.SourceRegistry = SourceRegistry;
        }
    """)
    println("IReader Sources Bundle initialized")
    println("HTTP Client: ${client.engine}")
    println("Available sources: ${SourceRegistry.getSourceCount()}")
}
