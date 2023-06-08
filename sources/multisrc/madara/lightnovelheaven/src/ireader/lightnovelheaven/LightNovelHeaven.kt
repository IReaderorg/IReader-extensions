package ireader.lightnovelheaven


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class LightNovelHeaven(val deps: Dependencies) : Madara(
    deps,
    key = "https://lightnovelheaven.com",
    sourceName = "lightnovelheaven",
    sourceId = 63,
    language = "en",
    paths = Path(
        novel = "series",
        novels = "series",
        chapter = "series"
    )

)
