package ireader.madara

import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.utility.TestConstants

object Constants : TestConstants {
    override val bookUrl: String
        get() = "https://ar-mtl.club/novel/astral-pet-store/"
    override val bookName: String
        get() = "Astral Pet Store"
    override val chapterUrl: String
        get() = "Astral Pet Store novel - Chapter 1193"
    override val chapterName: String
        get() = "https://ar-mtl.club/novel/astral-pet-store/chapter-1193/"

    override fun getExtension(deps: Dependencies): HttpSource {
        return object : ArMtl(deps) {

        }
    }
}
