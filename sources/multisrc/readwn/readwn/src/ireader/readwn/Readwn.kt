package ireader.readwn


import ireader.core.source.Dependencies
import tachiyomix.annotations.Extension

@Extension
abstract class Readwn(val deps: Dependencies) : ReaderWnScraper(
    deps,
    key = "https://www.readwn.com",
    sourceName = "Readwn.com",
    sourceId = 60,
    language = "en",
)
