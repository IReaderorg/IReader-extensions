package ireader.mysticalseries


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class MysticalSeries(val deps: Dependencies) : Madara(
    deps,
    key = "https://mysticalmerries.com",
    sourceName = "mysticalseries",
    sourceId = 72,
    language = "en",
    paths = Path(
        novel = "series",
        novels = "series",
        chapter = "series"
    )

)
