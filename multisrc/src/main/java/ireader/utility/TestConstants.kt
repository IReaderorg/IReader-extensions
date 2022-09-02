package ireader.utility

import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource

interface TestConstants {
    val bookUrl:String
    val bookName:String
    val chapterUrl:String
    val chapterName:String
    fun getExtension(deps: Dependencies): HttpSource
}