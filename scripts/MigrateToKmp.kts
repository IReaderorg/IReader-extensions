#!/usr/bin/env kotlin

/**
 * Kotlin script to migrate IReader sources from JVM-only to KMP.
 * 
 * Usage:
 *   kotlin MigrateToKmp.kts <source-path>
 *   kotlin MigrateToKmp.kts sources/en/novelupdates
 *   kotlin MigrateToKmp.kts --all
 *   kotlin MigrateToKmp.kts --dry-run sources/en/novelupdates
 *   kotlin MigrateToKmp.kts --all --multisrc --report
 */

import java.io.File

// ============================================
// REPLACEMENT DEFINITIONS
// ============================================

val importReplacements = mapOf(
    // Jsoup -> Ksoup
    "import org.jsoup.Jsoup" to "import com.fleeksoft.ksoup.Ksoup",
    "import org.jsoup.nodes.Document" to "import com.fleeksoft.ksoup.nodes.Document",
    "import org.jsoup.nodes.Element" to "import com.fleeksoft.ksoup.nodes.Element",
    "import org.jsoup.select.Elements" to "import com.fleeksoft.ksoup.select.Elements",
    "import org.jsoup.nodes.TextNode" to "import com.fleeksoft.ksoup.nodes.TextNode",
    "import org.jsoup.parser.Parser" to "import com.fleeksoft.ksoup.parser.Parser",
    "import org.jsoup.safety.Safelist" to "import com.fleeksoft.ksoup.safety.Safelist",
    "import org.jsoup.safety.Whitelist" to "import com.fleeksoft.ksoup.safety.Safelist",
    "import org.jsoup.nodes.Attribute" to "import com.fleeksoft.ksoup.nodes.Attribute",
    "import org.jsoup.nodes.Node" to "import com.fleeksoft.ksoup.nodes.Node",
    "import org.jsoup.Connection" to "// import org.jsoup.Connection - Use Ktor HttpClient instead"
)

val codeReplacements = mapOf(
    "Jsoup.parse(" to "Ksoup.parse(",
    "Jsoup.clean(" to "Ksoup.clean(",
    "Jsoup.parseBodyFragment(" to "Ksoup.parseBodyFragment(",
    "Whitelist." to "Safelist.",
    "System.currentTimeMillis()" to "Clock.System.now().toEpochMilliseconds()"
)

val dateReplacements = mapOf(
    "import java.text.SimpleDateFormat" to "import kotlinx.datetime.*",
    "import java.text.DateFormat" to "// import java.text.DateFormat - Use kotlinx.datetime",
    "import java.util.Locale" to "// import java.util.Locale - Not needed for KMP",
    "import java.util.Calendar" to "// import java.util.Calendar - Use kotlinx.datetime",
    "import java.util.Date" to "// import java.util.Date - Use kotlinx.datetime",
    "import java.util.TimeZone" to "// import java.util.TimeZone - Use kotlinx.datetime"
)

val collectionReplacements = mapOf(
    "import java.util.ArrayList" to "// import java.util.ArrayList - Use mutableListOf()",
    "import java.util.HashMap" to "// import java.util.HashMap - Use mutableMapOf()",
    "import java.util.HashSet" to "// import java.util.HashSet - Use mutableSetOf()"
)

val urlReplacements = mapOf(
    "import java.net.URLEncoder" to "// import java.net.URLEncoder - Use encodeURLParameter()",
    "import java.net.URLDecoder" to "// import java.net.URLDecoder - Use decodeURLPart()",
    "import java.net.URL" to "// import java.net.URL - Use Ktor Url"
)

// ============================================
// DATA CLASSES
// ============================================

data class MigrationResult(
    val source: String,
    val file: String,
    val changes: List<String>,
    val warnings: List<String>
)

// ============================================
// HELPER FUNCTIONS
// ============================================

