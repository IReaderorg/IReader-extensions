package ireader.hozomanaga

import ireader.madara.Madara
import ireader.core.source.Dependencies

import tachiyomix.annotations.Extension

@Extension
abstract class ArMtl(val deps: Dependencies) : Madara(
    deps,
    key = "https://hizomanga.net",
    sourceName = "HizoManga",
    sourceId = 46,
    language = "ar"
)
