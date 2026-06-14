# IReader Source Test Server

A local Ktor server for testing IReader extension sources in your browser.

## Quick Start

```bash
# 1. Compile sources first
./gradlew assembleDebug

# 2. Run the test server
./gradlew :source-test-server:run
```

Then open http://localhost:8080

## Dynamic Source Loading with dex2jar

For automatic loading of ALL compiled sources without manual configuration, install **dex2jar**:

### Windows
1. Download from https://github.com/pxb1988/dex2jar/releases
2. Extract to `C:\tools\dex2jar` (or any location)
3. Add to PATH: `C:\tools\dex2jar`

### macOS/Linux
```bash
# Using Homebrew (macOS)
brew install dex2jar

# Or download and extract manually
wget https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip
unzip dex-tools-v2.4.zip
export PATH=$PATH:$(pwd)/dex-tools-v2.4
```

### How it works
1. Sources are compiled to APK files (Android format)
2. dex2jar converts APK → JAR (JVM format)
3. Test server loads JARs dynamically at runtime

With dex2jar installed, the test server will automatically discover and load ALL compiled sources!

## Manual Source Loading (Alternative)

If you don't want to use dex2jar, you can add sources as dependencies:

Edit `source-test-server/build.gradle.kts`:

```kotlin
dependencies {
    // Add sources you want to test
    implementation(project(":sources:en:freewebnovel:main"))
    implementation(project(":sources:en:novelfull:main"))
    implementation(project(":sources:tu:epiknovel:main"))
}
```

## Features

- **Search & Browse** - Test source search and listing functionality
- **Novel Details** - View parsed novel information  
- **Chapter Lists** - Test chapter fetching
- **Content Reading** - View parsed chapter content
- **Test Suite** - Run automated tests on sources
- **JSON View** - See raw API responses with timing
- **Hot Reload** - Auto-rebuild when .kt files change (no restart needed)
- **Available Sources** - See all compiled sources (even if not loaded)

## Hot Reload (Auto-Rebuild)

When you modify a source `.kt` file, the server can automatically rebuild and reload it.

### Usage

1. Start the server: `./gradlew :source-test-server:run`
2. Click "Start Hot-Reload" in the sidebar, or:
   ```bash
   curl -X POST http://localhost:8080/api/watch/start
   ```
3. Edit any source file in `sources/`
4. The server detects the change, runs Gradle build, and reloads the source automatically

### API Endpoints for Watch

| Endpoint | Description |
|----------|-------------|
| `POST /api/watch/start` | Start watching for file changes |
| `POST /api/watch/stop` | Stop watching |
| `GET /api/watch/status` | Check if watching is active |

### How it works

1. File watcher monitors `sources/` directory for `.kt` file changes
2. When a change is detected, identifies the source (e.g., `en/novelfull`)
3. Runs `./gradlew :extensions:individual:en:novelfull:assembleEnDebug`
4. After build completes, reloads the source via dex2jar
5. Source is immediately available for testing

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/sources` | List all loaded sources |
| `GET /api/available-sources` | List all compiled sources |
| `GET /api/dex2jar-status` | Check if dex2jar is available |
| `POST /api/reload` | Reload sources dynamically |
| `GET /api/sources/{id}/search?q=query&page=1` | Search novels |
| `GET /api/sources/{id}/details?url=...` | Get novel details |
| `GET /api/sources/{id}/chapters?url=...` | Get chapter list |
| `GET /api/sources/{id}/chapters?url=...&html=...` | Get chapters with pre-rendered HTML (from WebView) |
| `GET /api/sources/{id}/content?url=...` | Get chapter content |
| `GET /api/sources/{id}/content?url=...&html=...` | Get content with pre-rendered HTML (from WebView) |
| `GET /api/sources/{id}/test` | Run test suite |

## WebView Support

Some sources (like Novelshub) require JavaScript rendering for chapter lists or content. The app's WebView renders the page and passes the HTML to the source via:
- `Command.Chapter.Fetch(html = renderedHtml)` for chapter lists
- `Command.Content.Fetch(html = renderedHtml)` for content

The test server supports this by:
1. Using the headless browser to render JavaScript
2. Passing the rendered HTML to the source

### Headless Browser

The test server includes a headless Chrome browser that implements the `BrowserEngineInterface` from the source-api. This allows sources to use `deps.httpClients.browser.fetch()` to render JavaScript content.

**How it works:**
1. Test server initializes headless Chrome on startup
2. Sources can call `deps.httpClients.browser.fetch(url, selector)` to get rendered HTML
3. The rendered HTML is returned as a `BrowserResult`

**Requirements:**
- Chrome or Chromium installed on the system
- ChromeDriver is auto-downloaded

**API Endpoint:**
```bash
# Render a URL with JavaScript
curl -X POST "http://localhost:8080/api/browser/render?url=https://example.com&selector=.content&timeout=30"
```

**Using in sources:**
```kotlin
// In your source's getPageList or getChapterList:
val result = deps.httpClients.browser.fetch(
    url = chapterUrl,
    selector = ".chapter-content",
    timeout = 30000
)
if (result.isSuccess) {
    val doc = Ksoup.parse(result.responseBody)
    // Parse content from rendered HTML
}
```

## Project Structure

```
source-test-server/
├── build.gradle.kts      # Server dependencies
└── src/main/kotlin/ireader/testserver/
    ├── Main.kt           # Server entry point
    ├── Routes.kt         # API routes
    ├── Models.kt         # Data models
    ├── SourceManager.kt  # Source management & HTTP client
    ├── SourceScanner.kt  # Scans for available sources
    ├── SourceLoader.kt   # Loads sources from classpath
    ├── SourceWatcher.kt  # File watcher for hot-reload
    ├── Dex2JarLoader.kt  # Loads sources via dex2jar
    └── UI.kt             # Web UI
```

## Troubleshooting

### No sources found
1. Make sure you've compiled sources: `./gradlew assembleDebug`
2. Check if dex2jar is installed: `d2j-dex2jar --help`
3. Or add sources manually as dependencies

### dex2jar not found
- Ensure dex2jar is in your PATH
- Or set `DEX2JAR_HOME` environment variable
- Or place dex2jar in `tools/dex2jar/` directory
