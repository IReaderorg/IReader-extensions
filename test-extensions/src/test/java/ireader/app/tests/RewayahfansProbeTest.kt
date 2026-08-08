package ireader.app.tests

import ireader.app.dependencies
import ireader.core.source.SourceFactory
import ireader.rewayatfans.RewayatFans
import ireader.rewayahfans.RewayahFans
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RewayahfansProbeTest {

    private val sources: List<SourceFactory> = listOf(
        object : RewayahFans(deps = dependencies) {},
        object : RewayatFans(deps = dependencies) {},
    )

    @Test
    fun `probe all listings`() = runBlocking {
        sources.forEach { ext ->
            println("### ${ext.name}")
            ext.exploreFetchers
                .filter { it.type != SourceFactory.Type.Search }
                .forEach { fetcher ->
                    try {
                        val page = ext.getLists(fetcher, 1, "", emptyList())
                        println(">>> ${fetcher.key}: count=${page.mangas.size} hasNext=${page.hasNextPage}")
                        page.mangas.take(3).forEach {
                            println("    [${it.title}] | ${it.key} | ${it.cover}")
                        }
                    } catch (e: Throwable) {
                        println(">>> ${fetcher.key}: ERROR $e")
                    }
                }
        }
    }
}
