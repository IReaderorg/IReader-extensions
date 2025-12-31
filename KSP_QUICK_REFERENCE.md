# 🚀 KSP Annotations - Quick Reference

## 🎯 Choose Your Path

| I want to... | Use this |
|--------------|----------|
| Create a Madara site source | [@MadaraSource](#zero-code-madara-source) |
| Auto-generate source ID | [@AutoSourceId](#auto-source-id) |
| Define selectors declaratively | [@ExploreFetcher](#declarative-selectors) |
| Generate filters/commands | [@GenerateFilters](#generate-filters) |

---

## Zero-Code Madara Source

For sites using the Madara WordPress theme:

```kotlin
@MadaraSource(
    name = "My Novel Site",
    baseUrl = "https://mynovelsite.com",
    lang = "en",
    id = 12345
)
object MyNovelSiteConfig  // Done! No class body needed!
```

---

## Auto Source ID

Never manually manage IDs again:

```kotlin
@Extension
@AutoSourceId  // ← Just add this!
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // ID is auto-generated from name + lang
}
```

**Migrating?** Keep old ID with: `@AutoSourceId(seed = "OldName")`

---

## Declarative Selectors

Define scraping without code - **all properties are auto-generated!**

```kotlin
@Extension
@ExploreFetcher(
    name = "Latest",
    endpoint = "/novels/page/{page}/",
    selector = ".novel-item",
    nameSelector = ".title",
    linkSelector = "a",
    coverSelector = "img"
)
@DetailSelectors(
    title = "h1.title",
    cover = ".cover img",
    description = ".summary"
)
@ChapterSelectors(
    list = ".chapter-list li",
    name = "a",
    link = "a"
)
@ContentSelectors(
    content = ".chapter-content p"
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // ✅ exploreFetchers, detailFetcher, chapterFetcher, contentFetcher
    // are ALL automatically generated! No manual override needed!
}
```

---

## Generate Filters & Commands

Auto-generate filter and command methods - **no manual override needed!**

```kotlin
@Extension
@GenerateFilters(
    title = true,
    sort = true,
    sortOptions = ["Latest", "Popular", "Rating"]
)
@GenerateCommands(
    detailFetch = true,
    chapterFetch = true,
    contentFetch = true
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // ✅ getFilters() and getCommands() are AUTOMATICALLY generated!
    // No manual override needed!
}
```

The KSP processor generates the method overrides directly in the Extension class.

---

## All Annotations at a Glance

| Annotation | Purpose |
|------------|---------|
| `@Extension` | **Required** - Mark as IReader source |
| `@AutoSourceId` | Auto-generate source ID |
| `@MadaraSource` | Zero-code Madara source |
| `@ThemeSource` | Zero-code theme source |
| `@SkipSource` | **Exclude broken source from repo** |
| `@BrokenSource` | Alias for @SkipSource |
| `@DeprecatedSource` | Mark source as deprecated |
| `@ExploreFetcher` | Define listing endpoints |
| `@DetailSelectors` | Novel page selectors |
| `@ChapterSelectors` | Chapter list selectors |
| `@ContentSelectors` | Chapter content selectors |
| `@SourceFilters` | Generate filter UI |
| `@GenerateFilters` | Generate filter function |
| `@GenerateCommands` | Generate commands function |
| `@RateLimit` | Limit request rate |
| `@CustomHeader` | Add HTTP headers |
| `@CloudflareConfig` | Handle Cloudflare |
| `@SourceDeepLink` | Handle browser URLs |
| `@GenerateTests` | Auto-generate tests |
| `@SourceMeta` | Add metadata |

---

## 🚫 Skip Broken Sources

Exclude non-working sources from the repository output:

```kotlin
@Extension
@SkipSource(
    reason = "Site is down",
    since = "2024-12-01"
)
abstract class BrokenSite(deps: Dependencies) : SourceFactory(deps) {
    // Source code kept for future reference
    // But NOT included in repo output
}
```

Or use the alias:

```kotlin
@Extension
@BrokenSource(reason = "Selectors outdated after site redesign")
abstract class OldSite(deps: Dependencies) : SourceFactory(deps)
```

**What happens:**
- ⚠️ Warning logged during build
- ❌ Source excluded from `index.json`
- ❌ APK not included in repo
- ✅ Code still compiles (for future fixes)

---

## 🛠️ Helpful Commands

```bash
./gradlew listSourceIds           # List all IDs
./gradlew checkSourceIdCollisions # Check for conflicts
./gradlew generateSourceId -PsourceName="Name"  # Generate new ID
```

---

## 📚 Full Documentation

- [KSP Boilerplate Reduction Guide](docs/KSP_BOILERPLATE_REDUCTION.md)
- [KSP Annotations Reference](docs/KSP_ANNOTATIONS_REFERENCE.md)
- [Example Source](sources/en/example-autoid/)
