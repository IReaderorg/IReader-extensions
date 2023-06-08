package ireader.arnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class ArNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://arnovel.me",
    sourceName = "arnovel",
    sourceId = 65,
    language = "ar",


)
