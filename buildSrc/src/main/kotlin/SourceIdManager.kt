import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.io.File
import java.security.MessageDigest

/**
 * Gradle task to manage source IDs across the project.
 * 
 * Usage:
 *   ./gradlew listSourceIds          - List all source IDs
 *   ./gradlew generateSourceId       - Generate ID for a new source
 *   ./gradlew checkSourceIdCollisions - Check for ID collisions
 *   ./gradlew migrateToAutoSourceId  - Generate migration code
 */
abstract class SourceIdManagerTask : DefaultTask() {
    
    @get:Input
    @get:Optional
    var sourceName: String? = null
    
    @get:Input
    @get:Optional
    var sourceLang: String? = null
    
    @get:Input
    @get:Optional
    var action: String = "list"
    
    @TaskAction
    fun execute() {
        when (action) {
            "list" -> listAllSourceIds()
            "generate" -> generateSourceId()
            "check" -> checkCollisions()
            "migrate" -> generateMigrationCode()
            else -> println("Unknown action: $action. Use: list, generate, check, migrate")
        }
    }
    
    private fun listAllSourceIds() {
        println("\n=== Source ID Registry ===\n")
        
        val sources = collectAllSources()
        val grouped = sources.groupBy { it.lang }
        
        grouped.toSortedMap().forEach { (lang, langSources) ->
            println("[$lang] (${langSources.size} sources)")
            langSources.sortedBy { it.name }.forEach { source ->
                println("  ${source.name.padEnd(30)} ID: ${source.id}")
            }
            println()
        }
        
        println("Total: ${sources.size} sources")
    }
    
    private fun generateSourceId() {
        val name = sourceName ?: run {
            println("Error: Please provide -PsourceName=<name>")
            return
        }
        val lang = sourceLang ?: "en"
        
        val id = generateId(name, lang, 1)
        
        println("\n=== Generated Source ID ===")
        println("Name: $name")
        println("Lang: $lang")
        println("ID:   $id")
        println()
        println("Add to your source:")
        println("  @AutoSourceId")
        println("  // or manually:")
        println("  override val id: Long get() = ${id}L")
    }
    
    private fun checkCollisions() {
        println("\n=== Checking for ID Collisions ===\n")
        
        val sources = collectAllSources()
        val idGroups = sources.groupBy { it.id }
        val collisions = idGroups.filter { it.value.size > 1 }
        
        if (collisions.isEmpty()) {
            println("✓ No collisions found! All ${sources.size} sources have unique IDs.")
        } else {
            println("⚠ Found ${collisions.size} collision(s):\n")
            collisions.forEach { (id, conflicting) ->
                println("ID: $id")
                conflicting.forEach { source ->
                    println("  - ${source.name} (${source.lang}) in ${source.path}")
                }
                println()
            }
        }
    }
    
    private fun generateMigrationCode() {
        println("\n=== Migration to @AutoSourceId ===\n")
        
        val sources = collectAllSources()
        
        sources.forEach { source ->
            val autoId = generateId(source.name, source.lang, 1)
            val needsSeed = autoId != source.id
            
            println("// ${source.name} (${source.lang})")
            if (needsSeed) {
                // Find a seed that generates the same ID
                val seed = findSeedForId(source.id, source.lang)
                if (seed != null) {
                    println("@AutoSourceId(seed = \"$seed\")")
                } else {
                    println("// Cannot auto-migrate - keep manual ID: ${source.id}")
                    println("// @AutoSourceId  // Would generate: $autoId")
                }
            } else {
                println("@AutoSourceId  // Generates same ID: ${source.id}")
            }
            println()
        }
    }
    
    private fun collectAllSources(): List<SourceInfo> {
        val sources = mutableListOf<SourceInfo>()
        
        // Scan sources directory
        val sourcesDir = project.file("sources")
        if (sourcesDir.exists()) {
            sourcesDir.walkTopDown()
                .filter { it.name == "build.gradle.kts" && it.parentFile.name != "sources" }
                .forEach { buildFile ->
                    parseSourceFromBuildFile(buildFile)?.let { sources.add(it) }
                }
        }
        
        // Scan sources-v5-batch directory
        val v5Dir = project.file("sources-v5-batch")
        if (v5Dir.exists()) {
            v5Dir.walkTopDown()
                .filter { it.name == "build.gradle.kts" && it.parentFile.name != "sources-v5-batch" }
                .forEach { buildFile ->
                    parseSourceFromBuildFile(buildFile)?.let { sources.add(it) }
                }
        }
        
        return sources
    }
    
    private fun parseSourceFromBuildFile(buildFile: File): SourceInfo? {
        val content = buildFile.readText()
        
        // Extract Extension parameters
        val nameMatch = Regex("""name\s*=\s*"([^"]+)"""").find(content)
        val langMatch = Regex("""lang\s*=\s*"([^"]+)"""").find(content)
        
        val name = nameMatch?.groupValues?.get(1) ?: return null
        val lang = langMatch?.groupValues?.get(1) ?: "en"
        
        // Check for explicit sourceId
        val idMatch = Regex("""sourceId\s*=\s*(\d+)""").find(content)
        val id = idMatch?.groupValues?.get(1)?.toLongOrNull() ?: generateId(name, lang, 1)
        
        return SourceInfo(
            name = name,
            lang = lang,
            id = id,
            path = buildFile.parentFile.relativeTo(project.rootDir).path
        )
    }
    
    private fun generateId(name: String, lang: String, version: Int): Long {
        val key = "${name.lowercase()}/$lang/$version"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }
    
    private fun findSeedForId(targetId: Long, lang: String): String? {
        // Try common variations
        val variations = listOf(
            "v1", "v2", "source", "novel", "read"
        )
        
        for (i in 1..100) {
            val seed = "seed$i"
            if (generateId(seed, lang, 1) == targetId) {
                return seed
            }
        }
        
        return null
    }
    
    data class SourceInfo(
        val name: String,
        val lang: String,
        val id: Long,
        val path: String
    )
}

// Register tasks
fun org.gradle.api.Project.registerSourceIdTasks() {
    tasks.register("listSourceIds", SourceIdManagerTask::class.java) {
        group = "source management"
        description = "List all source IDs in the project"
        action = "list"
    }
    
    tasks.register("generateSourceId", SourceIdManagerTask::class.java) {
        group = "source management"
        description = "Generate a source ID for a new source"
        action = "generate"
        sourceName = project.findProperty("sourceName") as? String
        sourceLang = project.findProperty("sourceLang") as? String ?: "en"
    }
    
    tasks.register("checkSourceIdCollisions", SourceIdManagerTask::class.java) {
        group = "source management"
        description = "Check for source ID collisions"
        action = "check"
    }
    
    tasks.register("migrateToAutoSourceId", SourceIdManagerTask::class.java) {
        group = "source management"
        description = "Generate migration code for @AutoSourceId"
        action = "migrate"
    }
}
