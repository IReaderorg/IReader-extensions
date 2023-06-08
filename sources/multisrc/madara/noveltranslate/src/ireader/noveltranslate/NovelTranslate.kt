package ireader.noveltranslate


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class NovelTranslate(val deps: Dependencies) : Madara(
    deps,
    key = "https://noveltranslate.com",
    sourceName = "NovelTranslate",
    sourceId = 55,
    language = "en",
    paths = Path(
        novel = "novel",
        novels = "novel",
        chapter = "novel"
    )

)
