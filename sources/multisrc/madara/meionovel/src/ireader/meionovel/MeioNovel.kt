package ireader.meionovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class MeioNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://meionovel.id",
    sourceName = "meionovel",
    sourceId = 64,
    language = "in",


)
