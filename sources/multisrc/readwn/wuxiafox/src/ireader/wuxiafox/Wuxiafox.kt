package ireader.wuxiafox

import ireader.core.source.Dependencies
import ireader.readwn.ReaderWnScraper
import tachiyomix.annotations.Extension

@Extension
abstract class Wuxiafox(deps: Dependencies) : ReaderWnScraper(
    deps = deps,
    sourceId = 62,
    key = "https://www.wuxiafox.com",
    sourceName = "Wuxiafox",
    language = "en"
)
