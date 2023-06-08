package ireader.skynovel

import ireader.core.source.Dependencies
import ireader.skynovelmodel.SkyNovelModel
import tachiyomix.annotations.Extension


@Extension
abstract class SkyNovel(val deps: Dependencies) : SkyNovelModel(deps,)
