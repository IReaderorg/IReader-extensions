---
name: ireader-extension-creator
description: Create new IReader novel/manga source extensions — guides through SourceFactory setup, KSP annotations, HTML parsing, build registration, and mandatory testing
---

# IReader Extension Creator

Helps create new source extensions for the IReader app (novel/manga reader).

## When to Use

- Creating a new novel/manga source extension
- Adding a source to an existing multisrc template (Madara, etc.)
- Registering an extension in build.gradle.kts
- Understanding the SourceFactory, HttpSource, or fetcher APIs

## Quick Start

```
1. Test website with cloudscraper/curl (NEVER skip this)
2. Extract CSS selectors from actual HTML
3. Choose source type: Madara → @MadaraSource, else → SourceFactory with KSP
4. Write source using templates from references/templates.md
5. Add KSP annotations (see references/ksp-annotations.md)
6. Add test annotations (REQUIRED, not optional)
7. Build: ./gradlew :extensions:individual:{lang}:{name}:assemble{Lang}Debug
```

## Decision Tree

```
Is the site a Madara/WordPress theme?
│
├── YES → Use @MadaraSource (ZERO CODE NEEDED!)
│         Signs:
│         - URL pattern: /novel/novel-name/chapter-1/
│         - Has /wp-admin/ page
│         - Similar layout to BoxNovel, NovelFull
│
└── NO → Is it based on another known theme?
         │
         ├── YES → Use @ThemeSource (minimal code)
         │
         └── NO → Use SourceFactory with KSP annotations
                  ALWAYS prefer KSP annotations over manual code!
```

## References

| Document | Description |
|----------|-------------|
| `references/ksp-annotations.md` | All KSP annotations, imports, and usage |
| `references/testing-guide.md` | Mandatory testing workflow with cloudscraper/curl |
| `references/templates.md` | Complete source templates (A, B, C, D) |
| `references/patterns.md` | Common patterns and CSS selectors |

## Constraints

- **NEVER write selectors without validating them against the actual website**
- **ALWAYS test with cloudscraper or curl before writing code**
- Use `@AutoSourceId` for all new sources
- Use `@GenerateTests` and `@TestFixture` for all sources
- Use `kotlinx.serialization.json.*` for JSON parsing (dynamic, NOT @Serializable)
- Use `com.fleeksoft.ksoup` for HTML parsing (not jsoup directly)
- Use `io.ktor` for HTTP requests (not OkHttp/Retrofit directly)
- Package name must match folder path: `ireader.{lowercase_name}`
- Class must be `abstract` and extend `SourceFactory`
- Must have `@Extension` annotation

## Validation Checklist

- [ ] Tested website accessibility with cloudscraper/curl
- [ ] All CSS selectors verified against actual HTML
- [ ] Used `@AutoSourceId` instead of hardcoding ID
- [ ] Used `@GenerateFilters` and `@GenerateCommands`
- [ ] Used `@GenerateTests` with `@TestFixture` and `@TestExpectations()`
- [ ] Package name is `ireader.{lowercase_name}`
- [ ] Directory matches package: `ireader/{lowercase_name}/`
- [ ] Class is `abstract` and extends `SourceFactory`
- [ ] Has `@Extension` annotation
- [ ] build.gradle.kts has correct name and lang
- [ ] Build succeeds: `./gradlew :extensions:individual:{lang}:{name}:assemble{Lang}Debug`

## Reference Files Location

```
IReader-extensions/
  annotations/src/commonMain/kotlin/tachiyomix/annotations/
    Extension.kt                    # @Extension
    MadaraSource.kt                 # @MadaraSource
    SourceAnnotations.kt            # @AutoSourceId, @GenerateFilters, @GenerateCommands
    SourceMeta.kt                   # @SourceMeta
    TestAnnotations.kt              # @GenerateTests, @SkipTests, @TestFixture, @TestExpectations
    ApiAnnotations.kt               # @ExploreFetcher, @DetailSelectors, etc.
  docs/
    SELECTOR_HELPER_GUIDE.md        # Browser extension for finding CSS selectors
    KSP_ANNOTATIONS_REFERENCE.md    # Full KSP annotation reference
    AI_SOURCE_GENERATOR_PROMPT.md   # Detailed prompt for AI source generation
```
