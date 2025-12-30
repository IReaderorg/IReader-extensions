package ireader.testserver

import ireader.core.source.CatalogSource
import ireader.core.source.Dependencies
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLClassLoader

/**
 * Dynamically loads IReader sources from compiled class directories.
 * 
 * Each source is loaded in its own isolated classloader to avoid conflicts
 * since all sources generate a class named `tachiyomix.extension.Extension`.
 */
class SourceLoader(private val deps: Dependencies) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Discovers and loads all compiled sources from the sources directory.
     * Returns a list of successfully loaded CatalogSource instances.
     */
    fun loadAllSources(sourcesDir: File = File("sources")): List<CatalogSource> {
        if (!sourcesDir.exists()) {
            println("   Sources directory not found: ${sourcesDir.absolutePath}")
            return emptyList()
        }
        
        val loadedSources = mutableListOf<CatalogSource>()
        val sourceBuilds = findSourceBuilds(sourcesDir)
        
        println("   Found ${sourceBuilds.size} compiled source(s)")
        
        sourceBuilds.forEach { sourceBuild ->
            try {
                val source = loadSourceFromBuild(sourceBuild)
                if (source != null) {
                    loadedSources.add(source)
                    println("   ✓ ${source.name} (${source.lang}) - ID: ${source.id}")
                }
            } catch (e: Exception) {
                println("   ✗ Failed to load ${sourceBuild.name}: ${e.message}")
            }
        }
        
        return loadedSources
    }
    
    /**
     * Finds all source build directories that have compiled classes.
     */
    private fun findSourceBuilds(sourcesDir: File): List<SourceBuild> {
        val builds = mutableListOf<SourceBuild>()
        
        // Walk through sources directory looking for build outputs
        sourcesDir.walkTopDown().maxDepth(6).forEach { file ->
            // Look for source-index.json which indicates a compiled source
            if (file.name == "source-index.json" && 
                file.parentFile.name == "resources" &&
                file.parentFile.parentFile.name.contains("Debug")) {
                
                val kspDir = file.parentFile.parentFile // e.g., enDebug
                val buildDir = kspDir.parentFile.parentFile.parentFile // build directory
                
                // Find corresponding kotlin-classes directory
                val variantName = kspDir.name // e.g., "enDebug"
                val classesDir = File(buildDir, "tmp/kotlin-classes/$variantName")
                
                if (classesDir.exists()) {
                    // Parse source-index.json to get source info
                    try {
                        val content = file.readText()
                        val jsonArray = json.parseToJsonElement(content).jsonArray
                        
                        jsonArray.firstOrNull()?.let { element ->
                            val obj = element.jsonObject
                            val name = obj["name"]?.jsonPrimitive?.content ?: return@let
                            val lang = obj["lang"]?.jsonPrimitive?.content ?: "en"
                            
                            builds.add(SourceBuild(
                                name = name,
                                lang = lang,
                                classesDir = classesDir,
                                sourceIndexFile = file
                            ))
                        }
                    } catch (e: Exception) {
                        // Skip invalid source-index.json
                    }
                }
            }
        }
        
        return builds.distinctBy { it.name }
    }
    
    /**
     * Loads a single source from its build directory using an isolated classloader.
     */
    private fun loadSourceFromBuild(build: SourceBuild): CatalogSource? {
        // Create an isolated classloader for this source
        // This prevents conflicts between Extension classes from different sources
        val classLoader = URLClassLoader(
            arrayOf(build.classesDir.toURI().toURL()),
            this::class.java.classLoader
        )
        
        // Try to load the Extension class
        return try {
            val extensionClass = classLoader.loadClass("tachiyomix.extension.Extension")
            
            if (!CatalogSource::class.java.isAssignableFrom(extensionClass)) {
                return null
            }
            
            val constructor = extensionClass.constructors.firstOrNull { 
                it.parameterCount == 1 && it.parameterTypes[0] == Dependencies::class.java
            } ?: return null
            
            constructor.newInstance(deps) as CatalogSource
        } catch (e: ClassNotFoundException) {
            // Extension class not found, try alternative class names
            loadAlternativeExtension(classLoader, build)
        } catch (e: LinkageError) {
            // Class already loaded by parent classloader
            null
        }
    }
    
    /**
     * Tries to load extension using alternative class naming patterns.
     */
    private fun loadAlternativeExtension(classLoader: ClassLoader, build: SourceBuild): CatalogSource? {
        // Try common patterns for extension class names
        val possibleClassNames = listOf(
            "ireader.${build.name.lowercase()}.${build.name}Extension",
            "ireader.${build.name.lowercase().replace(" ", "")}.${build.name.replace(" ", "")}Extension"
        )
        
        for (className in possibleClassNames) {
            try {
                val clazz = classLoader.loadClass(className)
                if (CatalogSource::class.java.isAssignableFrom(clazz) &&
                    !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) {
                    
                    val constructor = clazz.constructors.firstOrNull { 
                        it.parameterCount == 1 && it.parameterTypes[0] == Dependencies::class.java
                    } ?: continue
                    
                    return constructor.newInstance(deps) as CatalogSource
                }
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        
        return null
    }
}

/**
 * Represents a compiled source build.
 */
data class SourceBuild(
    val name: String,
    val lang: String,
    val classesDir: File,
    val sourceIndexFile: File
)
