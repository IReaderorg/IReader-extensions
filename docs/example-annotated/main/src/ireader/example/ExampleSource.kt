package ireader.example

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.FilterList
import tachiyomix.annotations.*

/**
 * Example source demonstrating ALL KSP annotations.
 * 
 * This source uses declarative annotations instead of manual implementation
 * for filters, fetchers, selectors, deep links, rate limiting, and more.
 */
@Extension
@SourceMeta(
    description = "Example novel source for demonstration",
    nsfw = false,
    icon = "https://example.com/icon.png",
    tags = ["english", "novels", "example"]
)

// Filter configuration
@SourceFilters(
    hasTitle = true,
    hasAuthor = true,
    hasSort = true,
    sortOptions = ["Latest", "Popular", "Rating", "A-Z"]
)

// Explore/listing endpoints
@ExploreFetcher(
    name = "Latest",
    endpoint = "/novels/latest?page={page}",
    selector = ".novel-item",
    nameSelector = ".novel-title a",
    linkSelector = ".novel-title a",
    coverSelector = ".novel-cover img"
)
@ExploreFetcher(
    name = "Popular",
    endpoint = "/novels/popular?page={page}",
    selector = ".novel-item",
    nameSelector = ".novel-title a",
    linkSelector = ".novel-title a",
    coverSelector = ".novel-cover img"
)
@ExploreFetcher(
    name = "Search",
    endpoint = "/search?q={query}&page={page}",
    selector = ".search-result",
    nameSelector = "h3 a",
    linkSelector = "h3 a",
    coverSelector = "img.cover",
    isSearch = true
)

// Page selectors
@DetailSelectors(
    title = "h1.novel-title",
    cover = ".novel-cover img",
    author = ".author-name a",
    description = ".novel-description p",
    genres = ".genre-tags a",
    status = ".novel-status"
)
@ChapterSelectors(
    list = ".chapter-list li",
    name = "a.chapter-link",
    link = "a.chapter-link",
    date = ".chapter-date",
    reversed = true
)
@ContentSelectors(
    content = ".chapter-content p",
    title = ".chapter-title",
    removeSelectors = [".ads", ".social-share", ".comments"]
)

// Deep link handling
@SourceDeepLink(
    host = "example.com",
    scheme = "https",
    pathPattern = "/novel/.*",
    type = "MANGA"
)
@SourceDeepLink(
    host = "example.com",
    pathPattern = "/chapter/.*",
    type = "CHAPTER"
)

// Rate limiting
@RateLimit(
    permits = 3,
    periodMs = 1000,
    applyToAll = true
)

// Custom headers
@CustomHeader(name = "X-Requested-With", value = "XMLHttpRequest")

// Pagination config
@Pagination(
    startPage = 1,
    maxPages = 100,
    itemsPerPage = 20
)

// API endpoints (for JSON APIs)
@ApiEndpoint(
    name = "GetNovelApi",
    path = "/api/v1/novel/{id}",
    method = "GET",
    params = ["id"]
)
@ApiEndpoint(
    name = "SearchApi",
    path = "/api/v1/search?q={query}&page={page}",
    method = "GET",
    params = ["query", "page"]
)

// Test generation configuration
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "fantasy",
    minSearchResults = 5
)
@TestFixture(
    novelUrl = "https://example.com/novel/test-novel",
    chapterUrl = "https://example.com/chapter/test-chapter",
    expectedTitle = "Test Novel",
    expectedAuthor = "Test Author"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 5,
    supportsPagination = true,
    requiresLogin = false
)

abstract class ExampleSource(deps: Dependencies) : SourceFactory(deps) {

    override val lang: String = "en"
    override val baseUrl: String = "https://example.com"
    override val id: Long = 999999L
    override val name: String = "Example Source"

    // Use generated filters from @SourceFilters annotation
    override fun getFilters(): FilterList {
        return ExampleSourceFilters.getGeneratedFilters()
    }

    override fun getCommands(): CommandList {
        return listOf(
            Command.Detail.Fetch(),
            Command.Chapter.Fetch(),
            Command.Content.Fetch(),
        )
    }

    // Use generated fetchers from @ExploreFetcher annotations
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = ExampleSourceFetchers.generatedExploreFetchers
    
    // Use generated request helpers
    // val request = ExampleSourceRequests.buildLatestRequest(baseUrl, page)
    
    // Use generated deep link handlers
    // if (ExampleSourceDeepLinks.canHandle(url)) { ... }
    
    // Use generated API client
    // val apiRequest = ExampleSourceApi.getnovelapi(baseUrl, novelId)
}
