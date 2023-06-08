package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class WebNovelLover(val deps: Dependencies) : Madara(
    deps,
    key = "https://www.webnovelover.com",
    sourceName = "webnovelover",
    sourceId = 66,
    language = "en",


)
