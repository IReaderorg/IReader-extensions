package ireader.hizomanga

import ireader.madara.Madara
import ireader.core.source.Dependencies

import tachiyomix.annotations.Extension

@Extension
abstract class HizoManga(val deps: Dependencies) : Madara(
    deps,
    key = "https://hizomanga.net",
    sourceName = "ArMtl",
    sourceId = 46,
    language = "ar"
)
