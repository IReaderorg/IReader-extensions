package ireader.neosekaitranslations

import ireader.madara.Madara
import ireader.core.source.Dependencies
import tachiyomix.annotations.Extension

@Extension
abstract class Neosekaitranslations(val deps: Dependencies) : Madara(
    deps,
    key = "https://www.neosekaitranslations.com",
    sourceName = "Neosekaitranslations",
    sourceId = 45,
    language = "en"
)
