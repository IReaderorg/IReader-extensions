package ireader.utility

import ireader.core.source.Dependencies
import ireader.core.source.HttpSource

interface TestConstants {
    val bookUrl:String
    val bookName:String
    val chapterUrl:String
    val chapterName:String
    fun getExtension(deps: Dependencies): HttpSource
}