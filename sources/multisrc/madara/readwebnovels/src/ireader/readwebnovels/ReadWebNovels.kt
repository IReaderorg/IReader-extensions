package ireader.readwebnovels


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class ReadWebNovels(val deps: Dependencies) : Madara(
    deps,
    key = "https://readwebnovels.net",
    sourceName = "readwebnovels",
    sourceId = 69,
    language = "en",


)
