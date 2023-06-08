package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class NovelMultiverse(val deps: Dependencies) : Madara(
    deps,
    key = "https://www.novelmultiverse.com",
    sourceName = "novelmultiverse",
    sourceId = 61,
    language = "en",


)
