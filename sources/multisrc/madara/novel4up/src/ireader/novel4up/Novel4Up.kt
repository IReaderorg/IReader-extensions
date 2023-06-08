package ireader.novel4up


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class Novel4Up(val deps: Dependencies) : Madara(
    deps,
    key = "https://novel4up.com",
    sourceName = "novel4up",
    sourceId = 74,
    language = "ar",

)
