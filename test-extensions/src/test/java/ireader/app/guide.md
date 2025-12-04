# 🧪 Test Extensions Module

This module provides **manual/exploratory testing** for IReader extensions.

## When to Use This vs KSP @GenerateTests

| Use Case | Use This Module | Use @GenerateTests |
|----------|-----------------|-------------------|
| Debugging a specific source | ✅ | |
| Quick manual testing | ✅ | |
| CI/automated testing | | ✅ |
| Per-source test files | | ✅ |
| Shared mock infrastructure | ✅ | |
| Custom test scenarios | ✅ | |

## Quick Start

### Step 1: Add your extension dependency

Edit `test-extensions/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // ADD YOUR EXTENSION HERE:
    implementation(project(":extensions:individual:en:mylovenovel"))
    // or
    implementation(project(":extensions:v5:en:novelbuddy"))
}
```

### Step 2: Update the Constants file

Edit `test-extensions/src/main/java/ireader/constants/Constants.kt`:

```kotlin
object Constants {
    fun getExtension(deps: Dependencies): HttpSource {
        return MySource(deps)  // Your source class
    }
}
```

### Step 3: Run tests

```bash
./gradlew :test-extensions:test
```

## ⚠️ Important: HTTP Client Issue

**DO NOT use OkHttp engine in tests!** It causes issues with unit testing.

```kotlin
// ❌ BAD - Will fail in tests
override val client: HttpClient
    get() = HttpClient(OkHttp) {
        engine {
            preconfigured = deps.httpClients.default.okhttp
        }
    }

// ✅ GOOD - Works in tests
override val client: HttpClient
    get() = HttpClient(CIO)
```

## Available Test Classes

| Test Class | What it Tests |
|------------|---------------|
| `BookListChecker` | Novel listings (latest, popular) |
| `ChapterChecker` | Chapter list parsing |
| `ContentChecker` | Chapter content parsing |
| `InfoChecker` | Novel details parsing |

## Mock Components

The module provides mock implementations:

- `FakeHttpClients` - Mock HTTP client for testing
- `FakePreferencesStore` - Mock preferences storage

## Alternative: KSP Auto-Generated Tests

For automated CI testing, use the `@GenerateTests` annotation instead:

```kotlin
@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "test"
)
@TestFixture(
    novelUrl = "https://example.com/novel/test/",
    expectedTitle = "Test Novel"
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps)
```

This generates test files automatically during build.
See: `docs/KSP_ANNOTATIONS_REFERENCE.md` for details.
