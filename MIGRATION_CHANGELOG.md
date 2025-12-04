# IReader Extensions KMP Migration Changelog

This document summarizes all changes made during the KMP (Kotlin Multiplatform) migration.

## Overview

The migration enables IReader extensions to support both Android/Desktop (JVM) and iOS (JS) platforms.

---

## Phase 1: Dependencies

### `gradle/libs.versions.toml`
- Added `ksoup = "0.2.5"` - KMP-compatible HTML parser (replaces Jsoup)
- Added `kotlinx-datetime = "0.7.1"` - KMP date/time library
- Added `ktor-client-js` - Ktor HTTP client for JS target
- Removed JVM-only dependencies from common bundles
- Added new bundles: `kmp-common`, `kmp-jvm`, `kmp-js`, `kmp-android`

---

## Phase 2: Common Module

### `common/build.gradle.kts`
- Converted from `kotlin("jvm")` to `kotlin("multiplatform")`
- Added JVM and JS targets
- Updated dependencies to use KMP-compatible libraries

### `common/src/commonMain/kotlin/`
Migrated all utility files to KMP:
- `DateParser.kt` - Uses `kotlinx.datetime` instead of `java.text.SimpleDateFormat`
- `HtmlCleaner.kt` - Uses Ksoup instead of Jsoup
- `RateLimiter.kt` - Uses `kotlinx.datetime.Clock`
- `UrlBuilder.kt` - Custom URL encoding (no `java.net.URLEncoder`)
- `ImageUrlHelper.kt` - Uses Ksoup's `Element` type
- `ErrorHandler.kt` - Pure Kotlin (no changes needed)
- `StatusParser.kt` - Pure Kotlin (no changes needed)
- `SelectorConstants.kt` - Pure Kotlin (no changes needed)

---

## Phase 3: Build System

### `buildSrc/src/main/kotlin/extension-setup.gradle.kts`
- Added Ksoup and kotlinx-datetime dependencies
- Removed JVM-only dependencies (okhttp, jsoup, ktor-gson, ktor-jackson)

### `buildSrc/src/main/kotlin/Extension.kt`
- Added `kmpEnabled` flag for future KMP source support

### `multisrc/build.gradle.kts`
- Updated to use KMP-compatible dependencies

---

## Phase 4: Annotations Module

### `annotations/build.gradle.kts`
- Converted to multiplatform (JVM + JS targets)

### New Annotations Created:
| File | Annotations |
|------|-------------|
| `Extension.kt` | `@Extension` |
| `MadaraSource.kt` | `@MadaraSource`, `@ThemeSource`, `@Selector`, `@DateFormat` |
| `SourceMeta.kt` | `@SourceMeta`, `@SourceFilters`, `@ExploreFetcher`, `@DetailSelectors`, `@ChapterSelectors`, `@ContentSelectors` |
| `ApiAnnotations.kt` | `@ApiEndpoint`, `@SourceDeepLink`, `@RateLimit`, `@CustomHeader`, `@CloudflareConfig`, `@RequiresAuth`, `@Pagination` |
| `TestAnnotations.kt` | `@GenerateTests`, `@TestFixture`, `@SkipTests`, `@TestExpectations` |

---

## Phase 5: KSP Processors

### New Processors:
| Processor | Purpose |
|-----------|---------|
| `ThemeSourceProcessor` | Generate theme-based sources from annotations |
| `SourceIndexProcessor` | Generate repository index JSON |
| `SourceFactoryProcessor` | Generate filters and fetchers |
| `SelectorValidatorProcessor` | Validate CSS selectors at compile time |
| `TestGeneratorProcessor` | Auto-generate test cases |
| `HttpClientProcessor` | Generate HTTP request helpers |
| `DeepLinkProcessor` | Generate deep link handlers |

---

## Phase 6: CI/CD Changes

### `.github/workflows/build_push.yml`
- Changed deployment branch from `repo` to `repov2`
- Legacy `repo` branch preserved for older IReader versions

