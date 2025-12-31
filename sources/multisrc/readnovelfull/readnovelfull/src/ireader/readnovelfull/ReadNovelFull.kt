package ireader.readnovelfull

import ireader.core.source.Dependencies
import tachiyomix.annotations.Extension

@Extension
abstract class ReadNovelFull(deps: Dependencies) : ReadNovelFullScraper(
    deps = deps,
    sourceId = 1001,
    key = "https://readnovelfull.com",
    sourceName = "ReadNovelFull",
    language = "en",
    options = ReadNovelFullOptions(
        latestPage = "novel-list/latest-release-novel",
        searchPage = "novel-list/search"
    )
)
