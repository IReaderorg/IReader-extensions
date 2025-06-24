package ireader.armtl

import ireader.madara.Madara
import ireader.core.source.Dependencies

import tachiyomix.annotations.Extension

@Extension
abstract class ArMtl(val deps: Dependencies) : Madara(
    deps,
    key = "https://ar-mtl.club",
    sourceName = "ArMtl",
    sourceId = 46,
    language = "ar"
)
