package ireader.wuxiaspot

import ireader.core.source.Dependencies
import ireader.readwn.ReaderWnScraper
import tachiyomix.annotations.Extension

@Extension
abstract class WuxiaSpot(deps: Dependencies) : ReaderWnScraper(
    deps = deps,
    sourceId = 64,
    key = "https://www.wuxiaspot.com",
    sourceName = "Wuxia Space",
    language = "en"
)
