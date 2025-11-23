# IReader Extension Scripts

This directory contains utility scripts for creating and converting extensions.

## Scripts

### 1. create-empty-source.py

Creates a complete empty extension structure with boilerplate code.

**Usage:**
```bash
python scripts/create-empty-source.py <name> <url> <lang> [options]
```

**Examples:**
```bash
# Create a basic English extension
python scripts/create-empty-source.py NovelExample https://novelexample.com en

# Create an NSFW extension with custom description
python scripts/create-empty-source.py AdultNovel https://adultnovel.com en --nsfw --description "Adult content novels"

# Create an Arabic extension
python scripts/create-empty-source.py ArabicNovels https://arabicnovels.com ar

# Specify custom output directory
python scripts/create-empty-source.py MyNovel https://mynovel.com en --output ./my-sources
```

**Arguments:**
- `name`: Extension name (no spaces, e.g., NovelExample)
- `url`: Base URL of the website (e.g., https://example.com)
- `lang`: Language code (en, ar, fr, cn, etc.)

**Options:**
- `--nsfw`: Mark extension as NSFW
- `--description`: Custom description for the extension
- `--output`: Output directory (default: ./sources)

**What it creates:**
```
sources/
└── en/
    └── novelexample/
        ├── build.gradle.kts
        ├── README.md
        └── main/
            └── src/
                └── ireader/
                    └── novelexample/
                        └── NovelExample.kt
```

### 2. js-to-kotlin-converter.py

Converts lnreader-plugins (TypeScript/JavaScript) to IReader extensions (Kotlin).

**Usage:**
```bash
python scripts/js-to-kotlin-converter.py <js_file> <lang> [output_dir]
```

**Examples:**
```bash
# Convert a single plugin
python scripts/js-to-kotlin-converter.py lnreader-plugins-master/plugins/english/novelbuddy.ts en

# Convert with custom output directory
python scripts/js-to-kotlin-converter.py lnreader-plugins-master/plugins/english/royalroad.ts en ./sources

# Convert an Arabic plugin
python scripts/js-to-kotlin-converter.py lnreader-plugins-master/plugins/arabic/rewayat.ts ar
```

**Arguments:**
- `js_file`: Path to the TypeScript/JavaScript plugin file
- `lang`: Language code for the extension
- `output_dir`: Output directory (default: ./sources)

**Features:**
- Extracts metadata (id, name, site, version)
- Generates Kotlin source code
- Creates build.gradle.kts
- Adds TODO comments for manual review
- Uses common utilities (DateParser, StatusParser, etc.)

**Note:** The converter creates a skeleton that needs manual review:
- Selectors need to be verified against the actual website
- Some logic may need manual conversion
- Test thoroughly before using

### 3. batch-convert.sh (Coming Soon)

Batch convert multiple lnreader plugins at once.

## Requirements

- Python 3.7+
- No additional dependencies required

## After Creating an Extension

1. **Update Selectors**: Review all TODO comments and update CSS selectors
2. **Add Icon**: Add a 96x96px icon to `main/res/mipmap-*` folders
3. **Test**: Compile and test in Android Studio
4. **Verify**:
   - Search functionality
   - Book listings
   - Book details
   - Chapter list
   - Chapter content
   - Image loading

## Tips

### Finding Selectors

1. Open the website in a browser
2. Right-click on the element you want to select
3. Choose "Inspect" or "Inspect Element"
4. Look at the HTML structure
5. Use the element's class, id, or tag name as the selector

### Common Selectors

- Book list: `.novel-item`, `.book-item`, `.fiction-list-item`
- Title: `.title`, `h3`, `.novel-title`
- Cover: `img`, `.cover img`, `.thumbnail`
- Chapters: `.chapter-list li`, `ul.chapters li`
- Content: `.chapter-content p`, `.text-content p`

### Using Common Utilities

The generated code uses common utilities:
- `DateParser.parseRelativeOrAbsoluteDate()` for dates
- `StatusParser.parseStatus()` for status
- `ImageUrlHelper.normalizeUrl()` for images
- `ErrorHandler.safeRequest()` for error handling
- `SelectorConstants.*` for common selectors

## Troubleshooting

### "Could not extract metadata"
- Check if the JS/TS file has the correct structure
- Ensure it implements `Plugin.PluginBase`
- Verify id, name, and site fields exist

### "Selectors not working"
- Inspect the actual website HTML
- Update selectors in the generated code
- Test with different pages

### "Build errors"
- Ensure the common module is included
- Check package names match directory structure
- Verify all imports are correct

## Contributing

If you create a useful script, please contribute it back to the project!
