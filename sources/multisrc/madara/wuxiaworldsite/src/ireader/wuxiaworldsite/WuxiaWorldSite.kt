package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class WuxiaWorldSite(val deps: Dependencies) : Madara(
    deps,
    key = "https://wuxiaworld.site",
    sourceName = "wuxiaworld",
    sourceId = 70,
    language = "en",
    paths = Path(
        novel = "manga",
        novels = "manga",
        chapter = "novel"
    )

)
