package ireader.readwn


import ireader.core.source.Dependencies
import tachiyomix.annotations.Extension

@Extension
abstract class Ltnovel(val deps: Dependencies) : ReaderWnScraper(
    deps,
    key = "https://www.ltnovel.com",
    sourceName = "Ltnovel",
    sourceId = 62,
    language = "en",
)
