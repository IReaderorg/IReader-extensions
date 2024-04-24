package ireader.mtlnoveles

import ireader.core.source.Dependencies
import ireader.mtlnovelmodel.MtlNovelModel
import tachiyomix.annotations.Extension


@Extension
abstract class MtlNovelEs(val deps: Dependencies) : MtlNovelModel(deps,) {
    override val baseUrl: String
        get() = "https://es.mtlnovel.com"
}
