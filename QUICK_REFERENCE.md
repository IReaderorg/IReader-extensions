# Quick Reference Card

## Creating Extensions

### Generate Empty Extension
```bash
python scripts/create-empty-source.py <Name> <URL> <lang>
```

### Convert from lnreader
```bash
python scripts/js-to-kotlin-converter.py <file.ts> <lang>
```

### Batch Convert
```bash
# Linux/Mac
./scripts/batch-convert-lnreader.sh [lang]

# Windows
.\scripts\batch-convert-lnreader.ps1 [lang]
```

## Common Utilities

### Date Parsing
```kotlin
DateParser.parseRelativeOrAbsoluteDate("2 hours ago")
DateParser.parseAbsoluteDate("Jan 15, 2024")
DateParser.addCustomFormat("dd-MM-yyyy")
```

### Status Parsing
```kotlin
StatusParser.parseStatus("Ongoing")
StatusParser.addCustomKeywords(MangaInfo.COMPLETED, "finished")
```

### Error Handling
```kotlin
ErrorHandler.safeRequest { client.get(url) }
ErrorHandler.withRetry(config) { attempt -> /* ... */ }
ErrorHandler.categorizeError(exception)
```

### Image URLs
```kotlin
ImageUrlHelper.normalizeUrl(url, baseUrl)
ImageUrlHelper.extractImageUrl(element)
ImageUrlHelper.thumbnailToFullSize(url)
```

### Selectors
```kotlin
SelectorConstants.WPManga.BOOK_LIST
SelectorConstants.Madara.SEARCH_RESULTS
SelectorConstants.NovelTheme.CHAPTER_LIST
```

## Extension Structure

```
sources/<lang>/<name>/
├── build.gradle.kts
├── README.md
└── main/
    └── src/
        └── ireader/
            └── <name>/
                └── <Name>.kt
```

## build.gradle.kts Template

```kotlin
listOf("en").map { lang ->
    Extension(
        name = "SourceName",
        versionCode = 1,
        libVersion = "1",
        lang = lang,
        description = "Description",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}.also(::register)
```

## Extension Template

```kotlin
@Extension
abstract class Source(deps: Dependencies) : ParsedHttpSource(deps) {
    override val name = "Name"
    override val id: Long = 123456L
    override val baseUrl = "https://example.com"
    override val lang = "en"
    
    // Implement required methods
}
```

## Testing Checklist

- [ ] Search works
- [ ] Latest/Popular listings load
- [ ] Book details display
- [ ] Chapter list loads
- [ ] Chapter content displays
- [ ] Images load correctly
- [ ] Date parsing works
- [ ] Status parsing works

## Common Selectors

| Element | Common Selectors |
|---------|-----------------|
| Book List | `.novel-item`, `.book-item` |
| Title | `.title`, `h3`, `.novel-title` |
| Cover | `img`, `.cover img` |
| Chapters | `.chapter-list li` |
| Content | `.chapter-content p` |
| Date | `.date`, `.time`, `time` |
| Status | `.status`, `.novel-status` |

## Language Codes

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

## Useful Commands

```bash
# Build all extensions
./gradlew assembleRelease

# Build specific extension
./gradlew :extensions:individual:en:source:assembleRelease

# Clean build
./gradlew clean

# List all projects
./gradlew projects

# Run detekt
./gradlew detekt

# Run ktlint
./gradlew ktlintCheck
```

## File Locations

- **Scripts**: `scripts/`
- **Common Utils**: `common/src/main/kotlin/ireader/common/utils/`
- **Templates**: `tutorial/`
- **Documentation**: `*.md` files
- **Sources**: `sources/<lang>/<name>/`

## Getting Help

- [Contributing Guide](./tutorial/CONTRIBUTING.md)
- [Extension Template](./tutorial/EXTENSION_TEMPLATE.md)
- [Scripts Guide](./SCRIPTS_GUIDE.md)
- [Common Utils README](./common/README.md)
- [Improvements Doc](./IMPROVEMENTS.md)

## Quick Tips

1. **Always use common utilities** - Don't reinvent the wheel
2. **Test thoroughly** - Check all functionality
3. **Update selectors** - Verify against actual website
4. **Add icons** - 96x96px PNG
5. **Increment version** - When making changes
6. **Follow conventions** - PascalCase for classes, lowercase for packages
7. **Use error handling** - Wrap network calls
8. **Document changes** - Add comments for complex logic

## Common Issues

| Issue | Solution |
|-------|----------|
| Selectors not working | Inspect website HTML, update selectors |
| Images not loading | Use `ImageUrlHelper.extractImageUrl()` |
| Dates showing "Unknown" | Add custom format with `DateParser.addCustomFormat()` |
| Build errors | Check imports, package names, dependencies |
| Extension not appearing | Increment versionCode, rebuild |

## Resources

- [Jsoup Documentation](https://jsoup.org/)
- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Ktor Client](https://ktor.io/docs/client.html)
- [CSS Selectors](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Selectors)
