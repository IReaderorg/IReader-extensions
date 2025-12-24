# Source Health Check System

Automated system for validating and repairing IReader extension selectors.

## Overview

This system helps maintain IReader extensions by:
1. **Snapshot Generation**: Creates JSON snapshots of working selectors and expected results
2. **Validation**: Tests selectors against live websites
3. **AI Repair**: Suggests new selectors when old ones break
4. **Auto-Fix**: Applies fixes and runs integration tests

## Quick Start

```bash
# Install dependencies
pip install -r scripts/source-health/requirements.txt

# Validate a single source
python scripts/source-health/validate.py --source fanmtl

# Validate all sources
python scripts/source-health/validate.py --all

# Generate snapshot for a source (after manual verification)
python scripts/source-health/snapshot.py --source fanmtl --verify

# Repair broken selectors with AI
python scripts/source-health/repair.py --source fanmtl --auto-fix
```

## Directory Structure

```
scripts/source-health/
├── README.md              # This file
├── requirements.txt       # Python dependencies
├── schema.json           # JSON schema for snapshots
├── validate.py           # Main validation script
├── snapshot.py           # Snapshot generation
├── repair.py             # AI-powered selector repair
├── utils/
│   ├── __init__.py
│   ├── selector_parser.py    # Parse selectors from Kotlin sources
│   ├── html_fetcher.py       # Fetch HTML with Playwright
│   └── ai_repair.py          # AI selector suggestion
└── snapshots/            # Generated snapshots
    └── en/
        ├── fanmtl.json
        └── allnovelfull.json
```

## Snapshot Schema

Each snapshot contains:
- Source metadata (name, baseUrl, lang)
- Test URLs (novel page, chapter page, search)
- Selector mappings with expected results
- Last verified timestamp

Example:
```json
{
  "source": "fanmtl",
  "baseUrl": "https://www.fanmtl.com",
  "lang": "en",
  "lastVerified": "2024-12-24T10:00:00Z",
  "testUrls": {
    "novel": "https://www.fanmtl.com/novel/6954065.html",
    "chapter": "https://www.fanmtl.com/novel/6954065_1.html",
    "search": "https://www.fanmtl.com/e/search/index.php?keyboard=douluo"
  },
  "selectors": {
    "detail": {
      "title": {
        "selector": "h1",
        "expected": "Douluo Continent: Me! Qiu'er's fiancé"
      },
      "cover": {
        "selector": "figure img",
        "attribute": "src",
        "expectedPattern": ".*\\.jpg$"
      }
    }
  }
}
```

## Workflow

### 1. Create Snapshot (One-time setup)

```bash
# Interactive mode - guides you through verification
python scripts/source-health/snapshot.py --source fanmtl --interactive

# Or provide test URLs directly
python scripts/source-health/snapshot.py --source fanmtl \
  --novel-url "https://www.fanmtl.com/novel/6954065.html" \
  --chapter-url "https://www.fanmtl.com/novel/6954065_1.html"
```

### 2. Validate Sources

```bash
# Single source
python scripts/source-health/validate.py --source fanmtl

# All sources with snapshots
python scripts/source-health/validate.py --all

# Output JSON report
python scripts/source-health/validate.py --all --output report.json
```

### 3. Repair Broken Selectors

```bash
# Show suggestions only
python scripts/source-health/repair.py --source fanmtl

# Auto-apply fixes (creates backup)
python scripts/source-health/repair.py --source fanmtl --auto-fix

# Run tests after fix
python scripts/source-health/repair.py --source fanmtl --auto-fix --test
```

## CI/CD Integration

Add to `.github/workflows/source-health.yml`:

```yaml
name: Source Health Check
on:
  schedule:
    - cron: '0 0 * * 0'  # Weekly on Sunday
  workflow_dispatch:

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - name: Install dependencies
        run: pip install -r scripts/source-health/requirements.txt
      - name: Install Playwright
        run: playwright install chromium
      - name: Validate sources
        run: python scripts/source-health/validate.py --all --output health-report.json
      - name: Upload report
        uses: actions/upload-artifact@v4
        with:
          name: health-report
          path: health-report.json
```

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `GEMINI_API_KEY` | Google Gemini API key for AI repair | For repair only |
| `OPENAI_API_KEY` | OpenAI API key (alternative) | For repair only |
| `ANTHROPIC_API_KEY` | Anthropic API key (alternative) | For repair only |

## Token-Efficient AI Repair

The AI repair module minimizes token usage by:
1. Sending only the broken selector and small HTML context (~500 chars around expected location)
2. Using structured JSON output format
3. Caching HTML to avoid re-fetching
4. Batching multiple broken selectors per source

Typical token usage: ~200-500 tokens per broken selector.
