package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class MoreNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://morenovel.net",
    sourceName = "morenovel",
    sourceId = 73,
    language = "in",


)
