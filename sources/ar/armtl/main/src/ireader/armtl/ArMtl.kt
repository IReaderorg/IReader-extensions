package ireader.armtl

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import ireader.Madara.Madara
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.findInstance
import org.ireader.core_api.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension


@Extension
abstract class ArMtl(val deps: Dependencies) : Madara(
    deps,
    key = "https://ar-mtl.club/",
    sourceName = "ArMtl",
    sourceId = 46,
    language = "ar"
) {

}