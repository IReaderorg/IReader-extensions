package ireader.wbnovel

import ireader.core.source.Dependencies
import ireader.skynovelmodel.SkyNovelModel
import tachiyomix.annotations.Extension


@Extension
abstract class WbNovel(val deps: Dependencies) : SkyNovelModel(deps,) {

    override val id: Long
        get() = 51

    override val name: String
        get() = "WbNovel"
    override val lang: String
        get() = "in"

    override val baseUrl: String
        get() = "https://wbnovel.com"

    override val mainEndpoint: String
        get() = "all-novel"

    override val descriptionSelector: String
        get() = "summary__content p"

    override val contentSelector: String
        get() = ".reading-content p"
}
