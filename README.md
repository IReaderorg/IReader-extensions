
# IReader Extension

This repository contains the available extension catalogues for the IReader app.

# Usage

Extension sources can be downloaded, installed, and uninstalled via the main IReader app. They are installed and uninstalled like regular apps, in `.apk` format.

# Contributing

Contributions are welcome!

To get started with development, see [CONTRIBUTING.md](./tutorial/CONTRIBUTING.md).

Check out the repo's [sample](https://github.com/kazemcodes/IReader-extensions/tree/master/sources/en).

## Quick Start

### Creating a New Source

```bash
python scripts/add-source.py
```

Answer 4 questions and get a working source template:
1. Source name (e.g., `NovelFull`)
2. Website URL (e.g., `https://novelfull.com`)
3. Language code (e.g., `en`)
4. Is it a Madara site? (y/n)

**For Madara sites** - generates zero-code source using `@MadaraSource` annotation.

**For other sites** - generates `SourceFactory` template with KSP annotations (`@AutoSourceId`, `@GenerateFilters`, `@GenerateCommands`). Just update the CSS selectors.

See [ADD_SOURCE_GUIDE.md](./docs/ADD_SOURCE_GUIDE.md) for details.

### Legacy Scripts

```bash
# Old script (still works)
python scripts/create-empty-source.py NovelExample https://novelexample.com en

# Convert from lnreader-plugins
python scripts/js-to-kotlin-converter.py plugin.ts en
```

---

## 🧪 Source Test Server

A built-in test server for testing IReader sources with real data. Features:

- **Visual Browser** - Browse novels like a real website at `/browse`
- **API Tester** - Test source methods with JSON responses at `/`
- **dex2jar Integration** - Automatically loads sources from compiled APKs
- **85+ Sources** - All compiled sources available for testing

### Running the Test Server

```bash
# Option 1: Quick start (uses cached APKs)
./gradlew testServer

# Option 2: Build all sources first, then start server
./gradlew buildAndTest

# Option 3: Manual steps
./gradlew assembleDebug          # Build sources
./gradlew :source-test-server:run  # Start server
```

Server runs at **http://localhost:8080**

### Android Studio Run Configurations

The project includes pre-configured run configurations (in the toolbar dropdown):

| Configuration | Description |
|--------------|-------------|
| 🧪 Test Server | Start the test server |
| 🔨 Build All Sources | Compile all source APKs |
| 🚀 Build + Test Server | Build sources then start server |
| 🆔 Generate Source ID | Generate a unique source ID |

### Quick Commands

| Command | Description |
|---------|-------------|
| `./gradlew testServer` | Start test server (port 8080) |
| `./gradlew buildAndTest` | Build all sources + start server |
| `./gradlew listSources` | List all sources with build commands |
| `./gradlew buildSourceHelp` | Show build command format |

### Building a Single Source

To build just one source (faster than building all):

```bash
# Format: ./gradlew :extensions:individual:{lang}:{name}:assembleDebug

# Examples:
./gradlew :extensions:individual:en:freewebnovel:assembleDebug
./gradlew :extensions:individual:en:royalroad:assembleDebug
./gradlew :extensions:individual:en:novelfull:assembleDebug

# Then start the test server
./gradlew testServer
```

Run `./gradlew listSources` to see all available sources with their build commands.

### Available Endpoints

| Endpoint | Description |
|----------|-------------|
| `/` | API Tester UI - Test source methods |
| `/browse` | Visual Browser - Browse novels like a real website |
| `/browse/{sourceId}` | Browse specific source |
| `/browse/{sourceId}/novel?url=...` | View novel details |
| `/browse/{sourceId}/read?url=...` | Read chapter content |
| `/api/sources` | List all loaded sources (JSON) |
| `/api/sources/{id}/search?q=...` | Search novels |
| `/api/sources/{id}/details?url=...` | Get novel details |
| `/api/sources/{id}/chapters?url=...` | Get chapter list |
| `/api/sources/{id}/content?url=...` | Get chapter content |
| `/api/sources/{id}/test` | Run automated test suite |

### Updating Sources

When you modify a source, you need to recompile and restart:

```bash
# Recompile specific source
./gradlew :sources:en:freewebnovel:assembleDebug

# Restart the server
./gradlew :source-test-server:run
```

The server loads sources at startup from APKs using dex2jar. JAR cache is automatically invalidated when APK changes.

### How It Works

1. **APK Discovery** - Scans `sources/*/build/intermediates/apk/*/debug/*.apk`
2. **dex2jar Conversion** - Converts DEX bytecode to JAR (cached in `source-test-server/jar-cache/`)
3. **Dynamic Loading** - Loads `tachiyomix.extension.Extension` class from each JAR
4. **Source Registration** - Registers sources with mock dependencies for testing

---

## Common Utilities

The repository includes shared utilities to reduce code duplication:

- **DateParser**: Parse relative and absolute dates
- **StatusParser**: Normalize status strings
- **ErrorHandler**: Standardized error handling with retry logic
- **ImageUrlHelper**: Handle image URLs and lazy loading
- **SelectorConstants**: Common CSS selectors for popular themes

See [Common Utilities README](./common/README.md) for details.

## Recent Improvements

- ✨ Added common utilities module for code reuse
- 🛠️ Created extension generator scripts
- 📝 Added comprehensive documentation
- 🔧 Improved build configuration
- 🎨 Added code quality tools (EditorConfig, Detekt)
- 📋 Added GitHub issue templates
- 🧪 Added source test server with visual browser

See [IMPROVEMENTS.md](./IMPROVEMENTS.md) for full details.

# Disclaimer

The core architecture of this repository was originally inspired by [Tachiyomi Extensions](https://github.com/tachiyomiorg/tachiyomi-extensions-1.x), but has been significantly modified and extended for IReader.

## License

    Copyright (C) 2022 The IReader Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