fun log(message: String, level: String = "INFO") {
    val color = when (level) {
        "SUCCESS" -> "\u001B[32m"
        "WARNING" -> "\u001B[33m"
        "ERROR" -> "\u001B[31m"
        "DEBUG" -> "\u001B[90m"
        else -> "\u001B[0m"
    }
    println("$color[$level] $message\u001B[0m")
}

// ============================================
// MIGRATION FUNCTIONS
// ============================================

fun migrateKotlinFile(file: File, sourceName: String, dryRun: Boolean = false): MigrationResult {
    var content = file.readText()
    val original = content
    val changes = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    
    // Combine all replacements
    val allReplacements = importReplacements + codeReplacements + dateReplacements + 
                          collectionReplacements + urlReplacements
    
    // Apply replacements
    allReplacements.forEach { (old, new) ->
        if (content.contains(old)) {
            content = content.replace(old, new)
            changes.add("$old -> $new")
        }
    }
    
    // Remove duplicate kotlinx.datetime imports
    content = content.replace(Regex("(import kotlinx\\.datetime\\.\\*\\r?\\n)+"), "import kotlinx.datetime.*\n")
    
    // Check for remaining JVM-only code
    if (content.contains("SimpleDateFormat")) {
        warnings.add("Contains SimpleDateFormat - needs manual migration")
    }
    if (content.contains("Calendar.getInstance")) {
        warnings.add("Contains Calendar.getInstance - needs manual migration")
    }
    if (Regex("java\\.io\\.").containsMatchIn(content)) {
        warnings.add("Contains java.io.* - may need KMP alternative")
    }
    if (Regex("java\\.net\\.").containsMatchIn(content)) {
        warnings.add("Contains java.net.* - may need KMP alternative")
    }
    
    // Write changes
    if (content != original) {
        if (dryRun) {
            log("Would modify: ${file.name}", "INFO")
            changes.forEach { log("  - $it", "DEBUG") }
        } else {
            file.writeText(content)
            log("Modified: ${file.name}", "SUCCESS")
        }
    }
    
    warnings.forEach { log("  WARNING: $it", "WARNING") }
    
    return MigrationResult(sourceName, file.name, changes, warnings)
}

fun migrateSource(sourceDir: File, dryRun: Boolean = false): List<MigrationResult> {
    val sourceName = sourceDir.name
    
    log("========================================")
    log("Migrating: $sourceName")
    log("========================================")
    
    if (!sourceDir.exists()) {
        log("Source directory not found: ${sourceDir.path}", "ERROR")
        return emptyList()
    }
    
    val ktFiles = sourceDir.walkTopDown()
        .filter { it.extension == "kt" }
        .toList()
    
    log("Found ${ktFiles.size} Kotlin files")
    
    val results = ktFiles.map { file ->
        migrateKotlinFile(file, sourceName, dryRun)
    }
    
    val modified = results.count { it.changes.isNotEmpty() }
    log("Modified $modified / ${ktFiles.size} files", "SUCCESS")
    
    return results
}

fun migrateAllSources(rootDir: File, dryRun: Boolean = false, includeMultisrc: Boolean = false): List<MigrationResult> {
    val sourcesDir = File(rootDir, "sources")
    if (!sourcesDir.exists()) {
        log("Sources directory not found", "ERROR")
        return emptyList()
    }
    
    val allResults = mutableListOf<MigrationResult>()
    var migrated = 0
    var skipped = 0
    var failed = 0
    
    sourcesDir.listFiles()?.filter { it.isDirectory }?.forEach { langDir ->
        if (langDir.name == "multisrc" && !includeMultisrc) return@forEach
        if (langDir.name == "common") return@forEach
        
        log("Processing language: ${langDir.name}")
        
        langDir.listFiles()?.filter { it.isDirectory && it.name != "build" }?.forEach { source ->
            val hasBuildFile = File(source, "build.gradle.kts").exists()
            val hasKtFiles = source.walkTopDown().any { it.extension == "kt" }
            
            if (!hasBuildFile && !hasKtFiles) return@forEach
            
            try {
                val results = migrateSource(source, dryRun)
                allResults.addAll(results)
                if (results.any { it.changes.isNotEmpty() }) {
                    migrated++
                } else {
                    skipped++
                }
            } catch (e: Exception) {
                log("Failed to migrate ${source.path}: ${e.message}", "ERROR")
                failed++
            }
        }
    }
    
    // Summary
    log("")
    log("========================================")
    log("MIGRATION SUMMARY")
    log("========================================")
    log("Migrated: $migrated", "SUCCESS")
    log("Skipped:  $skipped", "WARNING")
    log("Failed:   $failed", "ERROR")
    log("========================================")
    
    return allResults
}

