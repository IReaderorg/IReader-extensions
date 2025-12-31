package ireader.wuxiabox

import ireader.core.source.Dependencies
import ireader.readwn.ReaderWnScraper
import tachiyomix.annotations.Extension

@Extension
abstract class Wuxiabox(deps: Dependencies) : ReaderWnScraper(
    deps = deps,
    sourceId = 61,
    key = "https://www.wuxiabox.com",
    sourceName = "Wuxiabox",
    language = "en"
)
