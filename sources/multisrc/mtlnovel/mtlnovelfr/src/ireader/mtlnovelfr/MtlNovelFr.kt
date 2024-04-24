package ireader.mtlnovelfr

import ireader.core.source.Dependencies
import ireader.mtlnovelmodel.MtlNovelModel
import tachiyomix.annotations.Extension

@Extension
abstract class MtlNovelFr(val deps: Dependencies) : MtlNovelModel(deps,) {
    override val baseUrl: String
        get() = "https://fr.mtlnovel.com"
    override val lang = "fr"
}
