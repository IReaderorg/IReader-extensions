package ireader.mtlnovelen

import ireader.core.source.Dependencies
import ireader.mtlnovelmodel.MtlNovelModel
import tachiyomix.annotations.Extension


@Extension
abstract class MtlNovelEn(val deps: Dependencies) : MtlNovelModel(deps,) {
    override val baseUrl: String
        get() = "https://www.mtlnovel.com"
    override val lang = "en"
}
