package ireader.webnovelpub

import ireader.core.source.Dependencies
import ireader.lightnovelpub.LightNovelPub
import tachiyomix.annotations.Extension

/**
 * WebNovelPub extends LightNovelPub since they share the same site structure.
 * Only overrides the site-specific values (baseUrl, id, name) and content filtering.
 */
@Extension
abstract class WebNovelPub(deps: Dependencies) : LightNovelPub(deps) {

    override val baseUrl: String get() = "https://www.webnovelpub.com"
    override val id: Long get() = 48
    override val name: String get() = "WebNovelPub"

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "#chapter-article > section.page-in.content-wrap > div.titles > h1 > span.chapter-title",
            pageContentSelector = "#chapter-container p",
            onContent = { content ->
                content.filter { !it.contains("webnovelpub", true) || !it.contains("no_vel_read_ing") }
            }
        )
}
