package ireader.firstkissnovel


import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.madara.Path
import tachiyomix.annotations.Extension

@Extension
abstract class FirstKissNovel(val deps: Dependencies) : Madara(
    deps,
    key = "https://1stkissnovel.love",
    sourceName = "Firstkissnovel",
    sourceId = 57,
    language = "en",


)
