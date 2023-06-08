package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class Novelstic(val deps: Dependencies) : Madara(
    deps,
    key = "https://novelstic.com",
    sourceName = "novelstic",
    sourceId = 79,
    language = "en",


)
