package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class MostNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://mostnovel.com/",
    sourceName = "mostnovel",
    sourceId = 60,
    language = "en",
    paths = Path(
        novel = "manga",
        novels = "manga",
        chapter = "manga"
    )

)
