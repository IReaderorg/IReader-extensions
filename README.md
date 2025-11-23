
# IReader Extension

This repository contains the available extension catalogues for the IReader app.

# Usage

Extension sources can be downloaded, installed, and uninstalled via the main IReader app. They are installed and uninstalled like regular apps, in `.apk` format.

# Contributing

Contributions are welcome!

To get started with development, see [CONTRIBUTING.md](./tutorial/CONTRIBUTING.md).

Check out the repo's [sample](https://github.com/kazemcodes/IReader-extensions/tree/master/sources/en).

## Quick Start

### Creating a New Extension

**Option 1: Using the Script (Recommended)**
```bash
# Create an empty extension with boilerplate code
python scripts/create-empty-source.py NovelExample https://novelexample.com en

# Create an NSFW extension
python scripts/create-empty-source.py AdultNovel https://adultnovel.com en --nsfw
```

**Option 2: Converting from lnreader-plugins**
```bash
# Convert a single plugin
python scripts/js-to-kotlin-converter.py lnreader-plugins-master/plugins/english/novelbuddy.ts en

# Batch convert all plugins
./scripts/batch-convert-lnreader.sh

# Convert only English plugins
./scripts/batch-convert-lnreader.sh en
```

**Option 3: Manual Creation**
See [Extension Template](./tutorial/EXTENSION_TEMPLATE.md) for a complete guide.

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

The core part of this repository all belongs to [tachiyomi](https://github.com/tachiyomiorg/tachiyomi-extensions-1.x)

## License

    Copyright (C) 2022 The IReader Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
