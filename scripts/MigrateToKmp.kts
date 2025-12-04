#!/usr/bin/env kotlin

/**
 * Kotlin script to migrate IReader sources from JVM-only to KMP.
 * 
 * Usage:
 *   kotlin MigrateToKmp.kts <source-path>
 *   kotlin MigrateToKmp.kts sources/en/novelupdates
 *   kotlin MigrateToKmp.kts --all
 *   kotlin MigrateToKmp.kts --dry-run sources/en/novelupdates
 */

import java.io.File

// Import replacements
val importReplacements = mapOf(
    "import org.jsoup.Jsoup" to "import com.fleeksoft.ksoup.Ksoup",
    "import org.jsoup.nodes.Document" to "import com.fleeksoft.ksoup.nodes.Document",
    "import org.jsoup.nodes.Element" to "import com.fleeksoft.ksoup.nodes.Element",
    "import org.jsoup.select.Elements" to "import com.fleeksoft.ksoup.select.Elements",
    "import org.jsoup.nodes.TextNode" to "import com.fleeksoft.ksoup.nodes.TextNode",
    "import org.jsoup.parser.Parser" to "import com.fleeksoft.ksoup.parser.Parser",
    "import org.jsoup.safety.Safelist" to "import com.fleeksoft.ksoup.safety.Safelist",
    "import org.jsoup.safety.Whitelist" to "import com.fleeksoft.ksoup.safety.Safelist",
    "import org.jsoup.Connection" to "// import org.jsoup.Connection - Use Ktor HttpClient instead"
)

// Code replacements
val codeReplacements = mapOf(
    "Jsoup.parse(" to "Ksoup.parse(",
    "Jsoup.clean(" to "Ksoup.clean(",
    "Whitelist." to "Safelist.",
    "System.currentTimeMillis()" to "Clock.System.now().toEpochMilliseconds()"
)

// Date replacements
val dateReplacements = mapOf(
    "import java.text.SimpleDateFormat" to "import kotlinx.datetime.*",
    "import java.util.Locale" to "// import java.util.Locale - Not needed for KMP",
    "import java.util.Calendar" to "// import java.util.Calendar - Use kotlinx.datetime instead",
    "import java.util.Date" to "// import java.util.Date - Use kotlinx.datetime instead"
)

fun log(message: String, level: String = "INFO") {
    val color = when (level) {
        "SUCCESS" -> "\u001B[32m"
        "WARNING" -> "\u001B[33m"
        "ERROR" -> "\u001B[31m"
        else -> "\u001B[0m"
    }
    println("$color[$level] $message\u001B[0m")
}

fun migrateKotlinFile(file: File, dryRun: Boolean = false): Boolean {
    var content = file.readText()
    val original = content
    val changes = mutableListOf<String>()
    
    // Apply all replacements
    (importReplacements + codeReplacements + dateReplacements).forEach { (old, new) ->
        if (content.contains(old)) {
            content = content.replace(old, new)
            changes.add("$old -> $new")
        }
    }
    
    // Remove duplicate kotlinx.datetime imports
    content = content.replace(Regex("(import kotlinx\\.datetime\\.\\*\\r?\\n)+"), "import kotlinx.datetime.*\n")
    
    if (content != original) {
        if (dryRun) {
            log("Would modify: ${file.path}", "INFO")
            changes.forEach { log("  - $it", "INFO") }
        } else {
            file.writeText(content)
            log("Modified: ${file.path}", "SUCCESS")
            changes.forEach { log("  - $it", "INFO") }
        }
        return true
    }
    return false
}

