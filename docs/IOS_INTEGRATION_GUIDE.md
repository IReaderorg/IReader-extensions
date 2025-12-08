# iOS Integration Guide for IReader

This guide explains how to integrate JS-compiled extensions into the main IReader iOS app.

## Overview

IReader extensions are compiled to JavaScript using Kotlin/JS, enabling dynamic source loading on iOS via JavaScriptCore. This approach complies with App Store guidelines while allowing the same Kotlin source code to work across Android, Desktop, and iOS.

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
│              sources-bundle.js (downloaded from repo)            │
├─────────────────────────────────────────────────────────────────┤
│  Contains ALL JS-enabled sources (~24KB for 1 source)           │
│  ├── FreeWebNovelKmp source + initFreeWebNovelKmp()             │
│  ├── AnotherSource + initAnotherSource()                        │
│  └── ... more sources                                           │
└─────────────────────────────────────────────────────────────────┘
```

## Repository Structure

The extension repository (`build/repo/`) contains:

```
build/repo/
├── index.json          # Full index with APK and JS info
├── apk/                # Android APKs
├── icon/               # Source icons
├── jar/                # Desktop JARs
└── js/
    ├── sources-bundle.js      # Single bundle with ALL JS sources
    └── sources-bundle.js.map  # Source map for debugging
```

## index.json Format

```json
{
  "extensions": [
    // Android APK extensions...
  ],
  "js": {
    "note": "JS sources require runtime.js from the main IReader app",
    "sources": [
      {
        "id": "freewebnovelkmp",
        "name": "FreeWebNovelKmp",
        "lang": "en",
        "file": "sources-bundle.js",
        "initFunction": "initFreeWebNovelKmp"
      },
      {
        "id": "anothersource",
        "name": "Another Source",
        "lang": "en",
        "file": "sources-bundle.js",
        "initFunction": "initAnotherSource"
      }
    ]
  }
}
```

## iOS App Implementation

### 1. Setup JavaScriptCore Context

```swift
import JavaScriptCore

class SourceManager {
    private let jsContext: JSContext
    private var loadedSources: [String: Bool] = [:]
    
    init() {
        jsContext = JSContext()!
        
        // Setup error handling
        jsContext.exceptionHandler = { context, exception in
            print("JS Error: \(exception?.toString() ?? "unknown")")
        }
        
        // Load runtime.js (bundled in app)
        if let runtimePath = Bundle.main.path(forResource: "runtime", ofType: "js"),
           let runtimeCode = try? String(contentsOfFile: runtimePath) {
            jsContext.evaluateScript(runtimeCode)
        }
        
        // Setup SourceRegistry if not provided by runtime
        setupSourceRegistry()
    }
    
    private func setupSourceRegistry() {
        jsContext.evaluateScript("""
            if (typeof SourceRegistry === 'undefined') {
                var SourceRegistry = {
                    sources: {},
                    register: function(id, factory) {
                        this.sources[id] = factory;
                        console.log('Registered source: ' + id);
                    },
                    create: function(id, deps) {
                        var factory = this.sources[id];
                        if (factory) {
                            return factory(deps);
                        }
                        throw new Error('Source not found: ' + id);
                    },
                    list: function() {
                        return Object.keys(this.sources);
                    }
                };
            }
        """)
    }
}
```

### 2. Load Sources Bundle

```swift
extension SourceManager {
    /// Download and load the sources bundle
    func loadSourcesBundle(from url: URL) async throws {
        let (data, _) = try await URLSession.shared.data(from: url)
        let jsCode = String(data: data, encoding: .utf8)!
        
        // Evaluate the bundle - this defines all source classes
        jsContext.evaluateScript(jsCode)
    }
    
    /// Initialize a specific source by calling its init function
    func initializeSource(initFunction: String) -> JSValue? {
        // Call the init function (e.g., "initFreeWebNovelKmp")
        let result = jsContext.evaluateScript("\(initFunction)()")
        
        if let info = result?.toDictionary() {
            print("Initialized source: \(info["name"] ?? "unknown")")
            print("  ID: \(info["id"] ?? "")")
            print("  Lang: \(info["lang"] ?? "")")
            print("  Registered: \(info["registered"] ?? false)")
        }
        
        return result
    }
}
```

### 3. Fetch Repository Index

```swift
struct JsSourceInfo: Codable {
    let id: String
    let name: String
    let lang: String
    let file: String
    let initFunction: String
}

struct JsSection: Codable {
    let note: String?
    let sources: [JsSourceInfo]
}

