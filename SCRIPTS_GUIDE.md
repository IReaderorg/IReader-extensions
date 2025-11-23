# Extension Scripts Guide

Complete guide for using the extension creation and conversion scripts.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Creating Empty Extensions](#creating-empty-extensions)
3. [Converting from lnreader-plugins](#converting-from-lnreader-plugins)
4. [Batch Conversion](#batch-conversion)
5. [After Creation](#after-creation)
6. [Examples](#examples)

## Quick Start

### Prerequisites

- Python 3.7 or higher
- Git (for cloning lnreader-plugins if converting)

### Installation

No installation needed! The scripts are standalone Python files.

## Creating Empty Extensions

Use `create-empty-source.py` to generate a complete extension structure with boilerplate code.

### Basic Usage

```bash
python scripts/create-empty-source.py <name> <url> <lang>
```

### Parameters

- **name**: Extension class name (no spaces, e.g., `NovelExample`)
- **url**: Base URL of the website (e.g., `https://novelexample.com`)
- **lang**: Language code (e.g., `en`, `ar`, `fr`, `cn`)

### Options

- `--nsfw`: Mark the extension as containing NSFW content
- `--description "text"`: Custom description for the extension
- `--output path`: Custom output directory (default: `./sources`)

### Examples

```bash
# Basic English extension
python scripts/create-empty-source.py NovelHub https://novelhub.com en

# NSFW extension with custom description
python scripts/create-empty-source.py AdultNovels https://adultnovels.com en \
    --nsfw \
    --description "Adult content novels for mature readers"

# Arabic extension
python scripts/create-empty-source.py ArabicNovels https://arabicnovels.com ar

# French extension with custom output
python scripts/create-empty-source.py RomansFR https://romans.fr fr \
    --output ./my-extensions
```

### What Gets Created

```
sources/
└── en/
    └── novelhub/
        ├── build.gradle.kts          # Build configuration
        ├── README.md                 # Extension-specific README
        └── main/
            └── src/
                └── ireader/
                    └── novelhub/
                        └── NovelHub.kt   # Main source code
```

## Converting from lnreader-plugins

Use `js-to-kotlin-converter.py` to convert TypeScript/JavaScript plugins to Kotlin extensions.

### Basic Usage

```bash
python scripts/js-to-kotlin-converter.py <js_file> <lang> [output_dir]
```

### Parameters

- **js_file**: Path to the TypeScript/JavaScript plugin file
- **lang**: Language code for the extension
- **output_dir**: Output directory (optional, default: `./sources`)

### Examples

```bash
# Convert a single English plugin
python scripts/js-to-kotlin-converter.py \
    lnreader-plugins-master/plugins/english/novelbuddy.ts en

# Convert with custom output directory
python scripts/js-to-kotlin-converter.py \
    lnreader-plugins-master/plugins/english/royalroad.ts en \
    ./my-sources

# Convert an Arabic plugin
python scripts/js-to-kotlin-converter.py \
    lnreader-plugins-master/plugins/arabic/rewayat.ts ar
```

### What the Converter Does

1. ✅ Extracts metadata (id, name, site, version)
2. ✅ Generates Kotlin source code structure
3. ✅ Creates build.gradle.kts
4. ✅ Uses common utilities (DateParser, StatusParser, etc.)
5. ✅ Adds TODO comments for manual review
6. ⚠️ Requires manual selector verification

### Limitations

- Selectors need manual verification
- Complex JavaScript logic may need manual conversion
- Some site-specific features may not convert automatically
- Always test thoroughly after conversion

## Batch Conversion

Convert multiple lnreader-plugins at once.

### Linux/Mac

```bash
# Make script executable
chmod +x scripts/batch-convert-lnreader.sh

# Convert all plugins
./scripts/batch-convert-lnreader.sh

# Convert only English plugins
./scripts/batch-convert-lnreader.sh en

# Convert only Arabic plugins
./scripts/batch-convert-lnreader.sh ar
```

### Windows (PowerShell)

```powershell
# Convert all plugins
.\scripts\batch-convert-lnreader.ps1

# Convert only English plugins
.\scripts\batch-convert-lnreader.ps1 en

# Convert only Arabic plugins
.\scripts\batch-convert-lnreader.ps1 ar
```

### Supported Languages

| Language | Code | Language | Code |
|----------|------|----------|------|
| English | en | Polish | pl |
| Arabic | ar | Portuguese | pt |
| Chinese | cn | Russian | ru |
| French | fr | Spanish | es |
| Indonesian | in | Thai | th |
| Japanese | ja | Turkish | tu |
| Korean | ko | Ukrainian | uk |
| | | Vietnamese | vi |

## After Creation

### 1. Update Selectors

All generated code includes TODO comments marking selectors that need updating:

```kotlin
// TODO: Update this selector
val title = element.select(".title").text()
```

**How to find selectors:**
1. Open the website in a browser
2. Right-click on the element → "Inspect"
3. Look at the HTML structure
4. Use the element's class, id, or tag name

### 2. Add Icon

Create a 96x96px PNG icon and add it to:
```
main/res/mipmap-hdpi/ic_launcher.png
main/res/mipmap-mdpi/ic_launcher.png
main/res/mipmap-xhdpi/ic_launcher.png
main/res/mipmap-xxhdpi/ic_launcher.png
main/res/mipmap-xxxhdpi/ic_launcher.png
```

Or use a remote icon URL in `build.gradle.kts`:
```kotlin
icon = "https://example.com/icon.png"
```

### 3. Test the Extension

1. Open the project in Android Studio
2. Select your extension in Run/Debug Configuration
3. Build and run on device/emulator
4. Test all functionality:
   - ✅ Search
   - ✅ Latest/Popular listings
   - ✅ Book details
   - ✅ Chapter list
   - ✅ Chapter content
   - ✅ Image loading

### 4. Update Version

When making changes, increment `versionCode` in `build.gradle.kts`:

```kotlin
Extension(
    name = "NovelHub",
    versionCode = 2, // Increment this
    // ...
)
```

## Examples

### Example 1: Create a Simple Extension

```bash
# Create the extension
python scripts/create-empty-source.py SimpleNovels https://simplenovels.com en

# Navigate to the extension
cd sources/en/simplenovels

# Open in Android Studio and update:
# 1. Selectors in main/src/ireader/simplenovels/SimpleNovels.kt
# 2. Add icon to main/res/mipmap-*/ic_launcher.png
# 3. Test thoroughly
```

### Example 2: Convert lnreader Plugin

```bash
# Clone lnreader-plugins if you haven't
git clone https://github.com/LNReader/lnreader-plugins lnreader-plugins-master

# Convert a specific plugin
python scripts/js-to-kotlin-converter.py \
    lnreader-plugins-master/plugins/english/novelbuddy.ts en

# Review the generated code
cd sources/en/novelbuddy
# Update TODO comments and test
```

### Example 3: Batch Convert All English Plugins

```bash
# Ensure lnreader-plugins is cloned
git clone https://github.com/LNReader/lnreader-plugins lnreader-plugins-master

# Run batch conversion
./scripts/batch-convert-lnreader.sh en

# Review all generated extensions
ls sources/en/

# Test each extension individually
```

### Example 4: Create NSFW Extension

```bash
python scripts/create-empty-source.py AdultNovels https://adultnovels.com en \
    --nsfw \
    --description "Adult content novels (18+)"

# The extension will be marked as NSFW in the app
```

## Troubleshooting

### "Python not found"

**Solution:** Install Python 3.7+ from [python.org](https://www.python.org/downloads/)

### "Could not extract metadata"

**Cause:** The JS/TS file doesn't have the expected structure

**Solution:** 
- Check if the file implements `Plugin.PluginBase`
- Verify id, name, and site fields exist
- Try manual creation instead

### "Selectors not working"

**Cause:** Website structure differs from generated code

**Solution:**
1. Inspect the actual website HTML
2. Update selectors in the generated code
3. Test with different pages
4. Use browser DevTools to verify selectors

### "Build errors"

**Cause:** Missing dependencies or incorrect structure

**Solution:**
- Ensure the common module is included in settings.gradle.kts
- Check package names match directory structure
- Verify all imports are correct
- Run `./gradlew clean build`

### "Extension not appearing in app"

**Cause:** Build or installation issue

**Solution:**
1. Check versionCode is incremented
2. Verify build.gradle.kts is correct
3. Rebuild the extension
4. Reinstall the APK

## Best Practices

### 1. Use Common Utilities

Always use the provided utilities:

```kotlin
// Date parsing
DateParser.parseRelativeOrAbsoluteDate(dateStr)

// Status parsing
StatusParser.parseStatus(statusStr)

// Image URLs
ImageUrlHelper.normalizeUrl(url, baseUrl)

// Error handling
ErrorHandler.safeRequest { /* ... */ }
```

### 2. Add Descriptive Comments

```kotlin
// Selector for book title in search results
val title = element.select(".search-result .title").text()
```

### 3. Test Thoroughly

- Test with different search terms
- Test pagination
- Test with books that have many chapters
- Test with books that have few chapters
- Test error cases (invalid URLs, network errors)

### 4. Follow Naming Conventions

- Class names: PascalCase (e.g., `NovelHub`)
- Package names: lowercase (e.g., `novelhub`)
- Constants: UPPER_SNAKE_CASE (e.g., `USER_AGENT`)

### 5. Keep Code Clean

- Remove unused imports
- Remove commented-out code
- Use meaningful variable names
- Keep functions small and focused

## Getting Help

- Check [CONTRIBUTING.md](./tutorial/CONTRIBUTING.md) for contribution guidelines
- See [Extension Template](./tutorial/EXTENSION_TEMPLATE.md) for detailed examples
- Review [Common Utilities README](./common/README.md) for utility documentation
- Look at existing extensions for reference

## Contributing

If you create useful scripts or improvements:

1. Fork the repository
2. Create a feature branch
3. Add your script with documentation
4. Submit a pull request

We welcome contributions!