fun getPackageName(sourceDir: File): String? {
    // Search in main/src first, then src/main/kotlin
    val searchPaths = listOf(
        File(sourceDir, "main/src"),
        File(sourceDir, "src/main/kotlin"),
        sourceDir
    )
    
    val ktFiles = searchPaths
        .filter { it.exists() }
        .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" && f.name != "Init.kt" }.toList() }
        .take(1)
    
    if (ktFiles.isEmpty()) return null
    
    val content = ktFiles.first().readText()
    // Find package declaration at start of file
    for (line in content.lines()) {
        val trimmed = line.trim()
        val match = Regex("^package\\s+([\\w.]+)").find(trimmed)
        if (match != null) return match.groupValues[1]
        // Skip empty lines and comments
        if (trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")) break
    }
    return null
}

fun getClassName(sourceDir: File): String? {
    // Search in main/src first
    val searchPaths = listOf(
        File(sourceDir, "main/src"),
        File(sourceDir, "src/main/kotlin"),
        sourceDir
    )
    
    val ktFiles = searchPaths
        .filter { it.exists() }
        .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" && f.name != "Init.kt" }.toList() }
    
    for (file in ktFiles) {
        val content = file.readText()
        // Match abstract class first (common pattern for sources)
        Regex("abstract\\s+class\\s+(\\w+)\\s*[\\(:]").find(content)?.let { return it.groupValues[1] }
        Regex("class\\s+(\\w+)\\s*[\\(:].*SourceFactory").find(content)?.let { return it.groupValues[1] }
        Regex("class\\s+(\\w+)\\s*[\\(:]").find(content)?.let { return it.groupValues[1] }
    }
    return null
}


fun createKmpStructure(sourceDir: File, dryRun: Boolean = false): Boolean {
    val srcDir = File(sourceDir, "src")
    val commonMainDir = File(srcDir, "commonMain")
    
    if (commonMainDir.exists()) {
        log("Source already has KMP structure: ${sourceDir.path}", "WARNING")
        return false
    }
    
    val packageName = getPackageName(sourceDir) ?: run {
        log("Could not determine package name for: ${sourceDir.path}", "ERROR")
        return false
    }
    
    val packagePath = packageName.replace('.', '/')
    val commonMainKotlinDir = File(srcDir, "commonMain/kotlin/$packagePath")
    val jsMainKotlinDir = File(srcDir, "jsMain/kotlin/$packagePath")
    
    if (dryRun) {
        log("Would create: $commonMainKotlinDir", "INFO")
        log("Would create: $jsMainKotlinDir", "INFO")
        return true
    }
    
    commonMainKotlinDir.mkdirs()
    jsMainKotlinDir.mkdirs()
    log("Created KMP directory structure", "SUCCESS")
    return true
}

fun moveSourcesToCommonMain(sourceDir: File, dryRun: Boolean = false): Boolean {
    val packageName = getPackageName(sourceDir) ?: return false
    val packagePath = packageName.replace('.', '/')
    val commonMainDir = File(sourceDir, "src/commonMain/kotlin/$packagePath")
    
    // Find source files in old locations
    val oldLocations = listOf(
        File(sourceDir, "main/src"),
        File(sourceDir, "src/main/kotlin")
    )
    
    val ktFiles = oldLocations
        .filter { it.exists() }
        .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" }.toList() }
    
    if (ktFiles.isEmpty()) {
        log("No Kotlin files found to move", "WARNING")
        return false
    }
    
    ktFiles.forEach { file ->
        val destFile = File(commonMainDir, file.name)
        
        if (dryRun) {
            log("Would move: ${file.path} -> ${destFile.path}", "INFO")
        } else {
            // Migrate content first
            migrateKotlinFile(file)
            // Copy to new location
            file.copyTo(destFile, overwrite = true)
            log("Moved: ${file.name} -> commonMain", "SUCCESS")
        }
    }
    return true
}

fun createJsInitFile(sourceDir: File, dryRun: Boolean = false): Boolean {
    val packageName = getPackageName(sourceDir) ?: return false
    val className = getClassName(sourceDir) ?: return false
    val packagePath = packageName.replace('.', '/')
    val jsMainDir = File(sourceDir, "src/jsMain/kotlin/$packagePath")
    val initFile = File(jsMainDir, "Init.kt")
    
    val sourceId = sourceDir.name.lowercase()
    
    val content = """
package $packageName

import kotlin.js.JsExport

/**
 * Initialize $className source for iOS/JS runtime.
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
fun init$className() {
    // Register source with JS runtime
    // registerSource("$sourceId") { deps -> $className(deps.toDependencies()) }
    console.log("$className source initialized")
}
""".trimIndent()
    
    if (dryRun) {
        log("Would create Init.kt at: ${initFile.path}", "INFO")
        return true
    }
    
    jsMainDir.mkdirs()
    initFile.writeText(content)
    log("Created Init.kt for: $className", "SUCCESS")
    return true
}

fun migrateSource(sourceDir: File, dryRun: Boolean = false) {
    log("========================================")
    log("Migrating source: ${sourceDir.path}")
    log("========================================")
    
    if (!sourceDir.exists()) {
        log("Source directory not found: ${sourceDir.path}", "ERROR")
        return
    }
    
    log("Step 1: Creating KMP directory structure...")
    createKmpStructure(sourceDir, dryRun)
    
    log("Step 2: Moving and migrating source files...")
    moveSourcesToCommonMain(sourceDir, dryRun)
    
    log("Step 3: Creating JS Init file...")
    createJsInitFile(sourceDir, dryRun)
    
    log("Migration complete for: ${sourceDir.path}", "SUCCESS")
}

fun migrateAllSources(rootDir: File, dryRun: Boolean = false) {
    val sourcesDir = File(rootDir, "sources")
    if (!sourcesDir.exists()) {
        log("Sources directory not found", "ERROR")
        return
    }
    
    var migrated = 0
    var failed = 0
    
    sourcesDir.listFiles()?.filter { it.isDirectory && it.name != "multisrc" }?.forEach { langDir ->
        langDir.listFiles()?.filter { it.isDirectory }?.forEach { source ->
            if (File(source, "build.gradle.kts").exists()) {
                try {
                    migrateSource(source, dryRun)
                    migrated++
                } catch (e: Exception) {
                    log("Failed to migrate ${source.path}: ${e.message}", "ERROR")
                    failed++
                }
            }
        }
    }
    
    log("========================================")
    log("Migration Summary: Migrated=$migrated, Failed=$failed")
    log("========================================")
}

// Main
val args = args.toMutableList()
val dryRun = args.remove("--dry-run")
val migrateAll = args.remove("--all")

val scriptDir = File(System.getProperty("user.dir"))

when {
    migrateAll -> migrateAllSources(scriptDir.parentFile ?: scriptDir, dryRun)
    args.isNotEmpty() -> {
        val sourcePath = args.first()
        val sourceDir = if (File(sourcePath).isAbsolute) File(sourcePath) 
                        else File(scriptDir.parentFile ?: scriptDir, sourcePath)
        migrateSource(sourceDir, dryRun)
    }
    else -> {
        println("""
IReader Source KMP Migration Script (Kotlin)
=============================================

Usage:
  kotlin MigrateToKmp.kts <source-path>
  kotlin MigrateToKmp.kts sources/en/novelupdates
  kotlin MigrateToKmp.kts --all
  kotlin MigrateToKmp.kts --dry-run sources/en/novelupdates

Options:
  --all       Migrate all sources
  --dry-run   Preview changes without making them
        """.trimIndent())
    }
}
