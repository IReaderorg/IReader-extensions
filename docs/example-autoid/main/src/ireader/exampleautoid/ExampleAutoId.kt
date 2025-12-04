package ireader.exampleautoid

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    EXAMPLE: Using @AutoSourceId                          ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  This example shows the SIMPLEST way to use auto-generated source IDs.   ║
 * ║                                                                          ║
 * ║  BEFORE (old way):                                                       ║
 * ║    override val id: Long get() = 12345  // Manual, error-prone!          ║
 * ║                                                                          ║
 * ║  AFTER (new way):                                                        ║
 * ║    @AutoSourceId                                                         ║
 * ║    // ID is auto-generated at compile time!                              ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@Extension
@AutoSourceId  // <-- Just add this! ID is auto-generated from name + lang
abstract class ExampleAutoId(deps: Dependencies) : SourceFactory(deps) {

    // ========================================================================
    // REQUIRED: Basic source info
    // ========================================================================
    
    override val name: String get() = "Example Auto ID"
    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://example.com"

    // ========================================================================
    // ID: Now auto-generated! 🎉
    // ========================================================================
    // 
    // After you build the project, KSP generates a file called:
    //   ExampleAutoIdSourceId.kt
    // 
    // It contains:
    //   object ExampleAutoIdSourceId {
    //       const val ID: Long = 1234567890123456789L
    //   }
    //
    // Then you can use it like this:
    //   override val id: Long get() = ExampleAutoIdSourceId.ID
    //
    // For now, we use a placeholder (the build system provides the ID):
    override val id: Long get() = super.id

    // ========================================================================
    // Standard filters and commands (unchanged)
    // ========================================================================
    
    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    // ========================================================================
    // Fetchers (your scraping logic goes here)
    // ========================================================================
    
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/latest/page/{page}/",
                selector = ".novel-item",
                nameSelector = ".title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = "img",
                coverAtt = "src",
            ),
        )

    override val detailFetcher: Detail
        get() = Detail(
            nameSelector = "h1.title",
            coverSelector = ".cover img",
            coverAtt = "src",
            descriptionSelector = ".description",
        )

    override val chapterFetcher: Chapters
        get() = Chapters(
            selector = ".chapter-list a",
            nameSelector = "a",
            linkSelector = "a",
            linkAtt = "href",
        )

    override val contentFetcher: Content
        get() = Content(
            pageContentSelector = ".chapter-content p",
        )
}
