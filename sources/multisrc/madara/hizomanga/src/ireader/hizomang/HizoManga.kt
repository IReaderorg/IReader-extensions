package ireader.hizomanga

import ireader.madara.Madara
import ireader.core.source.Dependencies

import tachiyomix.annotations.Extension

@Extension
abstract class HizoManga(val deps: Dependencies) : Madara(
    deps,
    key = "https://hizomanga.net",
    sourceName = "HizoManga",
    sourceId = 48,  // Changed from 46 to avoid collision
    language = "ar"
)
