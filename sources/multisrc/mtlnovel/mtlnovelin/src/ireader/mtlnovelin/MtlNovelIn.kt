package ireader.mtlnovelin

import ireader.core.source.Dependencies
import ireader.mtlnovelmodel.MtlNovelModel
import tachiyomix.annotations.Extension


@Extension
abstract class MtlNovelIn(val deps: Dependencies) : MtlNovelModel(deps,) {
    override val baseUrl: String
        get() = "https://id.mtlnovel.com"
    override val lang = "in"
}
