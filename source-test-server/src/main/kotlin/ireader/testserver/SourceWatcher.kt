package ireader.testserver

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Watches source files for changes and triggers automatic rebuilds.
 * 
 * When a .kt file changes in sources/, it:
 * 1. Identifies which source was modified
 * 2. Runs Gradle build for that specific source
 * 3. Reloads the compiled source into SourceManager
 */
class SourceWatcher(
    private val sourceManager: SourceManager,
    private val scope: CoroutineScope
) {
    private var watcher: WatchService? = null
    private var watchJob: Job? = null
    private val watching = ConcurrentHashMap.newKeySet<String>()
    private val buildQueue = ConcurrentHashMap.newKeySet<String>()
    private var onSourceReloaded: ((String, Boolean, String) -> Unit)? = null
    
    val isWatching: Boolean get() = watchJob?.isActive == true
    
    /**
     * Set callback for when a source is reloaded.
     * Parameters: sourceName, success, message
     */
    fun setOnSourceReloaded(callback: (String, Boolean, String) -> Unit) {
        onSourceReloaded = callback
    }
    
    /**
     * Start watching the sources directory for changes.
     */
    fun startWatching(sourcesDir: File = File("sources")) {
        if (isWatching) {
            println("   Already watching for changes")
            return
        }
        
        if (!sourcesDir.exists()) {
            println("   Sources directory not found: ${sourcesDir.absolutePath}")
            return
        }
        
        println("   Starting file watcher on ${sourcesDir.absolutePath}")
        
        watchJob = scope.launch(Dispatchers.IO) {
            try {
                watcher = FileSystems.getDefault().newWatchService()
                
                // Register all language directories
                sourcesDir.listFiles()?.filter { it.isDirectory }?.forEach { langDir ->
                    if (langDir.name != "multisrc") {
                        registerDirectory(langDir.toPath())
                    }
                }
                
                // Also watch multisrc subdirectories
                val multisrcDir = File(sourcesDir, "multisrc")
                if (multisrcDir.exists()) {
                    multisrcDir.listFiles()?.filter { it.isDirectory }?.forEach { themeDir ->
                        registerDirectory(themeDir.toPath())
                    }
                }
                
                println("   Watching for .kt file changes...")
                println("   Modify a source file and it will auto-rebuild")
                
                // Process events
                while (isActive) {
                    val key = watcher?.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    
                    val events = key.pollEvents()
                    val eventIterator = events.iterator()
                    
                    while (eventIterator.hasNext()) {
                        val event = eventIterator.next() as WatchEvent<*>
                        val changed = event.context() as? Path ?: continue
                        val dir = key.watchable() as? Path ?: continue
                        
                        // Only react to .kt file changes
                        if (changed.toString().endsWith(".kt")) {
                            val fullPath = dir.resolve(changed)
                            val sourceDir = extractSourceDir(fullPath)
                            
                            if (sourceDir != null && !buildQueue.contains(sourceDir)) {
                                println("   Change detected: $fullPath")
                                buildQueue.add(sourceDir)
                                
                                // Debounce: wait a bit for more changes
                                delay(1000)
                                buildQueue.remove(sourceDir)
                                
                                // Trigger build
                                triggerBuild(sourceDir)
                            }
                        }
                    }
                    
                    key.reset()
                }
            } catch (e: Exception) {
                if (isActive) {
                    println("   Watcher error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Stop watching for changes.
     */
    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        watcher?.close()
        watcher = null
        println("   Stopped watching for changes")
    }
    
    /**
     * Register a directory and all its subdirectories for watching.
     */
    private fun registerDirectory(dir: Path) {
        try {
            dir.register(
                watcher,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE
            )
            watching.add(dir.toString())
        } catch (e: Exception) {
            // Skip directories we can't watch
        }
        
        // Register subdirectories recursively (limited depth)
        dir.toFile().listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            if (subDir.name != "build" && subDir.name != ".gradle") {
                registerDirectory(subDir.toPath())
            }
        }
    }
    
    /**
     * Extract the source directory (e.g., "en/novelfull") from a file path.
     */
    private fun extractSourceDir(path: Path): String? {
        val parts = path.toString().split(File.separator)
        
        // Find "sources" in the path and extract lang/name after it
        val sourcesIdx = parts.indexOf("sources")
        if (sourcesIdx >= 0 && parts.size > sourcesIdx + 2) {
            val lang = parts[sourcesIdx + 1]
            
            // Check if it's a multisrc source
            if (lang == "multisrc" && parts.size > sourcesIdx + 3) {
                val theme = parts[sourcesIdx + 2]
                val name = parts[sourcesIdx + 3]
                return "multisrc/$theme/$name"
            }
            
            val name = parts[sourcesIdx + 2]
            if (name != "multisrc") {
                return "$lang/$name"
            }
        }
        
        return null
    }
    
    /**
     * Trigger a Gradle build for a specific source and reload it.
     */
    private suspend fun triggerBuild(sourceDir: String) {
        val parts = sourceDir.split("/")
        if (parts.size < 2) return
        
        val lang = parts[0]
        val name = parts[1]
        val langCap = lang.replaceFirstChar { it.uppercase() }
        
        println("   Building: $name ($lang)")
        onSourceReloaded?.invoke(name, false, "Building...")
        
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
                println("   Build successful: $name")
                
                // Reload the source
                val dex2jarLoader = Dex2JarLoader(sourceManager.getDependencies())
                val reloaded = dex2jarLoader.reloadSource(name)
                
                if (reloaded != null) {
                    sourceManager.registerSource(reloaded)
                    println("   Reloaded: ${reloaded.name}")
                    onSourceReloaded?.invoke(name, true, "Reloaded: ${reloaded.name}")
                } else {
                    println("   Failed to reload: $name")
                    onSourceReloaded?.invoke(name, false, "Build OK but reload failed")
                }
            } else {
                val errorLines = output.lines().filter { it.contains("error:", ignoreCase = true) }.take(5)
                val errorMsg = if (errorLines.isNotEmpty()) {
                    errorLines.joinToString("\n")
                } else {
                    "Build failed (exit: ${process.exitValue()})"
                }
                println("   Build failed: $name\n   $errorMsg")
                onSourceReloaded?.invoke(name, false, errorMsg)
            }
        } catch (e: Exception) {
            println("   Build error: ${e.message}")
            onSourceReloaded?.invoke(name, false, "Error: ${e.message}")
        }
    }
}