struct RepoIndex: Codable {
    let extensions: [ExtensionInfo]
    let js: JsSection?
}

extension SourceManager {
    func fetchAvailableSources(repoUrl: URL) async throws -> [JsSourceInfo] {
        let indexUrl = repoUrl.appendingPathComponent("index.json")
        let (data, _) = try await URLSession.shared.data(from: indexUrl)
        let index = try JSONDecoder().decode(RepoIndex.self, from: data)
        return index.js?.sources ?? []
    }
}
```

### 4. Complete Loading Flow

```swift
extension SourceManager {
    /// Load all JS sources from repository
    func loadAllSources(repoUrl: URL) async throws {
        // 1. Fetch index to get available sources
        let sources = try await fetchAvailableSources(repoUrl: repoUrl)
        guard !sources.isEmpty else {
            print("No JS sources available")
            return
        }
        
        // 2. Download the bundle (all sources share the same file)
        let bundleFile = sources.first!.file  // "sources-bundle.js"
        let bundleUrl = repoUrl
            .appendingPathComponent("js")
            .appendingPathComponent(bundleFile)
        
        try await loadSourcesBundle(from: bundleUrl)
        
        // 3. Initialize each source
        for source in sources {
            _ = initializeSource(initFunction: source.initFunction)
            loadedSources[source.id] = true
        }
        
        print("Loaded \(sources.count) JS source(s)")
    }
}
```

### 5. Using Sources

```swift
extension SourceManager {
    /// Create a source instance for use
    func createSource(id: String, deps: [String: Any]) -> JSValue? {
        // Create Dependencies object
        let depsJs = jsContext.evaluateScript("""
            (function() {
                // Create a Dependencies-like object
                return {
                    httpClients: { /* ... */ },
                    preferences: { /* ... */ }
                };
            })()
        """)
        
        // Create source instance via SourceRegistry
        return jsContext.evaluateScript("SourceRegistry.create('\(id)', \(depsJs))")
    }
    
    /// Search for novels
    func search(sourceId: String, query: String, page: Int) async throws -> [Novel] {
        // This would call the source's search method via JS bridge
        // Implementation depends on your SourceBridge setup
    }
}
```

## Building JS Sources

### Enable JS for a Source

In the extension's `build.gradle.kts`:

```kotlin
register(
    Extension(
        name = "MySource",
        versionCode = 1,
        libVersion = "2",
        lang = "en",
          // Enable JS compilation
    )
)
```

### Build Commands

```bash
# 1. Build Android extension (generates KSP files)
./gradlew :extensions:individual:en:mysource:kspEnReleaseKotlin

# 2. Build JS bundle
./gradlew :js-sources:jsBrowserProductionWebpack

# 3. Generate full repository
./gradlew repo
```

### Output

After running `./gradlew repo`:
- `build/repo/js/sources-bundle.js` - The JS bundle (~24KB per source)
- `build/repo/index.json` - Repository index with JS source metadata

## Runtime.js Requirements

The iOS app must bundle a `runtime.js` that provides:

1. **Kotlin stdlib** - Core Kotlin functions
2. **Kotlinx coroutines** - Async support
3. **Kotlinx serialization** - JSON parsing
4. **Ktor client** - HTTP requests (JS engine)
5. **Ksoup** - HTML parsing
6. **source-api** - Base classes (SourceFactory, HttpSource, models)

Build runtime.js from the `source-api` module with Kotlin/JS.

## Debugging

### Enable Source Maps

The `sources-bundle.js.map` file enables debugging:

```swift
// In development, you can load the source map for better error messages
if let mapPath = Bundle.main.path(forResource: "sources-bundle", ofType: "js.map") {
    // Configure JSContext to use source maps (if supported)
}
```

### Console Logging

JS `console.log` calls can be captured:

```swift
let console = jsContext.objectForKeyedSubscript("console")
console?.setObject({ (message: String) in
    print("[JS] \(message)")
} as @convention(block) (String) -> Void, forKeyedSubscript: "log")
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "SourceRegistry not found" | Ensure runtime.js is loaded before sources-bundle.js |
| "initXxx is not a function" | Check that the bundle loaded successfully |
| Source not registering | Verify the init function name matches index.json |
| HTTP requests failing | Ensure Ktor JS client is properly configured in runtime |

## File Sizes

| Component | Typical Size |
|-----------|--------------|
| runtime.js | ~800KB - 1.2MB |
| sources-bundle.js (1 source) | ~24KB |
| sources-bundle.js (10 sources) | ~50-80KB |

The bundle size grows slowly with more sources since dependencies are shared.
