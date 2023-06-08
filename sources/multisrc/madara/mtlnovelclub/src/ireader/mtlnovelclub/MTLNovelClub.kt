package ireader.mtlnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class MTLNovelClub(val deps: Dependencies) : Madara(
    deps,
    key = "https://mtlnovel.club",
    sourceName = "mtlnovelclub",
    sourceId = 78,
    language = "en",


)
