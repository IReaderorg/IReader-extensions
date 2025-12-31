package ireader.boxnovel

import ireader.core.source.Dependencies
import ireader.readnovelfull.ReadNovelFullOptions
import ireader.readnovelfull.ReadNovelFullScraper
import tachiyomix.annotations.Extension

@Extension
abstract class BoxNovel(deps: Dependencies) : ReadNovelFullScraper(
    deps = deps,
    sourceId = 1007,
    key = "https://novlove.com",
    sourceName = "BoxNovel",
    language = "en",
    options = ReadNovelFullOptions(
        latestPage = "sort/nov-love-daily-update",
        searchPage = "search"
    )
)
