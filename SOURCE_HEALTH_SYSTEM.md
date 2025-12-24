# Source Health Check System

A comprehensive system for validating and automatically repairing IReader extension selectors.

## Overview

When website structures change, extension selectors break. This system:

1. **Detects** broken selectors by comparing against known-good snapshots
2. **Suggests** fixes using AI (Gemini/OpenAI/Anthropic)
3. **Applies** fixes automatically with backup
4. **Validates** fixes with integration tests

## Quick Start

```bash
# Install dependencies
pip install -r scripts/source-health/requirements.txt

# Validate a source
python scripts/source-health/validate.py --source fanmtl --verbose

# Get AI repair suggestions
python scripts/source-health/repair.py --source fanmtl

# Auto-fix with backup
python scripts/source-health/repair.py --source fanmtl --auto-fix --test
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    SOURCE HEALTH SYSTEM                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. SNAPSHOT (JSON)                                             │
│     - Selector → Expected result mappings                       │
│     - Test URLs for each page type                              │
│     - URL validation patterns                                   │
│                                                                 │
│  2. VALIDATION (Python)                                         │
│     - Fetch live HTML (with optional JS rendering)              │
│     - Run selectors against HTML                                │
│     - Compare with snapshot expectations                        │
│     - Report: healthy / degraded / broken                       │
│                                                                 │
│  3. AI REPAIR (Python + API)                                    │
│     - Extract HTML context around broken selector               │
│     - Send minimal prompt to AI (200-500 tokens)                │
│     - Get suggested new selector                                │
│     - Apply with confidence threshold                           │
│                                                                 │
│  4. INTEGRATION (KSP + CI/CD)                                   │
│     - @TestFixture annotation for test URLs                     │
│     - @TestExpectations for validation rules                    │
│     - GitHub Actions for weekly health checks                   │
│     - Auto-create issues for broken sources                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Files

```
scripts/source-health/
├── README.md              # Detailed documentation
├── requirements.txt       # Python dependencies
├── schema.json           # JSON schema for snapshots
├── validate.py           # Main validation script
├── snapshot.py           # Snapshot generation
├── repair.py             # AI-powered repair
├── test_system.py        # Quick test script
├── utils/
│   ├── selector_parser.py    # Parse Kotlin sources
│   ├── html_fetcher.py       # Fetch HTML (with Playwright)
│   └── ai_repair.py          # AI selector suggestions
└── snapshots/
    └── en/
        └── fanmtl.json       # Example snapshot
```

## KSP Annotations

Add these to your sources for automated validation:

```kotlin
@Extension
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "test",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://example.com/novel/123",
    chapterUrl = "https://example.com/novel/123/chapter-1",
    expectedTitle = "My Novel Title",
    expectedAuthor = "Author Name"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 50,
    supportsPagination = true
)
@UrlValidation(
    novelPattern = "^https://example\\.com/novel/\\d+$",
    chapterPattern = "^https://example\\.com/novel/\\d+/chapter-\\d+$"
)
abstract class MySource(deps: Dependencies) : SourceFactory(deps)
```

## Workflow

### 1. Create Snapshot (One-time)

```bash
# Interactive mode
python scripts/source-health/snapshot.py --source fanmtl --interactive

# Or with URLs
python scripts/source-health/snapshot.py --source fanmtl \
  --novel-url "https://example.com/novel/123" \
  --chapter-url "https://example.com/novel/123/chapter-1" \
  --verify
```

### 2. Validate Regularly

```bash
# Single source
python scripts/source-health/validate.py --source fanmtl

# All sources
python scripts/source-health/validate.py --all --output report.json
```

### 3. Repair When Broken

```bash
# Get suggestions
python scripts/source-health/repair.py --source fanmtl

# Auto-apply (creates backup)
python scripts/source-health/repair.py --source fanmtl --auto-fix

# Auto-apply and test
python scripts/source-health/repair.py --source fanmtl --auto-fix --test
```

## CI/CD Integration

The system includes a GitHub Actions workflow (`.github/workflows/source-health.yml`) that:

- Runs weekly on Sunday at midnight UTC
- Validates all sources with snapshots
- Creates/updates issues for broken sources
- Uploads health reports as artifacts

### Manual Trigger

You can manually trigger the workflow from GitHub Actions with:
- Specific source name (optional)
- JavaScript rendering option

## AI Token Efficiency

The repair system minimizes API costs by:

1. **Minimal context**: Only sends ~500 chars of HTML around the broken selector
2. **Structured output**: Requests JSON response for easy parsing
3. **Caching**: Caches HTML to avoid re-fetching
4. **Confidence threshold**: Only applies fixes with >50% confidence

Typical usage: **200-500 tokens per broken selector**

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `GEMINI_API_KEY` | Google Gemini API key | For AI repair |
| `OPENAI_API_KEY` | OpenAI API key (alternative) | For AI repair |
| `ANTHROPIC_API_KEY` | Anthropic API key (alternative) | For AI repair |

## Snapshot Schema

See `scripts/source-health/schema.json` for the full JSON schema.

Key fields:
- `testUrls`: URLs for testing each page type
- `selectors`: Selector definitions with expected values
- `urlValidation`: Regex patterns for URL validation
- `metadata`: Source-specific settings (JS required, rate limits, etc.)

## Contributing

1. Add `@TestFixture` to your sources
2. Generate snapshots: `python scripts/source-health/snapshot.py --source <name> --verify`
3. Commit snapshots to `scripts/source-health/snapshots/`
4. The CI will automatically validate weekly
