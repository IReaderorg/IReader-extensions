package ireader.libread

import ireader.core.source.Dependencies
import ireader.readnovelfull.ReadNovelFullOptions
import ireader.readnovelfull.ReadNovelFullScraper
import tachiyomix.annotations.Extension

@Extension
abstract class LibRead(deps: Dependencies) : ReadNovelFullScraper(
    deps = deps,
    sourceId = 1004,
    key = "https://libread.com",
    sourceName = "Lib Read",
    language = "en",
    options = ReadNovelFullOptions(
        latestPage = "sort/latest-novels",
        searchPage = "search",
        searchKey = "searchkey",
        postSearch = true,
        noAjax = true,
        pageAsPath = true
    )
)
