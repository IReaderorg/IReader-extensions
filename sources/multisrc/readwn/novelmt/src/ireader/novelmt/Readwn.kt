package ireader.readwn


import ireader.core.source.Dependencies
import tachiyomix.annotations.Extension

@Extension
abstract class Readwn(val deps: Dependencies) : ReaderWnScraper(
    deps,
    key = "https://www.novelmt.com",
    sourceName = "Novelmt",
    sourceId = 61,
    language = "en",
)
