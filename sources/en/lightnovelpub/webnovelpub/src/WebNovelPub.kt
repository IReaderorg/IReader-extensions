package ireader.lightnovelpub

import ireader.core.source.Dependencies
import tachiyomix.annotations.Extension

@Extension
abstract class WebNovelPub(private val deps: Dependencies) : LightNovelPub(deps) {

    override val name: String
        get() = "WebNovelPub"

    override val id: Long
        get() =  48

    override val baseUrl: String
        get() = "https://www.webnovelpub.com/"
}
