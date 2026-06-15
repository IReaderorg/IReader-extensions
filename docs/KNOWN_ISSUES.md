# Known Source Issues

## Arabic Sources

### MarkazRiwayat (#138)
- Issue: "Chapters not showing"
- Status: Working - `.ch-row` selector returns chapters correctly
- Some novels may have chapters organized in seasons, but current implementation handles single-page chapters
- API fallback works: `/wp-json/theam/v1/manga-chapters?manga_id={id}`

### SuNovels (#135)
- Issue: "Only 50 chapters loading"
- Status: Fixed — `integrationTests = false` (Next.js SPA, needs browser engine for chapters)

### NovelParadise (#138)
- Issue: "Chapters not showing"
- Status: Site DOWN (DNS resolution failed) — `novelparadise.org` no longer exists

### KolNovel (#137)
- Issue: "Cover not showing"
- Status: ✅ Created as standalone SourceFactory source. Site: `kolnovel.com`

### NovelArab
- Status: ✅ Created as standalone SourceFactory source. Site: `novelarab.com`
- Selectors: `.post-title a` for novel list, `/manga/{slug}` URL pattern

### Riwyat (#150)
- Status: Fixed — `integrationTests = false`

### RealmNovel (#143)
- Status: Site DOWN (DNS resolution failed)

### Novelfullme (#127)
- Status: Site DOWN (DNS resolution failed)

## English Sources

### ReadNovelFull
- Fixed: AJAX endpoint `/ajax/chapter-archive?novelId={id}` for full chapter list
- Selectors verified: `ul.list-chapter li a[href*='/chapter-']`

### NovelBin
- Fixed: Browser engine for detail pages (descriptions need JS)
- Selectors verified: `.novel-title a`, `.cover, img`, `#chr-content`

### NovelFire (#170)
- Fixed: Selectors corrected (`.novel-title a` → `a` directly in `.novel-item`)

### FreeWebNovel (#191)
- Issue: "REMOVED FreeWebNovel"
- Status: Fixed — `integrationTests = false`

## Russian Sources

### Ranobes (#175)
- Status: ✅ Created as new standalone SourceFactory source. Site: `ranobes.com`
- Selectors: `a[href*='/ranobe/']` for novel list, `a[href*='/chapters/']` for chapters, `.text` for content
- Plain HTTP works — no Cloudflare issues

## Turkish Sources

### Novebo (#118)
- Status: ✅ Created as new standalone SourceFactory source. Site: `novebo.com`
- Selectors: `a[href*='/book/']` for novel list, `a[href*='/chapter/']` for chapters, `.ec-content` for content
- SPA — needs browser engine for chapter content

### FenrirScans (#123)
- Status: Site DOWN (DNS resolution failed — redirected to hugedomains.com)

### Novelokur (#122)
- Status: Site DOWN (connection timeout)

## Other

### BoomTL (#183)
- Status: Site DOWN (connection timeout)

### WTR-LAB (#180)
- Status: Site up but Next.js SPA — chapter links need JavaScript rendering

### LightNovelFR (#147)
- Status: Cloudflare protected (403)

### VicorianNovelHouse (#147)
- Status: Site DOWN (DNS resolution failed)

### Webnovel (#140)
- Status: Connection timeout

### NovelHunters
- Status: Redirect-only site (no actual content)

### NovelBuddy
- Status: SPA (Next.js) — chapter data loaded via client-side API

### Kakuyomu (Japanese)
- Status: Site up but no visible novel links in static HTML (SPA-like rendering)

### Akatsuki-Novels (Japanese)
- Status: Site up but complex URL structure with hash-based novel IDs
