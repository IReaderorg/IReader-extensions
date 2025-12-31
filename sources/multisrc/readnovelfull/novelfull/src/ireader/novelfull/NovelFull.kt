package ireader.novelfull

import ireader.core.source.Dependencies
import ireader.readnovelfull.ReadNovelFullOptions
import ireader.readnovelfull.ReadNovelFullScraper
import tachiyomix.annotations.Extension

@Extension
abstract class NovelFull(deps: Dependencies) : ReadNovelFullScraper(
    deps = deps,
    sourceId = 1003,
    key = "https://novelfull.com",
    sourceName = "NovelFull",
    language = "en",
    options = ReadNovelFullOptions(
        latestPage = "latest-release-novel",
        searchPage = "search",
        chapterListing = "ajax-chapter-option",
        chapterParam = "novelId"
    )
)
