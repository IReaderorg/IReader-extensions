# iOS Support via Kotlin/JS

IReader supports iOS through Kotlin/JS compilation. Sources written in Kotlin are compiled to JavaScript and executed via JavaScriptCore on iOS devices, enabling dynamic source loading while remaining App Store compliant.

## Why JavaScript for iOS?

Apple prohibits loading dynamic native code at runtime. JavaScript execution via JavaScriptCore is allowed, making Kotlin/JS the ideal solution for cross-platform source support.

| Platform | Loading Method |
|----------|---------------|
| Android | DEX/APK dynamic loading |
| Desktop | JAR class loading |
| iOS | JavaScript via JavaScriptCore |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    IReader iOS App                               │
├─────────────────────────────────────────────────────────────────┤
│  runtime.js (bundled in app, ~800KB-1.2MB)                      │
│  ├── Kotlin stdlib, coroutines, serialization                   │
│  ├── Ktor HTTP client (JS engine)                               │
│  ├── Ksoup HTML parser                                          │
│  ├── source-api: HttpSource, SourceFactory, models              │
│  └── SourceRegistry + SourceBridge                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ provides globals
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              Extension JS Files (downloaded per source)          │
├─────────────────────────────────────────────────────────────────┤
│  freewebnovelkmp.js (~24KB)                                     │
│  ├── FreeWebNovelKmp source class                               │
│  ├── JsExtension implementation                                 │
│  └── initFreeWebNovelKmp() registration function                │
└─────────────────────────────────────────────────────────────────┘
```

## Loading Sequence

```swift
// 1. App startup - load runtime once
jsContext.evaluateScript(bundledRuntimeJs)

// 2. User installs source - download JS file
let sourceJs = download("https://repo.example.com/js/freewebnovelkmp.js")
jsContext.evaluateScript(sourceJs)

// 3. Initialize source
jsContext.evaluateScript("initFreeWebNovelKmp()")

// 4. Source is now registered and ready to use
let results = jsContext.evaluateScript("SourceBridge.search('freewebnovelkmp', 'query', 1)")
```

## Building JS Sources

### Enable JS for a Source

In the source's `build.gradle.kts`:

```kotlin
Extension(
    name = "MySource",
    versionCode = 1,
    libVersion = "2",
    lang = "en",
    enableJs = true,  // Enable JS compilation
)
```

### Build Commands

```bash
# Build JS bundle for all enabled sources
./gradlew :js-sources:jsBrowserProductionWebpack

# Generate repository with JS files
./gradlew repo

# Or run both
./gradlew :js-sources:createSourceIndex repo
```

### Output Structure

```
build/repo/
├── index.json          # Includes JS source metadata
├── apk/                # Android APKs
├── icon/               # Source icons
└── js/                 # JavaScript bundles
    ├── freewebnovelkmp.js
    └── freewebnovelkmp.js.map
```

## Repository Format

The `index.json` includes a `js` section for iOS sources:

```json
{
  "extensions": [...],
  "js": {
    "note": "JS sources require runtime.js from the main IReader app",
    "sources": [
      {
        "id": "freewebnovelkmp",
        "name": "FreeWebNovelKmp",
        "lang": "en",
        "file": "freewebnovelkmp.js",
        "initFunction": "initFreeWebNovelKmp"
      }
    ]
  }
}
```

## How KSP Generates JS Code

The `JsExtensionProcessor` automatically generates registration code for each source:

**Input:** Source class with `@Extension` annotation
```kotlin
@Extension
abstract class FreeWebNovelKmp(deps: Dependencies) : SourceFactory(deps) {
    override val name = "FreeWebNovelKmp"
    override val id = 4808063048038840027L
    // ...
}
```

**Generated:** `JsInit.kt` with registration functions
```kotlin
package ireader.freewebnovelkmp.js

class JsExtension(deps: Dependencies) : FreeWebNovelKmp(deps)

@JsExport
fun initFreeWebNovelKmp(): dynamic {
    SourceRegistry.register("freewebnovelkmp") { deps ->
        JsExtension(deps)
    }
    return jsObject {
        id = "4808063048038840027"
        name = "FreeWebNovelKmp"
        lang = "en"
    }
}
```

## File Sizes

| Component | Size | Notes |
|-----------|------|-------|
| runtime.js | ~800KB-1.2MB | Bundled in iOS app, loaded once |
| Per source | ~15-30KB | Downloaded when user installs |
| 50 sources | ~1.5MB total | User downloads as needed |

The webpack bundler produces self-contained JS files that include only the source-specific code, relying on `runtime.js` for shared dependencies.

## Source Compatibility

Sources using `SourceFactory` are automatically JS-compatible. The same Kotlin code runs on:
- Android (compiled to DEX)
- Desktop (compiled to JVM bytecode)
- iOS (compiled to JavaScript)

### Requirements for JS Compatibility

1. Extend `SourceFactory` (not legacy `HttpSource`)
2. Use Ksoup for HTML parsing (not Jsoup)
3. Use Ktor for HTTP requests
4. Avoid JVM-specific APIs

## Testing JS Sources

### Browser Console Test

```javascript
// After loading the JS file in a browser
initFreeWebNovelKmp()
// Should log: "FreeWebNovelKmp: Registered with SourceRegistry"
```

### Verify Bundle Contents

```powershell
# Check if init function exists
Get-Content "build/repo/js/freewebnovelkmp.js" | Select-String "initFreeWebNovelKmp"

# Check file size
(Get-Item "build/repo/js/freewebnovelkmp.js").Length / 1KB
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| JS file too large | Ensure webpack mode is `executable`, not `library` |
| Init function not found | Check KSP generated `JsInit.kt` in build output |
| Source not registering | Verify `SourceRegistry` is available from runtime.js |
| Build fails on JS target | Check for JVM-only dependencies |

## Related Documentation

- `iOS-Source-Architecture.md` - Detailed architecture and implementation plan
- `JS_INTEGRATION.md` - Technical integration details
- `compiler/src/main/kotlin/JsExtensionProcessor.kt` - KSP processor source
- `js-sources/build.gradle.kts` - JS build configuration

## Summary

IReader's iOS support works by:
1. Compiling Kotlin sources to JavaScript via Kotlin/JS
2. Bundling shared runtime in the iOS app (~1MB, loaded once)
3. Distributing individual source JS files (~15-30KB each)
4. Loading sources dynamically via JavaScriptCore
5. Using SourceRegistry/SourceBridge for iOS-JS communication

This approach enables the same source code to work across all platforms while complying with App Store requirements.
