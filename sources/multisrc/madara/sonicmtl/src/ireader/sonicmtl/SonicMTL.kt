package ireader.sonicmtl


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class SonicMTL(val deps: Dependencies) : Madara(
    deps,
    key = "https://www.sonicmtl.com",
    sourceName = "sonicmtl",
    sourceId = 77,
    language = "en",


)
