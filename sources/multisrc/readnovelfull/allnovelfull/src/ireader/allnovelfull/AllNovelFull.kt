package ireader.allnovelfull

import ireader.core.source.Dependencies
import ireader.readnovelfull.ReadNovelFullOptions
import ireader.readnovelfull.ReadNovelFullScraper
import tachiyomix.annotations.Extension

@Extension
abstract class AllNovelFull(deps: Dependencies) : ReadNovelFullScraper(
    deps = deps,
    sourceId = 1006,
    key = "https://novgo.net",
    sourceName = "AllNovelFull",
    language = "en",
    options = ReadNovelFullOptions(
        latestPage = "latest-release-novel",
        searchPage = "search",
        chapterListing = "ajax-chapter-option",
        chapterParam = "novelId"
    )
)
