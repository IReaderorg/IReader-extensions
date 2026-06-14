# Novelshub

**IReader Extension for Novelshub (NovelDex)**

## Information
- **Source**: https://novelshub.org
- **Version**: 1.0.0
- **Type**: API-based (JSON API)
- **Language**: English
- **NSFW**: Yes

## Features
- Browse popular and latest novels
- Search for novels by title
- View novel details (description, author, genres)
- Read chapter lists
- Read chapter content

## API Endpoints Used
- `/api/series` - List/search series with pagination
- Series detail pages via HTML scraping
- Chapter content via HTML scraping

## Notes
- This extension uses the site's JSON API for listing/searching
- Chapter content is fetched from HTML pages
- Only free/unlocked chapters are displayed
- The site rebranded from Novelshub to NovelDex but the API remains the same

## Build Status
- ✅ Compilation successful
- ✅ Unit tests passed
- ✅ Android instrumentation tests passed
- ✅ Lint checks passed
