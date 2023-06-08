package ireader.teamxnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class TeamXNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://teamxnovel.com",
    sourceName = "teamxnovel",
    sourceId = 75,
    language = "ar",

)
