package ireader.testserver

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.server.response.*
import ireader.core.source.CatalogSource
import ireader.core.source.Dependencies
import ireader.core.log.Log
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    // Initialize log capture to see source logs
    LogCapture.initialize()
    
    // Enable verbose logging to see all source logs
    Log.enableVerboseLogging()
    
    println("""
        ╔═══════════════════════════════════════════════════════════╗
        ║         IReader Source Test Server                        ║
        ║                                                           ║
        ║  Starting server on http://localhost:8080                 ║
        ║  📋 Verbose logging ENABLED - source logs will appear     ║
        ║  📋 View logs at http://localhost:8080/api/logs           ║
        ╚═══════════════════════════════════════════════════════════╝
    """.trimIndent())
    
    // Auto-discover and register all Extension classes
    println("\n📦 Discovering sources from compiled classes...")
    discoverAndRegisterSources(sourceManager)
    
    val count = sourceManager.getAllSources().size
    println("\n✅ $count source(s) loaded and ready to test")
    if (count > 0) {
        sourceManager.getAllSources().sortedBy { it.name }.forEach { 
            println("   • ${it.name} (${it.lang})")
        }
    } else {
        println("""
        
   ⚠️  No sources found! Make sure to compile sources first:
       ./gradlew assembleDebug
       
   Then run the test server again.
        """.trimIndent())
    }
    println("─".repeat(60))
    
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configurePlugins()
        configureRouting()
    }.start(wait = true)
}

/**
 * Auto-discover all Extension classes and register them
 */
fun discoverAndRegisterSources(manager: SourceManager) {
    val deps = manager.getDependencies()
    
    // Method 1: Try reflection on current classpath (for sources added as dependencies)
    try {
        val reflections = org.reflections.Reflections("tachiyomix.extension", "ireader")
        val extensionClasses = reflections.getSubTypesOf(CatalogSource::class.java)
        
        extensionClasses.forEach { clazz ->
            if (java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) return@forEach
            
            try {
                val constructor = clazz.constructors.firstOrNull { 
                    it.parameterCount == 1 && it.parameterTypes[0] == Dependencies::class.java
                }
                if (constructor != null) {
                    val source = constructor.newInstance(deps) as CatalogSource
                    if (manager.getSource(source.id) == null) {
                        manager.registerSource(source)
                        println("   ✓ Loaded from classpath: ${source.name}")
                    }
                }
            } catch (e: Exception) {
                // Skip failed sources silently
            }
        }
    } catch (e: Exception) {
        println("   ⚠ Reflection scan failed: ${e.message}")
    }
    
    // Method 2: Use dex2jar to load sources from compiled APKs (parallel)
    println("\n   Loading sources from APKs (parallel)...")
    val dex2jarLoader = Dex2JarLoader(deps)
    val loadedSources = dex2jarLoader.loadAllSourcesParallel()
    loadedSources.forEach { source ->
        if (manager.getSource(source.id) == null) {
            manager.registerSource(source)
        }
    }
}

fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
    }
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(
                    error = cause::class.simpleName ?: "Unknown",
                    message = cause.message ?: "An error occurred",
                    stackTrace = cause.stackTraceToString().take(2000)
                )
            )
        }
    }
    
    // Configure mock novel server
    configureMockServer()
}
