package ireader.hizomanga

import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.utility.TestConstants

object Constants : TestConstants {
    override val bookUrl: String
        get() = "https://hizomanga.net/serie/flowers-bloom-even-on-malicious-trees/"
    override val bookName: String
        get() = "Flowers bloom even on malicious trees."
    override val chapterUrl: String
        get() = "55"
    override val chapterName: String
        get() = "https://hizomanga.net/serie/flowers-bloom-even-on-malicious-trees/55/"

    override fun getExtension(deps: Dependencies): HttpSource {
        return object : HizoManga(deps) {

        }
    }
}
