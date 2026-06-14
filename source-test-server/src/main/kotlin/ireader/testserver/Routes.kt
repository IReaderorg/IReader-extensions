package ireader.testserver

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ireader.core.source.model.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

// Global source manager
val sourceManager = SourceManager()
val sourceScanner = SourceScanner()
lateinit var sourceWatcher: SourceWatcher

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
            
            // Reload sources (adds new sources only)
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
            
            // Force reload all sources (clears cache and reloads everything)
            post("/reload-all") {
                val deps = sourceManager.getDependencies()
                
                // Clear the source manager
                sourceManager.clearAll()
                
                // Clear dex2jar cache and reload
                val dex2jarLoader = Dex2JarLoader(deps)
                dex2jarLoader.clearCache()
                
                val sources = dex2jarLoader.loadAllSourcesParallel()
                sources.forEach { source ->
                    sourceManager.registerSource(source)
                }
                
                call.respond(mapOf(
                    "success" to true,
                    "message" to "All sources reloaded from scratch",
                    "total" to sourceManager.getAllSources().size
                ))
            }
            
            // Reload a specific source by name
            post("/reload/{name}") {
                val name = call.parameters["name"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "Source name required"))
                
                val deps = sourceManager.getDependencies()
                val dex2jarLoader = Dex2JarLoader(deps)
                
                // Remove old source from manager
                sourceManager.removeByName(name)
                
                // Reload from APK
                val source = dex2jarLoader.reloadSource(name)
                
                if (source != null) {
                    sourceManager.registerSource(source)
                    call.respond(mapOf(
                        "success" to true,
                        "message" to "Source '$name' reloaded successfully",
                        "source" to sourceManager.getSourceInfo(source)
                    ))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiError("NotFound", "Could not reload source: $name"))
                }
            }
            
            // Build and reload a specific source
            post("/build/{name}") {
                val name = call.parameters["name"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid", "Source name required"))
                
                val lang = call.request.queryParameters["lang"] ?: "en"
                val langCap = lang.replaceFirstChar { it.uppercase() }
                
                call.respond(mapOf(
                    "success" to true,
                    "message" to "Building $name...",
                    "building" to true
                ))
                
                // Build in background
                launch(Dispatchers.IO) {
                    try {
                        val gradleTask = ":extensions:individual:$lang:$name:assemble${langCap}Debug"
                        
                        val process = ProcessBuilder(
                            "./gradlew", gradleTask, "--daemon"
                        )
                            .directory(File("."))
                            .redirectErrorStream(true)
                            .start()
                        
                        val output = process.inputStream.bufferedReader().readText()
                        val success = process.waitFor(120, TimeUnit.SECONDS)
                        
                        if (success && process.exitValue() == 0) {
                            // Reload the source
                            val deps = sourceManager.getDependencies()
                            val dex2jarLoader = Dex2JarLoader(deps)
                            sourceManager.removeByName(name)
                            val reloaded = dex2jarLoader.reloadSource(name)
                            
                            if (reloaded != null) {
                                sourceManager.registerSource(reloaded)
                                println("   Build & reloaded: ${reloaded.name}")
                            } else {
                                println("   Build OK but reload failed: $name")
                            }
                        } else {
                            val errorLines = output.lines().filter { it.contains("error:", ignoreCase = true) }.take(5)
                            val errorMsg = if (errorLines.isNotEmpty()) {
                                errorLines.joinToString("\n")
                            } else {
                                "Build failed (exit: ${process.exitValue()})"
                            }
                            println("   Build failed: $name\n   $errorMsg")
                        }
                    } catch (e: Exception) {
                        println("   Build error: ${e.message}")
                    }
                }
            }
            
            // Start watching for file changes (auto-rebuild)
            post("/watch/start") {
                if (sourceWatcher.isWatching) {
                    call.respond(mapOf(
                        "success" to true,
                        "message" to "Already watching",
                        "watching" to true
                    ))
                } else {
                    sourceWatcher.startWatching()
                    call.respond(mapOf(
                        "success" to true,
                        "message" to "Started watching for changes",
                        "watching" to true
                    ))
                }
            }
            
            // Stop watching
            post("/watch/stop") {
                sourceWatcher.stopWatching()
                call.respond(mapOf(
                    "success" to true,
                    "message" to "Stopped watching",
                    "watching" to false
                ))
            }
            
            // Check watch status
            get("/watch/status") {
                val message = if (sourceWatcher.isWatching) "Watching for file changes" else "Not watching"
                call.respond(mapOf(
                    "watching" to sourceWatcher.isWatching,
                    "message" to message
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
                
                val html = call.request.queryParameters["html"] ?: ""
                
                val commands = if (html.isNotBlank()) {
                    listOf(Command.Chapter.Fetch(html = html))
                } else {
                    emptyList()
                }
                
                var chapters: List<ChapterInfo>
                val timing = measureTimeMillis {
                    chapters = source.getChapterList(MangaInfo(key = url, title = ""), commands)
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
                
                val html = call.request.queryParameters["html"] ?: ""
                
                val commands = if (html.isNotBlank()) {
                    listOf(Command.Content.Fetch(html = html))
                } else {
                    emptyList()
                }
                
                var pages: List<Page>
                val timing = measureTimeMillis {
                    pages = source.getPageList(ChapterInfo(key = url, name = ""), commands)
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
                    val count = result.mangas.size
                    if (count == 0) throw Exception("No manga found - source may be broken")
                    "Found $count manga(s), hasNextPage: ${result.hasNextPage}"
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
                        if (details.title.isBlank()) throw Exception("Title is empty")
                        "Title: ${details.title}, Author: ${details.author}"
                    })
                    
                    // Test 4: Get chapters
                    val chapters = try {
                        source.getChapterList(firstManga, emptyList())
                    } catch (e: Exception) { emptyList() }
                    
                    tests.add(runTest("Get Chapters") {
                        if (chapters.isEmpty()) throw Exception("No chapters found - source may be broken")
                        if (chapters.size < 3) throw Exception("Only ${chapters.size} chapters found - likely insufficient data")
                        "Found ${chapters.size} chapter(s)"
                    })
                    
                    // Test 5: Get content (if chapters exist)
                    val firstChapter = chapters.firstOrNull()
                    
                    if (firstChapter != null) {
                        tests.add(runTest("Get Content") {
                            val pages = source.getPageList(firstChapter, emptyList())
                            val textPages = pages.filterIsInstance<Text>()
                            val textCount = textPages.size
                            if (textCount == 0) throw Exception("No text content found - source may require JavaScript or be broken")
                            val totalChars = textPages.sumOf { it.text.length }
                            if (totalChars < 50) throw Exception("Content too short ($totalChars chars) - likely not actual content")
                            "Found $textCount paragraphs, $totalChars characters"
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
