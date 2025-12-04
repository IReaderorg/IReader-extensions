/**
 * JS-specific source registration for iOS runtime.
 * 
 * This file contains the @JsExport functions that register sources
 * with the SourceRegistry from runtime.js.
 * 
 * Generated sources (JsExtension classes) are imported from the
 * individual source modules and registered here.
 */
@file:OptIn(ExperimentalJsExport::class)

package ireader.js

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// Import generated JsExtension classes
import ireader.freewebnovelkmp.js.JsExtension as FreeWebNovelKmpExtension
import ireader.freewebnovelkmp.js.FreeWebNovelKmpInfo

/**
 * Initialize FreeWebNovelKmp source for iOS/JS runtime.
 */
@JsExport
fun initFreewebnovelkmp(): dynamic {
    console.log("FreeWebNovelKmp: Initializing source...")
    js("""
        if (typeof SourceRegistry !== 'undefined') {
            SourceRegistry.register('freewebnovelkmp', function(deps) {
                return new ireader.freewebnovelkmp.js.JsExtension(deps);
            });
            console.log('FreeWebNovelKmp: Registered with SourceRegistry');
        } else {
            console.warn('FreeWebNovelKmp: SourceRegistry not found. Load runtime.js first.');
        }
    """)
    return js("""({
        id: "4808063048038840027",
        name: "FreeWebNovelKmp",
        lang: "en",
        registered: typeof SourceRegistry !== 'undefined'
    })""")
}

/**
 * Get source info for FreeWebNovelKmp.
 */
@JsExport
fun getFreewebnovelkmpInfo(): dynamic = js("""({
    id: "4808063048038840027",
    name: "FreeWebNovelKmp",
    lang: "en"
})""")

/**
 * Initialize all sources at once.
 */
@JsExport
fun initAllSources(): dynamic {
    val results = mutableListOf<dynamic>()
    results.add(initFreewebnovelkmp())
    return results.toTypedArray()
}

/**
 * Get info for all available sources.
 */
@JsExport
fun getAllSourcesInfo(): dynamic = js("""[
    { id: "4808063048038840027", name: "FreeWebNovelKmp", lang: "en" }
]""")
