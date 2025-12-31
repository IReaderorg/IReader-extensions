package ireader.fanmtl

import ireader.core.source.Dependencies
import ireader.readwn.ReaderWnScraper
import tachiyomix.annotations.Extension

@Extension
abstract class FanMTL(deps: Dependencies) : ReaderWnScraper(
    deps = deps,
    sourceId = 63,
    key = "https://www.fanmtl.com",
    sourceName = "Fans MTL",
    language = "en"
)
