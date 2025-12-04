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

Define scraping without code:

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
abstract class MySource(deps: Dependencies) : SourceFactory(deps)
```

---

## Generate Filters

Auto-generate filter UI:

```kotlin
@Extension
@GenerateFilters(
    title = true,
    sort = true,
    sortOptions = ["Latest", "Popular", "Rating"]
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    override fun getFilters() = mysourceFilters()  // Generated!
}
```

---

## All Annotations at a Glance

| Annotation | Purpose |
|------------|---------|
| `@Extension` | **Required** - Mark as IReader source |
| `@AutoSourceId` | Auto-generate source ID |
| `@MadaraSource` | Zero-code Madara source |
| `@ThemeSource` | Zero-code theme source |
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
