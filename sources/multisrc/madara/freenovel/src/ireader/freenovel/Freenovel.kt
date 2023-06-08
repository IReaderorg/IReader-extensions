package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class Freenovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://freenovel.me",
    sourceName = "freenovel",
    sourceId = 59,
    language = "en",

)
