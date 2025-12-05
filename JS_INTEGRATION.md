# IReader Extensions - JavaScript Integration

This document explains how IReader extensions are built as a self-contained JavaScript bundle.

## Architecture Overview

The JS bundle is **fully self-contained** - it includes ALL dependencies and can be used by any JavaScript application without requiring IReader's runtime.

```
┌─────────────────────────────────────────────────────────────────┐
│           sources-bundle.js (Self-Contained Bundle)              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  BUNDLED DEPENDENCIES (~2-5MB total):                           │
│  ├── Kotlin stdlib & coroutines                                 │
│  ├── Ktor HTTP client (JS engine)                               │
│  ├── Ksoup HTML parser                                          │
│  ├── kotlinx-serialization                                      │
│  ├── kotlinx-datetime                                           │
│  └── source-api: HttpSource, SourceFactory, models              │
│                                                                  │
│  SOURCES (50+ novel sources):                                   │
│  ├── FreeWebNovel, RoyalRoad, NovelUpdates, etc.               │
│  └── Each with init function for registration                   │
│                                                                  │
│  EXPORTS (UMD - works in browser, Node.js, AMD):                │
│  - IReaderSources.SourceRegistry                                │
│  - IReaderSources.getSource(id)                                 │
│  - IReaderSources.getAllSources()                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## How It Works

### Browser Usage

```html
<!-- Load the self-contained bundle -->
<script src="sources-bundle.js"></script>

<script>
// Access via global IReaderSources object
const registry = IReaderSources.SourceRegistry;

// Get all available source IDs
const sourceIds = registry.getSourceIds();
console.log('Available sources:', sourceIds);

// Get a specific source instance
const source = registry.getSource('freewebnovel');

// Use the source
const results = await source.getMangaList([], 1);
</script>
```

### Node.js Usage

```javascript
// CommonJS
const IReaderSources = require('./sources-bundle.js');

// Or ES Modules
import IReaderSources from './sources-bundle.js';

// Get all sources
const sources = IReaderSources.SourceRegistry.getAllSources();

// Get specific source
const source = IReaderSources.SourceRegistry.getSource('royalroad');
```

### iOS/Swift Usage (JavaScriptCore)

```swift
import JavaScriptCore

// 1. Load the self-contained bundle (includes everything)
let bundleJs = loadBundleFromCDN("sources-bundle.js")  // ~2-5MB
jsContext.evaluateScript(bundleJs)

// 2. Access sources directly
let sourceIds = jsContext.evaluateScript("IReaderSources.SourceRegistry.getSourceIds()")

// 3. Get and use a source
jsContext.evaluateScript("""
    const source = IReaderSources.SourceRegistry.getSource('freewebnovel');
    source.getMangaList([], 1).then(results => {
        // Handle results
    });
""")
```

### Source Registration Flow

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Bundle Loaded   │────►│  SourceRegistry  │────►│   Source Ready   │
│  (self-contained)│     │  (in bundle)     │     │   (use directly) │
└──────────────────┘     └──────────────────┘     └──────────────────┘
         │                        │                        │
         │ Auto-registers         │ getSource(id)          │ getMangaList()
         │ all sources            │ returns instance       │ getChapterList()
         ▼                        ▼                        ▼
    50+ sources ready        Get by ID              Fetch content
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

| Component | Size | Notes |
|-----------|------|-------|
| sources-bundle.js | ~1.6MB | Self-contained, includes all deps (Ktor, Ksoup, etc.) |
| sources-bundle.js.map | ~1.5MB | Source maps for debugging |
| js-index.json | ~16KB | Source metadata catalog |

## Distribution

### CDN Structure
```
raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/
├── js/
│   ├── sources-bundle.js      # Self-contained bundle
│   ├── sources-bundle.js.map  # Source maps
│   ├── js-index.json          # Source catalog (pretty)
│   └── js-index.min.json      # Source catalog (minified)
└── icon/
    └── ireader-*.png          # Source icons
```

### js-index.json Format
```json
[
  {
    "pkg": "ireader.freewebnovel.en",
    "name": "FreeWebNovel",
    "id": 123456789,
    "lang": "en",
    "code": 1,
    "version": "2.1",
    "description": "Novel source",
    "nsfw": false,
    "file": "sources-bundle.js",
    "initFunction": "initFreeWebNovel",
    "iconUrl": "https://raw.githubusercontent.com/.../icon/ireader-en-freewebnovel-v2.1.png"
  }
]
```

## Key Points

1. **Self-contained bundle** - No external runtime.js required
2. **UMD format** - Works in browser, Node.js, and AMD loaders
3. **All dependencies included** - Kotlin stdlib, Ktor, Ksoup, etc.
4. **Global export** - `IReaderSources` object available globally
5. **Same source code** - Works on Android, Desktop, iOS, and any JS environment
6. **KSP generates init code** - No manual JS code needed

## API Reference

### SourceRegistry

```typescript
interface SourceRegistry {
  // Get all registered source IDs
  getSourceIds(): string[];
  
  // Get a source instance by ID
  getSource(id: string): Source | null;
  
  // Get all source instances
  getAllSources(): Source[];
  
  // Check if source exists
  hasSource(id: string): boolean;
  
  // Get count of registered sources
  getSourceCount(): number;
}
```

### Source Interface

```typescript
interface Source {
  id: number;
  name: string;
  lang: string;
  baseUrl: string;
  
  // Fetch manga/novel list
  getMangaList(filters: Filter[], page: number): Promise<MangasPageInfo>;
  
  // Get manga/novel details
  getMangaDetails(manga: MangaInfo): Promise<MangaInfo>;
  
  // Get chapter list
  getChapterList(manga: MangaInfo): Promise<ChapterInfo[]>;
  
  // Get chapter content
  getPageList(chapter: ChapterInfo): Promise<Page[]>;
}
```

## Building

```bash
# Run KSP for all JS-enabled sources
./gradlew :extensions:individual:en:freewebnovel:kspEnReleaseKotlin

# Build the self-contained bundle
./gradlew :js-sources:jsBrowserProductionWebpack :js-sources:createSourceIndex

# Output in js-sources/build/js-dist/
```

## Related Files

- `js-sources/build.gradle.kts` - JS build configuration
- `js-sources/src/jsMain/kotlin/IReaderSources.kt` - Main entry point
- `js-sources/webpack.config.d/bundle.js` - Webpack UMD config
- `compiler/src/main/kotlin/JsExtensionProcessor.kt` - KSP processor