fun exportReport(results: List<MigrationResult>, outputDir: File) {
    if (results.isEmpty()) {
        log("No migration data to report", "WARNING")
        return
    }
    
    // CSV report
    val csvFile = File(outputDir, "migration-report.csv")
    csvFile.writeText("Source,File,Change,Type\n")
    results.forEach { result ->
        result.changes.forEach { change ->
            csvFile.appendText("${result.source},${result.file},\"$change\",Replaced\n")
        }
        result.warnings.forEach { warning ->
            csvFile.appendText("${result.source},${result.file},\"$warning\",NeedsAttention\n")
        }
    }
    log("Report saved to: ${csvFile.path}", "SUCCESS")
    
    // Summary
    val summaryFile = File(outputDir, "migration-summary.txt")
    val totalChanges = results.sumOf { it.changes.size }
    val totalWarnings = results.sumOf { it.warnings.size }
    val sourcesWithWarnings = results.filter { it.warnings.isNotEmpty() }
        .groupBy { it.source }
        .map { "${it.key}: ${it.value.flatMap { r -> r.warnings }.joinToString(", ")}" }
    
    summaryFile.writeText("""
IReader KMP Migration Report
============================
Generated: ${java.time.LocalDateTime.now()}

Total Changes: $totalChanges
Total Warnings: $totalWarnings

Sources Needing Attention:
${sourcesWithWarnings.take(20).joinToString("\n") { "  - $it" }}
    """.trimIndent())
    log("Summary saved to: ${summaryFile.path}", "SUCCESS")
}

// ============================================
// MAIN
// ============================================

val args = args.toMutableList()
val dryRun = args.remove("--dry-run")
val migrateAll = args.remove("--all")
val includeMultisrc = args.remove("--multisrc")
val generateReport = args.remove("--report")

val scriptDir = File(System.getProperty("user.dir"))
val rootDir = if (scriptDir.name == "scripts") scriptDir.parentFile else scriptDir

when {
    migrateAll -> {
        val results = migrateAllSources(rootDir, dryRun, includeMultisrc)
        if (generateReport) {
            exportReport(results, rootDir)
        }
    }
    args.isNotEmpty() -> {
        val sourcePath = args.first()
        val sourceDir = if (File(sourcePath).isAbsolute) File(sourcePath) 
                        else File(rootDir, sourcePath)
        val results = migrateSource(sourceDir, dryRun)
        if (generateReport) {
            exportReport(results, rootDir)
        }
    }
    else -> {
        println("""
IReader Source KMP Migration Script (Kotlin)
=============================================

Usage:
  kotlin MigrateToKmp.kts <source-path>
  kotlin MigrateToKmp.kts sources/en/novelupdates
  kotlin MigrateToKmp.kts --all
  kotlin MigrateToKmp.kts --all --multisrc
  kotlin MigrateToKmp.kts --all --report
  kotlin MigrateToKmp.kts --dry-run sources/en/novelupdates

Options:
  --all         Migrate all sources
  --multisrc    Include multisrc sources
  --dry-run     Preview changes without making them
  --report      Generate migration report

What this script does:
  - Converts Jsoup imports to Ksoup (KMP-compatible HTML parser)
  - Updates Jsoup.parse() calls to Ksoup.parse()
  - Updates date parsing imports to kotlinx-datetime
  - Converts java.util.* collections to Kotlin stdlib
  - Flags JVM-only code that needs manual attention
        """.trimIndent())
    }
}
