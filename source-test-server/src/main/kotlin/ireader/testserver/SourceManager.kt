package ireader.testserver

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import ireader.core.http.HttpClientsInterface
import ireader.core.http.BrowserEngine
import ireader.core.http.NetworkConfig
import ireader.core.http.SSLConfiguration
import ireader.core.http.CookieSynchronizer
import ireader.core.prefs.PreferenceStore
import ireader.core.prefs.Preference
import ireader.core.source.Dependencies
import ireader.core.source.CatalogSource
import ireader.core.source.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages source instances for testing
 */
class SourceManager {
    private val sources = ConcurrentHashMap<Long, CatalogSource>()
    private val sourcesByName = ConcurrentHashMap<String, CatalogSource>()
    
    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        engine {
            config {
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }
    
    private val dependencies = Dependencies(
        httpClients = TestHttpClients(httpClient),
        preferences = TestPreferenceStore()
    )
    
    fun registerSource(source: CatalogSource) {
        sources[source.id] = source
        sourcesByName[source.name.lowercase()] = source
        println("Registered source: ${source.name} (${source.id})")
    }
    
    fun getSource(id: Long): CatalogSource? = sources[id]
    
    fun getSourceByName(name: String): CatalogSource? = sourcesByName[name.lowercase()]
    
    fun getAllSources(): List<CatalogSource> = sources.values.toList()
    
    fun getDependencies(): Dependencies = dependencies
    
    /**
     * Remove a source by name.
     */
    fun removeByName(name: String) {
        val source = sourcesByName.remove(name.lowercase())
        if (source != null) {
            sources.remove(source.id)
            println("Removed source: ${source.name} (${source.id})")
        }
    }
    
    /**
     * Clear all sources.
     */
    fun clearAll() {
        println("Clearing all ${sources.size} sources...")
        sources.clear()
        sourcesByName.clear()
    }
    
    fun getSourceInfo(source: CatalogSource): SourceInfo {
        val filters = source.getFilters().map { filter ->
            FilterInfo(
                name = filter.name,
                type = filter::class.simpleName ?: "Unknown",
                options = when (filter) {
                    is Filter.Sort -> filter.options.toList()
                    is Filter.Select -> filter.options.toList()
                    else -> null
                }
            )
        }
        
        return SourceInfo(
            id = source.id.toString(),  // Convert to string for JavaScript
            name = source.name,
            lang = source.lang,
            baseUrl = (source as? ireader.core.source.HttpSource)?.baseUrl ?: "",
            hasFilters = source.getFilters().isNotEmpty(),
            hasCommands = source.getCommands().isNotEmpty(),
            filters = filters,
            listings = source.getListings().map { it.name }
        )
    }
}

/**
 * Simple HTTP clients implementation for testing - implements the interface directly
 */
class TestHttpClients(private val client: HttpClient) : HttpClientsInterface {
    override val default: HttpClient get() = client
    override val cloudflareClient: HttpClient get() = client
    override val browser: BrowserEngine get() = BrowserEngine()
    override val config: NetworkConfig get() = NetworkConfig()
    override val sslConfig: SSLConfiguration get() = SSLConfiguration()
    override val cookieSynchronizer: CookieSynchronizer get() = CookieSynchronizer()
}

/**
 * Simple preference store for testing
 */
class TestPreferenceStore : PreferenceStore {
    private val prefs = mutableMapOf<String, Any>()
    
    override fun getString(key: String, defaultValue: String): Preference<String> {
        return TestPreference(key, defaultValue, prefs)
    }
    
    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return TestPreference(key, defaultValue, prefs)
    }
    
    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return TestPreference(key, defaultValue, prefs)
    }
    
    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return TestPreference(key, defaultValue, prefs)
    }
    
    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return TestPreference(key, defaultValue, prefs)
    }
    
    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return TestPreference(key, defaultValue, prefs)
    }
    
    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T
    ): Preference<T> {
        return TestPreference(key, defaultValue, prefs)
    }
    
    override fun <T> getJsonObject(
        key: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule
    ): Preference<T> {
        return TestPreference(key, defaultValue, prefs)
    }
}

class TestPreference<T>(
    private val key: String,
    private val defaultValue: T,
    private val prefs: MutableMap<String, Any>
) : Preference<T> {
    override fun key(): String = key
    @Suppress("UNCHECKED_CAST")
    override fun get(): T = prefs[key] as? T ?: defaultValue
    override fun set(value: T) { prefs[key] = value as Any }
    override fun isSet(): Boolean = prefs.containsKey(key)
    override fun delete() { prefs.remove(key) }
    override fun defaultValue(): T = defaultValue
    override fun changes(): Flow<T> = flowOf(get())
    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        return MutableStateFlow(get())
    }
}
