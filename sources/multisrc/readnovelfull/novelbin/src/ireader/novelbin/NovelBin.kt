package ireader.novelbin

import ireader.core.source.Dependencies
import ireader.readnovelfull.ReadNovelFullOptions
import ireader.readnovelfull.ReadNovelFullScraper
import tachiyomix.annotations.Extension

@Extension
abstract class NovelBin(deps: Dependencies) : ReadNovelFullScraper(
    deps = deps,
    sourceId = 1005,
    key = "https://novelbin.com",
    sourceName = "Novel Bin",
    language = "en",
    options = ReadNovelFullOptions(
        latestPage = "sort/latest",
        searchPage = "search"
    )
)
