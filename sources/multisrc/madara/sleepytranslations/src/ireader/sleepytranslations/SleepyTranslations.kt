package ireader.zinnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class SleepyTranslations(val deps: Dependencies) : Madara(
    deps,
    key = "https://sleepytranslations.com",
    sourceName = "SleepyTranslations",
    sourceId = 56,
    language = "en",
    paths = Path(
        novel = "series",
        novels = "series",
        chapter = "series"
    )

)
