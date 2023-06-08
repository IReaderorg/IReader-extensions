package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class Daonovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://daonovel.com",
    sourceName = "daonovel",
    sourceId = 58,
    language = "en",
    paths = Path(
        novel = "novels-list",
        novels = "novels",
        chapter = "novels"
    )

)
