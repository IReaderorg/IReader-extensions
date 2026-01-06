package ireader.testserver

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ireader.core.source.model.*
import kotlin.system.measureTimeMillis

// Global source manager
val sourceManager = SourceManager()
val sourceScanner = SourceScanner()

fun Application.configureRouting() {
    routing {
        // Serve the UI
        get("/") {
            call.respondText(getIndexHtml(), ContentType.Text.Html)
        }
        
        // API Routes
        route("/api") {
            // List all loaded sources
            get("/sources") {
                val sources = sourceManager.getAllSources().map { 
                    sourceManager.getSourceInfo(it) 
                }
                call.respond(sources)
            }
            
            // List all available sources (from compiled builds)
            get("/available-sources") {
                val available = sourceScanner.scanAvailableSources()
                call.respond(available)
            }
            
            // List all source files (even uncompiled)
            get("/source-files") {
                val files = sourceScanner.scanSourceFiles()
                call.respond(files)
            }
            
            // Check dex2jar status
            get("/dex2jar-status") {
                val loader = Dex2JarLoader(sourceManager.getDependencies())
                call.respond(mapOf(
                    "available" to loader.isDex2JarAvailable(),
                    "message" to if (loader.isDex2JarAvailable()) 
                        "dex2jar is available for dynamic source loading" 
                    else 
                        "dex2jar not found. Install from https://github.com/pxb1988/dex2jar/releases"
                ))
            }
            
            // Get captured logs
            get("/logs") {
                val level = call.request.queryParameters["level"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                call.respond(LogCapture.getLogs(level, limit))
            }
            
            // Clear logs
            post("/logs/clear") {
                LogCapture.clearLogs()
                call.respond(mapOf("success" to true, "message" to "Logs cleared"))
            }
            
            // Reload sources
            post("/reload") {
                val deps = sourceManager.getDependencies()
                var loaded = 0
                
                // Try dex2jar loading (parallel)
                val dex2jarLoader = Dex2JarLoader(deps)
                if (dex2jarLoader.isDex2JarAvailable()) {
                    val sources = dex2jarLoader.loadAllSourcesParallel()
                    sources.forEach { source ->
                        if (sourceManager.getSource(source.id) == null) {
                            sourceManager.registerSource(source)
                            loaded++
                        }
                    }
                }
                
                call.respond(mapOf(
                    "success" to true,
                    "loaded" to loaded,
                    "total" to sourceManager.getAllSources().size
                ))
            }
            
            // Get source info
            get("/sources/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "Invalid source ID"))
                
                val source = sourceManager.getSource(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("NotFound", "Source not found"))
                
                call.respond(sourceManager.getSourceInfo(source))
            }
            
            // Search/Browse
            get("/sources/{id}/search") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "Invalid source ID"))
                
                val source = sourceManager.getSource(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("NotFound", "Source not found"))
                
                val query = call.request.queryParameters["q"] ?: ""
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                
                var result: MangasPageInfo
                val timing = measureTimeMillis {
                    result = if (query.isNotBlank()) {
                        val filters = listOf(Filter.Title().apply { value = query })
                        source.getMangaList(filters, page)
                    } else {
                        val listing = source.getListings().firstOrNull()
                        source.getMangaList(listing, page)
                    }
                }
                
                call.respond(SearchResponse(
                    source = source.name,
                    query = query,
                    page = page,
                    hasNextPage = result.hasNextPage,
                    results = result.mangas.map { it.toResult() },
                    timing = timing
                ))
            }
            
            // Get manga details
            get("/sources/{id}/details") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "Invalid source ID"))
                
                val source = sourceManager.getSource(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("NotFound", "Source not found"))
                
                val url = call.request.queryParameters["url"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "URL parameter required"))
                
                var manga: MangaInfo
                val timing = measureTimeMillis {
                    manga = source.getMangaDetails(MangaInfo(key = url, title = ""), emptyList())
                }
                
                call.respond(DetailsResponse(
                    source = source.name,
                    url = url,
                    manga = manga.toResult(),
                    timing = timing
                ))
            }
            
            // Get chapters
            get("/sources/{id}/chapters") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "Invalid source ID"))
                
                val source = sourceManager.getSource(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("NotFound", "Source not found"))
                
                val url = call.request.queryParameters["url"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "URL parameter required"))
                
                var chapters: List<ChapterInfo>
                val timing = measureTimeMillis {
                    chapters = source.getChapterList(MangaInfo(key = url, title = ""), emptyList())
                }
                
                call.respond(ChaptersResponse(
                    source = source.name,
                    url = url,
                    chapters = chapters.map { it.toResult() },
                    timing = timing
                ))
            }
            
            // Get chapter content
            get("/sources/{id}/content") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "Invalid source ID"))
                
                val source = sourceManager.getSource(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("NotFound", "Source not found"))
                
                val url = call.request.queryParameters["url"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "URL parameter required"))
                
                var pages: List<Page>
                val timing = measureTimeMillis {
                    pages = source.getPageList(ChapterInfo(key = url, name = ""), emptyList())
                }
                
                val content = pages.mapNotNull { page ->
                    when (page) {
                        is Text -> page.text
                        else -> null
                    }
                }
                
                call.respond(ContentResponse(
                    source = source.name,
                    url = url,
                    content = ContentResult(content = content),
                    timing = timing
                ))
            }
            
            // Run test suite for a source
            get("/sources/{id}/test") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "Invalid source ID"))
                
                val source = sourceManager.getSource(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("NotFound", "Source not found"))
                
                val tests = mutableListOf<TestResult>()
                
                // Test 1: Browse/Latest
                tests.add(runTest("Browse Latest") {
                    val listing = source.getListings().firstOrNull()
                    val result = source.getMangaList(listing, 1)
                    "Found ${result.mangas.size} manga(s), hasNextPage: ${result.hasNextPage}"
                })
                
                // Test 2: Search (if supported)
                if (source.getFilters().any { it is Filter.Title }) {
                    tests.add(runTest("Search") {
                        val filters = listOf(Filter.Title().apply { value = "test" })
                        val result = source.getMangaList(filters, 1)
                        "Found ${result.mangas.size} result(s)"
                    })
                }
                
                // Test 3: Get details (if browse returned results)
                val firstManga = try {
                    val listing = source.getListings().firstOrNull()
                    source.getMangaList(listing, 1).mangas.firstOrNull()
                } catch (e: Exception) { null }
                
                if (firstManga != null) {
                    tests.add(runTest("Get Details") {
                        val details = source.getMangaDetails(firstManga, emptyList())
                        "Title: ${details.title}, Author: ${details.author}"
                    })
                    
                    // Test 4: Get chapters
                    tests.add(runTest("Get Chapters") {
                        val chapters = source.getChapterList(firstManga, emptyList())
                        "Found ${chapters.size} chapter(s)"
                    })
                    
                    // Test 5: Get content (if chapters exist)
                    val firstChapter = try {
                        source.getChapterList(firstManga, emptyList()).firstOrNull()
                    } catch (e: Exception) { null }
                    
                    if (firstChapter != null) {
                        tests.add(runTest("Get Content") {
                            val pages = source.getPageList(firstChapter, emptyList())
                            val textCount = pages.count { it is Text }
                            "Found $textCount text page(s)"
                        })
                    }
                }
                
                call.respond(SourceTestSuite(source = source.name, tests = tests))
            }
        }
    }
}

private suspend fun runTest(name: String, block: suspend () -> String): TestResult {
    return try {
        var data: String
        val timing = measureTimeMillis {
            data = block()
        }
        TestResult(success = true, message = name, timing = timing, data = data)
    } catch (e: Exception) {
        TestResult(success = false, message = "$name: ${e.message}", timing = 0)
    }
}

// Extension functions to convert models
private fun MangaInfo.toResult() = MangaResult(
    key = key,
    title = title,
    cover = cover,
    author = author,
    description = description,
    genres = genres,
    status = when (status) {
        MangaInfo.ONGOING -> "Ongoing"
        MangaInfo.COMPLETED -> "Completed"
        MangaInfo.LICENSED -> "Licensed"
        MangaInfo.CANCELLED -> "Cancelled"
        MangaInfo.ON_HIATUS -> "On Hiatus"
        else -> "Unknown"
    }
)

private fun ChapterInfo.toResult() = ChapterResult(
    key = key,
    name = name,
    number = number,
    dateUpload = dateUpload,
    scanlator = scanlator
)
