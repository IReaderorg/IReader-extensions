# Testing Guide

## MANDATORY: Test Before Writing Code

**NEVER write selectors without first validating them against the actual website.**

## Testing Tools (Order of Preference)

1. **cloudscraper** (recommended) — bypasses Cloudflare and basic JS challenges
2. **curl** — simple HTTP requests for static sites
3. **Browser DevTools** — for JS-rendered content
4. **Browser fetch via deps.httpClients.browser** — for heavy JS/SPA sites

## Test 1: Website Accessibility

```bash
# Check if site is accessible
curl -s -o /dev/null -w "%{http_code}" "https://example.com"

# Fetch page content
curl -s "https://example.com" | head -100
```

```python
# With cloudscraper (bypasses Cloudflare)
python3 -c "
import cloudscraper
scraper = cloudscraper.create_scraper()
resp = scraper.get('https://example.com')
print(f'Status: {resp.status_code}')
print(f'Content length: {len(resp.text)}')
print(resp.text[:2000])
"
```

## Test 2: Selector Validation

```python
import cloudscraper
from bs4 import BeautifulSoup

scraper = cloudscraper.create_scraper()

# Test listing page
resp = scraper.get('https://example.com/novels')
soup = BeautifulSoup(resp.text, 'html.parser')
items = soup.select('.novel-item')
print(f'Listing items: {len(items)}')
if items:
    title = items[0].select_one('.title')
    link = items[0].select_one('a')
    print(f'  First title: {title.text if title else "NOT FOUND"}')
    print(f'  First link: {link.get("href") if link else "NOT FOUND"}')

# Test detail page
resp = scraper.get('https://example.com/novel/test-novel')
soup = BeautifulSoup(resp.text, 'html.parser')
print(f'Detail title: {soup.select_one("h1.novel-title")}')
print(f'Detail author: {soup.select_one(".author-name")}')

# Test chapter page
resp = scraper.get('https://example.com/novel/test-novel/chapter-1')
soup = BeautifulSoup(resp.text, 'html.parser')
content = soup.select('.chapter-content p')
print(f'Content paragraphs: {len(content)}')

# Test search
resp = scraper.get('https://example.com/search?q=test')
soup = BeautifulSoup(resp.text, 'html.parser')
results = soup.select('.search-result')
print(f'Search results: {len(results)}')
```

## Test 3: Build Verification

```bash
# Format: ./gradlew :extensions:individual:{lang}:{sourcename}:assemble{Lang}Debug
./gradlew :extensions:individual:en:mysource:assembleEnDebug

# Build all sources
./gradlew assembleDebug
```

## Test 4: Run Generated Tests

```bash
# Unit tests
./gradlew :extensions:individual:en:mysource:test

# Integration tests (requires network)
./gradlew :extensions:individual:en:mysource:connectedTest
```

## Test 5: Test Server

```bash
# Build + start server
./gradlew buildAndTest

# Or manually:
./gradlew assembleDebug
./gradlew testServer
```

## Selector Validation Rules

- **Every CSS selector MUST be verified** against actual HTML before writing
- **Never guess selectors** — always extract from real page content
- **Test selectors on multiple pages** — novel list, detail, chapter list, content
- **Handle lazy-loaded images** — check for `data-src` vs `src`
- **Handle Cloudflare** — use cloudscraper or browser engine if needed

## Cloudflare-Protected Sites

```python
# Option 1: cloudscraper
import cloudscraper
scraper = cloudscraper.create_scraper()
resp = scraper.get('https://cf-protected-site.com')

# Option 2: Browser engine in Kotlin
val html = deps.httpClients.browser.fetch(url = url, selector = "h1").responseBody
```

## Common Issues and Solutions

| Issue | Solution |
|-------|----------|
| Status 403 Forbidden | Use cloudscraper or browser engine |
| Empty content | Site uses JS rendering, use browser engine |
| Wrong selectors | Validate with curl/cloudscraper first |
| Missing images | Check `data-src` instead of `src` |
| Dynamic content | Use browser fetch with selector wait |
