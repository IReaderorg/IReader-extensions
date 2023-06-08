package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class Zinnovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://zinnovel.com",
    sourceName = "zinnovel",
    sourceId = 54,
    language = "en",
    paths = Path(
        novel = "manga",
        novels = "manga",
        chapter = "novel"
    )

)