### `.github/workflows/build_pull_request.yml`
- Changed deployment branch from `repo` to `repov2`

---

## Phase 7: Migration Scripts

### `scripts/migrate-source-to-kmp.ps1` (PowerShell)
Features:
- Jsoup → Ksoup import conversion
- Date parsing migration
- Java collections → Kotlin stdlib
- URL encoding migration
- Migration report generation
- Dry-run mode

### `scripts/MigrateToKmp.kts` (Kotlin Script)
Cross-platform version with same features.

Usage:
```bash
# PowerShell
.\scripts\migrate-source-to-kmp.ps1 -All -Report

# Kotlin
kotlin scripts/MigrateToKmp.kts --all --report
```

---

## Documentation

### New Files:
- `docs/KSP_ANNOTATIONS.md` - Complete guide to all KSP annotations
- `MIGRATION_CHANGELOG.md` - This file

### Updated Files:
- `IReader-Extensions-Migration-Plan.md` - Added script documentation and branch info

---

## Example Sources

### `sources/en/example-annotated/`
Demonstrates all KSP annotations including:
- `@SourceMeta`, `@SourceFilters`
- `@ExploreFetcher` (multiple)
- `@DetailSelectors`, `@ChapterSelectors`, `@ContentSelectors`
- `@SourceDeepLink`, `@RateLimit`, `@CustomHeader`
- `@ApiEndpoint`, `@Pagination`
- `@GenerateTests`, `@TestFixture`, `@TestExpectations`

### `sources/en/example-madara/`
Demonstrates `@MadaraSource` annotation for theme-based sources.

---

## Breaking Changes

1. **Repository Branch**: Extensions now deploy to `repov2` instead of `repo`
2. **Dependencies**: Jsoup replaced with Ksoup in common module
3. **Date Parsing**: `java.text.SimpleDateFormat` replaced with `kotlinx.datetime`

---

## Phase 8: Source Migration (Completed)

### Automated Migration Results
- **Total Sources Processed**: 75
- **Successfully Migrated**: 51+ sources
- **Skipped (already compatible)**: 20+ sources

### Key Changes Applied:
1. **Jsoup → Ksoup**: All `org.jsoup.*` imports converted to `com.fleeksoft.ksoup.*`
2. **Date Parsing**: All `Calendar.getInstance()` and `SimpleDateFormat` replaced with `DateParser` utility
3. **Madara Multisrc**: Base class and all implementations migrated to Ksoup

### Sources with Manual Fixes:
- `comrademao`, `realwebnovel`, `webnovelcom` - Date parsing
- `wuxiaworldsite`, `wuxiaworldsiteco` - Date parsing
- `novelowlcom`, `readmtl`, `kissnovellove` - Date parsing
- `teamxnovel` (Arabic) - Date parsing
- `lightnovelpub`, `lightnovelsme` - SimpleDateFormat removal
- `madara` multisrc base - Full Ksoup migration

---

## Migration Checklist for Source Authors

- [x] Update Jsoup imports to Ksoup
- [x] Replace `Jsoup.parse()` with `Ksoup.parse()`
- [x] Replace `System.currentTimeMillis()` with `Clock.System.now().toEpochMilliseconds()`
- [x] Replace `SimpleDateFormat` with `DateParser` utility
- [x] Replace `Calendar.getInstance()` with `DateParser.parseRelativeOrAbsoluteDate()`
- [x] Replace `java.util.*` collections with Kotlin stdlib
- [ ] Test source compilation
- [ ] Test source functionality

---

## Version Compatibility

| Component | Old Version | New Version |
|-----------|-------------|-------------|
| Ksoup | N/A | 0.2.5 |
| kotlinx-datetime | N/A | 0.7.1 |
| Ktor | 3.3.2 | 3.3.2 |
| Kotlin | 2.2.21 | 2.2.21 |
