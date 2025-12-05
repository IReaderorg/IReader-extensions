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
 * Main entry point - called when bundle is loaded.
 * Initializes all sources.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("initializeBundle")
fun initializeBundle() {
    println("IReader Sources Bundle initialized")
    println("Available sources: ${SourceRegistry.getSourceCount()}")
}
