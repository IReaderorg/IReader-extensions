package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class WBNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://wbnovel.com",
    sourceName = "wbnovel",
    sourceId = 70,
    language = "in",


)
