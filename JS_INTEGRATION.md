# IReader Extensions - JavaScript Integration for iOS

This document explains how IReader extensions are built for iOS using Kotlin/JS.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│              IReader Main App (source-api + source-runtime-js)   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  runtime.js (~800KB-1.2MB) - LOADED ONCE AT APP START           │
│  ├── Kotlin stdlib, coroutines, serialization                   │
│  ├── Ktor HTTP client (JS engine)                               │
│  ├── Ksoup HTML parser                                          │
│  ├── source-api: HttpSource, SourceFactory, models              │
│  └── source-runtime-js: SourceBridge, SourceRegistry            │
│                                                                  │
│  Provides global objects:                                        │
│  - SourceRegistry.register(id, factory)                         │
│  - SourceBridge.search(), getDetails(), getChapters(), etc.     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  IReader-Extensions (this project)               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Individual source .js files (~10-30KB each)                    │
│  ├── freewebnovelkmp.js                                         │
│  ├── royalroad.js                                               │
│  └── novelupdates.js                                            │
│                                                                  │
│  Each source file contains:                                      │
│  - Source class extending SourceFactory                         │
│  - JsExtension concrete implementation                          │
│  - init<SourceName>() function to register with SourceRegistry  │
│                                                                  │
│  NO runtime dependencies - uses globals from runtime.js         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## How It Works

### iOS App Loading Sequence

```swift
// 1. Load runtime.js ONCE at app start (from source-api + source-runtime-js)
jsContext.evaluateScript(bundledRuntimeJs)  // ~800KB, bundled in app

// 2. Initialize runtime
jsContext.evaluateScript("initRuntime()")

// 3. When user installs a source, download and load it
let sourceJs = downloadSource("freewebnovelkmp.js")  // ~15KB
jsContext.evaluateScript(sourceJs)

// 4. Initialize the source
jsContext.evaluateScript("initFreeWebNovelKmp()")

// 5. Use SourceBridge to interact
let result = jsContext.evaluateScript("SourceBridge.search('freewebnovelkmp', 'test', 1)")
```

### Source Registration Flow

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Source JS File  │────►│  SourceRegistry  │────►│   SourceBridge   │
│  (from CDN)      │     │  (from runtime)  │     │  (from runtime)  │
└──────────────────┘     └──────────────────┘     └──────────────────┘
         │                        │                        │
         │ initSource()           │ creates instance       │ search()
         │ calls register()       │ stores in map          │ returns JSON
         ▼                        ▼                        ▼
    Source registered        Ready to use            iOS gets results
```

## Creating a JS-Compatible Source

### Step 1: Source Implementation

Create your source extending `SourceFactory` (same as Android):

```kotlin
// sources/en/mysource/main/src/ireader/mysource/MySource.kt
@Extension
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    override val name = "My Source"
    override val baseUrl = "https://example.com"
    override val lang = "en"
    override val id = 123456L
    
    override val exploreFetchers = listOf(...)
    override val detailFetcher = Detail(...)
    override val chapterFetcher = Chapters(...)
    override val contentFetcher = Content(...)
}
```

### Step 2: Enable JS in build.gradle.kts

```kotlin
listOf("en").map { lang ->
    Extension(
        name = "MySource",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        // Enable JS generation
        enableJs = true,
    )
}.also(::register)
```

### Step 3: KSP Generates JsInit.kt

When you build, `JsExtensionProcessor` automatically generates:

```kotlin
// Generated: build/generated/ksp/.../ireader/mysource/js/JsInit.kt
package ireader.mysource.js

class JsExtension(deps: Dependencies) : MySource(deps)

@JsExport
fun initMySource(): dynamic {
    // Registers with SourceRegistry from runtime.js
    js("""
        SourceRegistry.register('mysource', function(deps) {
            return new ireader.mysource.js.JsExtension(deps);
        });
    """)
    return js("""({ id: "123456", name: "My Source", lang: "en" })""")
}

@JsExport
fun getSourceInfo(): dynamic = js("""({ id: "123456", name: "My Source", lang: "en" })""")
```

### Step 4: Build JS Bundle

```bash
# Build the source (generates JsInit.kt via KSP)
./gradlew :extensions:individual:en:mysource:assembleRelease

# Compile to JS and package for distribution
./gradlew :js-sources:createSourceIndex
```

Output in `js-sources/build/js-dist/`:
```
js-dist/
├── sources-bundle.js      # ~3-30KB - Extension source code ONLY
├── sources-bundle.js.map  # Source map for debugging
├── *.d.ts                 # TypeScript definitions
└── index.json             # Source metadata
```

**Important:** The output does NOT include runtime dependencies (kotlin-stdlib, ktor, ksoup, etc.) - those are provided by the main IReader app's `runtime.js`.

## File Sizes

| Component | Size | Location |
|-----------|------|----------|
| runtime.js | ~800KB-1.2MB | Bundled in iOS app |
| Per source .js | ~10-30KB | Downloaded from CDN |
| 50 sources | ~1.5MB total | User downloads as needed |

## Distribution

### CDN Structure
```
sources.ireader.app/
├── index.json              # Source catalog
└── js/
    ├── freewebnovelkmp.js  # Individual sources
    ├── royalroad.js
    └── novelupdates.js
```

### index.json Format
```json
{
  "version": 1,
  "sources": [
    {
      "id": "freewebnovelkmp",
      "name": "FreeWebNovel (KMP)",
      "lang": "en",
      "version": "2.1",
      "file": "freewebnovelkmp.js",
      "initFunction": "initFreeWebNovelKmp"
    }
  ]
}
```

## Key Points

1. **runtime.js is in the main IReader app** - NOT in this extensions project
2. **Extensions only produce small source-specific JS files** (~10-30KB each)
3. **Sources use globals from runtime.js** - SourceRegistry, SourceBridge, etc.
4. **KSP generates JsInit.kt** - no manual JS code needed
5. **Same source code works on Android, Desktop, and iOS**

## Related Files

- `iOS-Source-Architecture.md` - Detailed iOS implementation plan
- `IReader-Extensions-Migration-Plan.md` - Full migration checklist
- `compiler/src/main/kotlin/JsExtensionProcessor.kt` - KSP processor for JS init generation
