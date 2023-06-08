package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class TurkceLightNovels(val deps: Dependencies) : Madara(
    deps,
    key = "https://turkcelightnovels.com",
    sourceName = "turkcelightnovels",
    sourceId = 76,
    language = "tu",
    paths = Path(
        novel = "light-novel",
        novels = "light-novel",
        chapter = "light-novel"
    )

)
