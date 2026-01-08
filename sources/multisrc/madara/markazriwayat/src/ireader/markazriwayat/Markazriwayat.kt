package ireader.markazriwayat

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.post
import ireader.madara.Madara
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.MangaInfo
import ireader.core.util.DefaultDispatcher
import ireader.madara.DateParser
import kotlinx.coroutines.withContext

import tachiyomix.annotations.Extension

@Extension
abstract class Markazriwayat(val deps: Dependencies) : Madara(
    deps,
    key = "https://markazriwayat.com",
    sourceName = "Markazriwayat",
    sourceId = 47,  // Changed from 46 to avoid collision
    language = "ar"
) {
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let {
            return chaptersParse(Ksoup.parse(it.html)).reversed()
        }
        return kotlin.runCatching {
            return@runCatching withContext(DefaultDispatcher) {
                var chapters =
                    chaptersParse(
                        client.post(requestBuilder(manga.key + "ajax/chapters/")).asJsoup(),
                    )
                if (chapters.isEmpty()) {
                    chapters = chaptersParse(client.post(requestBuilder(manga.key)).asJsoup())
                }
                return@withContext chapters.reversed()
            }
        }.getOrThrow()
    }
}
