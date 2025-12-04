# 🎯 KSP Boilerplate Reduction Guide

> **New to this?** Start with [KSP_QUICK_REFERENCE.md](../KSP_QUICK_REFERENCE.md) for a 2-minute intro!

This guide explains how KSP (Kotlin Symbol Processing) helps reduce repetitive code in IReader extensions.

---

## 🆔 Feature 1: Automatic Source IDs

**The Problem:** Manually managing source IDs is error-prone and tedious.

**The Solution:** Add `@AutoSourceId` and forget about IDs forever!

### Basic Usage (Recommended)

```kotlin
@Extension
@AutoSourceId  // ← Just add this!
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    override val name = "My Source"
    override val lang = "en"
    // No need to override id - it's handled automatically!
}
```

### How IDs Are Generated

The ID is a hash of: `"sourcename/lang/version"`

| Source Name | Lang | Version | Generated ID |
|-------------|------|---------|--------------|
| My Source   | en   | 1       | 1234567890... |
| My Source   | es   | 1       | 9876543210... |
| My Source   | en   | 2       | 5555555555... |

**Same inputs = Same ID** (always deterministic!)

### Migrating Existing Sources

If you're adding `@AutoSourceId` to an existing source and need to keep the same ID:

```kotlin
// Option 1: Use the old name as seed
@AutoSourceId(seed = "OldSourceName")

// Option 2: Keep using manual ID (no annotation needed)
override val id: Long get() = 12345L
```

### Using the Generated Constant

After building, KSP creates `{ClassName}SourceId.kt`:

```kotlin
object MySourceSourceId {
    const val ID: Long = 1234567890123456789L
    const val NAME: String = "My Source"
    const val LANG: String = "en"
}
```

You can reference it:
```kotlin
override val id: Long get() = MySourceSourceId.ID
```

---

## 📦 Feature 2: Package Name Validation

**The Problem:** Package names sometimes don't match folder structure, causing issues.

**The Solution:** Automatic validation with helpful error messages!

### How It Works

When you build, KSP checks:
- Folder: `sources/en/daonovel/`
- Expected package: `ireader.daonovel`

If there's a mismatch (e.g., `package ireader.dao`), you'll see:

```
⚠️ PACKAGE MISMATCH DETECTED
  Class: DaoNovel
  Current:  ireader.dao
  Expected: ireader.daonovel
  
  Fix: Change "package ireader.dao" to "package ireader.daonovel"
```

### Auto-Fix Script

KSP also generates `fix-packages.kts` that you can run:
```bash
kotlin build/generated/ksp/.../fix-packages.kts
```

---

## 🔍 Feature 3: Filter Generation (Optional)

**The Problem:** Most sources have similar filters (title, sort, etc.).

**The Solution:** Generate them with an annotation!

```kotlin
@Extension
@AutoSourceId
@GenerateFilters(
    title = true,
    sort = true,
    sortOptions = ["Latest", "Popular", "Rating"]
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // Use the generated function:
    override fun getFilters() = mysourceFilters()
}
```

### Available Options

| Option | Type | Description |
|--------|------|-------------|
| `title` | Boolean | Title search (default: true) |
| `author` | Boolean | Author search |
| `sort` | Boolean | Sort dropdown |
| `sortOptions` | Array | Options for sort |
| `genre` | Boolean | Genre filter |
| `genreOptions` | Array | Options for genre |
| `status` | Boolean | Status filter |

**Note:** For complex filters, just write them manually - this is optional!

---

## ⚡ Feature 4: Command Generation (Optional)

**The Problem:** Almost every source has the same commands.

**The Solution:** Generate them!

```kotlin
@Extension
@AutoSourceId
@GenerateCommands(
    detailFetch = true,
    contentFetch = true,
    chapterFetch = true
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    // Use the generated function:
    override fun getCommands() = mysourceCommands()
}
```

---

## 🛠️ Gradle Tasks

### List All Source IDs
```bash
./gradlew listSourceIds
```
Output:
```
[en] (5 sources)
  DaoNovel                       ID: 1234567890123456789
  FreeWebNovel                   ID: 9876543210987654321
  ...
```

### Generate ID for New Source
```bash
./gradlew generateSourceId -PsourceName="My New Source" -PsourceLang="en"
```
Output:
```
Name: My New Source
Lang: en
ID:   5555555555555555555
```

### Check for Collisions
```bash
./gradlew checkSourceIdCollisions
```
Output:
```
✓ No collisions found! All 50 sources have unique IDs.
```

---

## 📋 Complete Example

```kotlin
package ireader.mysource

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import tachiyomix.annotations.*

@Extension
@AutoSourceId
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular"])
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
    
    override val name = "My Source"
    override val lang = "en"
    override val baseUrl = "https://example.com"
    
    // ID is auto-generated!
    // Filters use: override fun getFilters() = mysourceFilters()
    // Commands use: override fun getCommands() = mysourceCommands()
    
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/latest/{page}",
                selector = ".novel-item",
                // ... your selectors
            )
        )
    
    // ... rest of your implementation
}
```

---

## ❓ Troubleshooting

### "Cannot find MySourceSourceId"
Build the project first! KSP generates the file during compilation.

### "Package mismatch" warning
Your package name doesn't match the folder. Check the warning message for the fix.

### "ID collision detected"
Two sources have the same ID. Use `@AutoSourceId(seed = "unique_name")` on one of them.

---

## 📚 Reference

| Annotation | Required? | Purpose |
|------------|-----------|---------|
| `@Extension` | Yes | Marks class as IReader extension |
| `@AutoSourceId` | No | Auto-generates source ID |
| `@GenerateFilters` | No | Generates filter list |
| `@GenerateCommands` | No | Generates command list |
| `@SourceConfig` | No | Advanced: declarative config |
| `@SourceMeta` | No | Metadata for repository index |
