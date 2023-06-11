package ireader.lunarletters


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class LunarLetters(val deps: Dependencies) : Madara(
    deps,
    key = "https://www.lunarletters.com",
    sourceName = "LunarLetters",
    sourceId = 81,
    language = "en",
    paths = Path(
        novel = "series",
        novels = "series",
        chapter = "series"
    )

)
