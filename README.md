
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

### Common Utilities

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

See [IMPROVEMENTS.md](./IMPROVEMENTS.md) for full details.

# Disclaimer

The core architecture of this repository was originally inspired by [Tachiyomi Extensions](https://github.com/tachiyomiorg/tachiyomi-extensions-1.x), but has been significantly modified and extended for IReader.

## License

    Copyright (C) 2022 The IReader Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
