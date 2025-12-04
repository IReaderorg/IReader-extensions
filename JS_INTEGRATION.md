# IReader Extensions - JavaScript Integration for iOS

This document explains how IReader extensions are built for iOS using Kotlin/JS.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    IReader Main Project                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  source-api (KMP Library)                                       │
│  ├── commonMain: HttpSource, SourceFactory, Models              │
│  ├── jsMain: HttpClients.js.kt (Ktor JS engine)                │
│  └── Publishes: io.github.ireaderorg:source-api:1.5.0          │
│                                                                  │
│  source-runtime-js (JS Only)                                    │
│  ├── SourceBridge.kt - Main iOS interop interface              │
│  ├── SourceRegistry.kt - Source factory registration           │
│  ├── JsDependencies.kt - Dependencies for JS context           │
│  └── Exports.kt - @JsExport helper functions                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  IReader-Extensions Project                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  common/ (KMP Library)                                          │
│  ├── commonMain: DateParser, HtmlCleaner, utilities            │
│  └── jsMain: (uses source-api JS)                              │
│                                                                  │
│  compiler/ (KSP Processors)                                     │
│  ├── ExtensionProcessor - Generates Extension.kt               │
│  └── JsExtensionProcessor - Generates JsInit.kt (when enabled) │
│                                                                  │
│  sources/en/mysource/                                           │
│  ├── build.gradle.kts: enableJs = true                         │
│  ├── main/src: MySource.kt (source implementation)             │
│  └── build/generated/ksp/.../js/JsInit.kt (auto-generated)     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## How It Works

### 1. Runtime Loading (iOS App)

The iOS app loads JavaScript in this order:

```swift
// 1. Load the shared runtime (from source-runtime-js)
jsContext.evaluateScript(loadFile("runtime.js"))

// 2. Initialize the runtime
jsContext.evaluateScript("initRuntime()")

// 3. Load individual source bundles
jsContext.evaluateScript(loadFile("freewebnovelkmp.js"))

// 4. Initialize the source
jsContext.evaluateScript("initFreeWebNovelKmp()")

// 5. Use SourceBridge to interact
let result = jsContext.evaluateScript("SourceBridge.search('freewebnovelkmp', 'test', 1)")
```

### 2. Source Registration Flow

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Source JS File  │────►│  SourceRegistry  │────►│   SourceBridge   │
│  (init function) │     │  (factory store) │     │  (iOS interface) │
└──────────────────┘     └──────────────────┘     └──────────────────┘
         │                        │                        │
         │ registerSource()       │ initSource()           │ search()
         │ (stores factory)       │ (creates instance)     │ getDetails()
         ▼                        ▼                        ▼
    Factory stored          Source created           JSON returned
```

### 3. SourceBridge API

The `SourceBridge` object (from source-runtime-js) provides these methods:

```kotlin
object SourceBridge {
    // Registration
    fun registerSource(id: String, source: Source)
    fun getRegisteredSourceIds(): Array<String>
    
    // Source info
    fun getSourceInfo(sourceId: String): String  // JSON
    fun getAllSourcesInfo(): String              // JSON array
    
    // Source operations (return Promise<String>)
    fun search(sourceId: String, query: String, page: Int): Promise<String>
    fun getPopular(sourceId: String, page: Int): Promise<String>
    fun getBookDetails(sourceId: String, bookJson: String): Promise<String>
    fun getChapters(sourceId: String, bookJson: String): Promise<String>
    fun getContent(sourceId: String, chapterJson: String): Promise<String>
    fun getContentText(sourceId: String, chapterJson: String): Promise<String>
}
```

## Creating a JS-Compatible Source

### Step 1: Source Implementation (commonMain)

Create your source extending `SourceFactory`:

```kotlin
// sources/en/mysource/main/src/ireader/mysource/MySource.kt
package ireader.mysource

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory

abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    override val name = "My Source"
    override val baseUrl = "https://example.com"
    override val lang = "en"
    override val id = 123456L
    
    override val exploreFetchers = listOf(
        BaseExploreFetcher(
            key = "search",
            endpoint = "/search?q={query}",
            selector = "div.item",
            nameSelector = "h3",
            linkSelector = "a",
            linkAtt = "href",
            type = Type.Search
        )
    )
    
    override val detailFetcher = Detail(
        nameSelector = "h1",
        descriptionSelector = "div.desc"
    )
    
    override val chapterFetcher = Chapters(
        selector = "ul.chapters li a",
        nameSelector = "a",
        linkSelector = "a",
        linkAtt = "href"
    )
    
    override val contentFetcher = Content(
        pageContentSelector = "div.content p"
    )
}
```

### Step 2: JS Init File (Auto-Generated by KSP)

The `JsExtensionProcessor` automatically generates the JS initialization file when you build the source. No manual creation needed!

The generated file (`<package>.js.JsInit.kt`) includes:
- `JsExtension` - Concrete implementation class
- `init<SourceName>()` - Registration function with @JsExport
- `createSource(deps)` - Direct instantiation function
- `getSourceInfo()` - Metadata function

Example generated code:
```kotlin
// Generated by JsExtensionProcessor - DO NOT EDIT
package ireader.mysource.js

class JsExtension(deps: Dependencies) : MySource(deps)

@JsExport
fun initMySource(): dynamic {
    console.log("My Source: Initializing source...")
    js("""
        if (typeof SourceRegistry !== 'undefined') {
            SourceRegistry.register('mysource', function(deps) {
                return new ireader.mysource.js.JsExtension(deps);
            });
        }
    """)
    return js("""({ id: 123456, name: "My Source", lang: "en", registered: true })""")
}

@JsExport
fun createSource(deps: Dependencies): JsExtension = JsExtension(deps)

@JsExport
fun getSourceInfo(): dynamic = js("""({ id: 123456, name: "My Source", lang: "en" })""")
```

### Step 3: Enable JS in build.gradle.kts

Add `enableJs = true` to your Extension configuration:

```kotlin
// sources/en/mysource/build.gradle.kts
listOf("en").map { lang ->
    Extension(
        name = "MySource",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "My awesome source",
        // Enable JS build for iOS support
        enableJs = true,
    )
}.also(::register)
```

When `enableJs = true`, the `JsExtensionProcessor` will generate `JsInit.kt` during the build.

## Build Commands

```bash
# Build Android APK (also generates JS init files via KSP)
./gradlew :extensions:individual:en:mysource:assembleRelease

# Collect JS init files to central location
./gradlew :extensions:individual:en:mysource:collectJsInitFiles

# Generate JS source index
./gradlew :extensions:individual:en:mysource:generateJsSourceIndex

# Output locations:
# - APK: build/outputs/apk/
# - JS init files: build/js-sources/
# - JS index: build/js-dist/index.json
```

## File Size Estimates

| Component | Size |
|-----------|------|
| runtime.js (shared) | ~800KB - 1.2MB |
| Per source .js | ~10-30KB |
| 50 sources total | ~2.5MB |

## Testing in Browser

```html
<!DOCTYPE html>
<html>
<head>
    <title>Source Test</title>
</head>
<body>
    <script src="runtime.js"></script>
    <script src="mysource.js"></script>
    <script>
        // Initialize
        initRuntime();
        initMySource();
        
        // Test search
        SourceBridge.search('mysource', 'test', 1)
            .then(result => console.log(JSON.parse(result)));
    </script>
</body>
</html>
```

## iOS Integration

See `iOS-Source-Architecture.md` for detailed iOS implementation using JavaScriptCore.

## Key Points

1. **source-api** provides the core classes with JS support via `@JsExport`
2. **source-runtime-js** provides `SourceBridge` and `SourceRegistry` for iOS interop
3. Each source needs a `jsMain` init file that registers with `SourceRegistry`
4. All source operations return JSON strings for easy Swift/Objective-C interop
5. HTTP requests use Ktor JS engine, which works in both browser and JavaScriptCore
