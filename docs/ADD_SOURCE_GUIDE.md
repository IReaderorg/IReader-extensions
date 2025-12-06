# Adding a New Source

## Quick Start

```bash
python scripts/add-source.py
```

Answer 4 questions:
1. Source name (e.g., `NovelFull`)
2. Website URL (e.g., `https://novelfull.com`)
3. Language code (e.g., `en`)
4. Is it a Madara site? (y/n)

## What Gets Generated

### Madara Sites (zero-code)
```kotlin
@MadaraSource(
    name = "MySite",
    baseUrl = "https://mysite.com",
    lang = "en",
    id = 12345L
)
object MySiteConfig
```
That's it! KSP generates everything else.

### Regular Sites (with KSP annotations)
```kotlin
@Extension
@AutoSourceId(seed = "MySite")
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular"])
@GenerateCommands(detailFetch = true, chapterFetch = true, contentFetch = true)
abstract class MySite(deps: Dependencies) : SourceFactory(deps = deps) {
    // Just update the CSS selectors
}
```

## Finding CSS Selectors

1. Open the website in Chrome/Firefox
2. Right-click any element → "Inspect"
3. Find the class name or tag
4. Test: `document.querySelector(".your-selector")`

### Common Selectors

| Element | Try These |
|---------|-----------|
| Novel card | `.novel-item`, `.book-item`, `.post` |
| Title | `h1`, `.title`, `h3.name` |
| Cover | `img`, `.cover img` |
| Chapters | `.chapter-list li`, `ul.chapters a` |
| Content | `.chapter-content p`, `.text p` |

## Build & Test

```bash
./gradlew :sources:en:mysource:assembleDebug
```
