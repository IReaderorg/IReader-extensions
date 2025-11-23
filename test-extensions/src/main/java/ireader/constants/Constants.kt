package ireader.constants

import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.chrysanthemumgarden.Chrysanthemumgarden
import ireader.utility.TestConstants

object Constants : TestConstants {
    override val bookUrl: String
        get() = "https://chrysanthemumgarden.com/novel/test"
    override val bookName: String
        get() = "Test Novel"
    override val chapterUrl: String
        get() = "https://chrysanthemumgarden.com/novel/test/chapter-1"
    override val chapterName: String
        get() = "Chapter 1"

    override fun getExtension(deps: Dependencies): HttpSource {
        return object : Chrysanthemumgarden(deps) {
            // Test instance
        }
    }
}
