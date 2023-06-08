package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class CrateNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://cratenovel.com",
    sourceName = "cratenovel",
    sourceId = 67,
    language = "en",


)
