package ireader.constants

import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.rewayahfans.RewayahFans
import ireader.utility.TestConstants

object Constants : TestConstants {
    override val bookUrl: String
        get() = "https://rewayahfans.net/الكونت-الذي-هجرته-زوجته-الزوجة-التي-تع"
    override val bookName: String
        get() = "Test Novel"
    override val chapterUrl: String
        get() = "https://rewayahfans.net/الكونت-الذي-هجرته-زوجته-الزوجة-التي-تع"
    override val chapterName: String
        get() = "Chapter 1"

    override fun getExtension(deps: Dependencies): HttpSource {
        return object : RewayahFans(deps) {
            // Test instance
        }
    }
}
