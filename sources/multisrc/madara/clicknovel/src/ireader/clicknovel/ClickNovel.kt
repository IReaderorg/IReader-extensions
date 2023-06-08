package ireader.clicknovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class ClickNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://clicknovel.net",
    sourceName = "clicknovel",
    sourceId = 68,
    language = "en",

)
