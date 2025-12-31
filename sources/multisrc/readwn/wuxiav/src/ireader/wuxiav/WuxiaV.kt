package ireader.wuxiav

import ireader.core.source.Dependencies
import ireader.readwn.ReaderWnScraper
import tachiyomix.annotations.Extension

@Extension
abstract class WuxiaV(deps: Dependencies) : ReaderWnScraper(
    deps = deps,
    sourceId = 65,
    key = "https://www.wuxiav.com",
    sourceName = "WuxiaV",
    language = "en"
)
