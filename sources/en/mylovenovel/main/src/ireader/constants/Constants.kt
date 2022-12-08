package ireader.constants

import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.mylovenovel.MyLoveNovel
import ireader.utility.TestConstants

object Constants : TestConstants {
    override val bookUrl: String
        get() = "https://m.novelhold.com/Death-Scripture-8130/"
    override val bookName: String
        get() = "Death Scripture"
    override val chapterUrl: String
        get() = "https://m.novelhold.com/Death-Scripture-8130/807305.html"
    override val chapterName: String
        get() = "https://m.novelhold.com/Death-Scripture-8130/807305.html"

    override fun getExtension(deps: Dependencies): HttpSource {
        return object : MyLoveNovel(deps) {

        }
    }
}
