/**
 * JS Sources Compilation Module
 * 
 * Compiles extension sources to JavaScript for iOS.
 * 
 * IMPORTANT: This module produces source-only JS files (~10-30KB each).
 * Runtime dependencies (Kotlin stdlib, Ktor, Ksoup, source-api) are provided
 * by the main IReader app's runtime.js (~800KB), NOT bundled here.
 * 
 * Output: Individual source JS files that register with SourceRegistry
 * 
 * USAGE: Run the Android KSP task first, then build JS:
 *   ./gradlew <project>:kspEnReleaseKotlin (for each JS-enabled source)
 *   ./gradlew :js-sources:jsBrowserProductionWebpack
 */

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

data class JsSourceConfig(
    val name: String,      // Display name (matches @Extension)
    val id: String,        // Lowercase ID for file naming
    val lang: String,
    val projectPath: String,  // Gradle project path for dependency
    val sourceDir: String,
    val generatedDir: String
)

/**
 * Auto-discover JS-enabled sources by scanning build.gradle.kts files.
 * Looks for extensions with enableJs = true.
 */
fun discoverJsSources(): List<JsSourceConfig> {
    val sources = mutableListOf<JsSourceConfig>()
    val sourcesDir = rootDir.resolve("sources")
    
    // Scan individual sources (sources/<lang>/<name>)
    sourcesDir.listFiles()?.filter { it.isDirectory && it.name != "multisrc" }?.forEach { langDir ->
        langDir.listFiles()?.filter { it.isDirectory }?.forEach { sourceDir ->
            val buildFile = sourceDir.resolve("build.gradle.kts")
            if (buildFile.exists()) {
                val config = parseExtensionConfig(buildFile, langDir.name, sourceDir.name, "individual")
                if (config != null) {
                    sources.add(config)
                }
            }
        }
    }
    
    // Scan multisrc sources (sources/multisrc/<theme>)
    val multisrcDir = sourcesDir.resolve("multisrc")
    if (multisrcDir.exists()) {
        multisrcDir.listFiles()?.filter { it.isDirectory }?.forEach { themeDir ->
            val buildFile = themeDir.resolve("build.gradle.kts")
            if (buildFile.exists()) {
                // Multisrc can have multiple extensions, parse each
                val configs = parseMultisrcExtensionConfigs(buildFile, themeDir.name)
                sources.addAll(configs)
            }
        }
    }
    
    return sources
}

/**
 * Parse a build.gradle.kts file to extract Extension config with enableJs = true.
 */
fun parseExtensionConfig(buildFile: File, lang: String, sourceName: String, type: String): JsSourceConfig? {
    val content = buildFile.readText()
    
    // Check if enableJs = true is present
    if (!content.contains("enableJs") || !content.contains("enableJs = true") && !content.contains("enableJs=true")) {
        return null
    }
    
    // Extract extension name from the Extension(...) block
    val nameRegex = """Extension\s*\([^)]*name\s*=\s*"([^"]+)"""".toRegex()
    val nameMatch = nameRegex.find(content)
    val extensionName = nameMatch?.groupValues?.get(1) ?: sourceName
    
    // Determine source directory (default is "main")
    val sourceDirRegex = """sourceDir\s*=\s*"([^"]+)"""".toRegex()
    val sourceDirMatch = sourceDirRegex.find(content)
    val sourceSubDir = sourceDirMatch?.groupValues?.get(1) ?: "main"
    
    val projectPath = ":extensions:$type:$lang:$sourceName"
    val baseDir = if (type == "individual") "sources/$lang/$sourceName" else "sources/multisrc/$sourceName"
    
    return JsSourceConfig(
        name = extensionName,
        id = sourceName.lowercase(),
        lang = lang,
        projectPath = projectPath,
        sourceDir = "../$baseDir/$sourceSubDir/src",
        generatedDir = "../$baseDir/build/generated/ksp/${lang}Release/kotlin"
    )
}

/**
 * Parse multisrc build.gradle.kts for extensions with enableJs = true.
 */
fun parseMultisrcExtensionConfigs(buildFile: File, themeName: String): List<JsSourceConfig> {
    val content = buildFile.readText()
    val configs = mutableListOf<JsSourceConfig>()
    
    // Find all Extension blocks with enableJs = true
    val extensionBlockRegex = """Extension\s*\(([^)]+enableJs\s*=\s*true[^)]*)\)""".toRegex()
    
    extensionBlockRegex.findAll(content).forEach { match ->
        val block = match.groupValues[1]
        
        // Extract name
        val nameRegex = """name\s*=\s*"([^"]+)"""".toRegex()
        val name = nameRegex.find(block)?.groupValues?.get(1) ?: return@forEach
        
        // Extract lang
        val langRegex = """lang\s*=\s*"([^"]+)"""".toRegex()
        val lang = langRegex.find(block)?.groupValues?.get(1) ?: "en"
        
        // Extract sourceDir (for flavor)
        val sourceDirRegex = """sourceDir\s*=\s*"([^"]+)"""".toRegex()
        val sourceDir = sourceDirRegex.find(block)?.groupValues?.get(1) ?: "main"
        
        val flavor = if (sourceDir == "main") lang else "$sourceDir-$lang"
        
        configs.add(JsSourceConfig(
            name = name,
            id = name.lowercase().replace(" ", ""),
            lang = lang,
            projectPath = ":extensions:multisrc:$themeName",
            sourceDir = "../sources/multisrc/$themeName/$sourceDir/src",
            generatedDir = "../sources/multisrc/$themeName/build/generated/ksp/${flavor}Release/kotlin"
        ))
    }
    
    return configs
}

// Auto-discover JS-enabled sources
val jsSources: List<JsSourceConfig> = discoverJsSources().also { sources ->
    if (sources.isNotEmpty()) {
        logger.lifecycle("Discovered ${sources.size} JS-enabled source(s):")
        sources.forEach { logger.lifecycle("  - ${it.name} (${it.lang})") }
    } else {
        logger.lifecycle("No JS-enabled sources found. Set enableJs = true in Extension config to enable.")
    }
}

kotlin {
    js(IR) {
        // Use browser target with webpack for bundling
        browser {
            webpackTask {
                mainOutputFileName = "sources-bundle.js"
            }
        }
        
        // Executable mode - bundles all code including dependencies
        // This ensures SourceFactory and all base classes are included
        binaries.executable()
        
        // Compiler options
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    // Keep member names readable for debugging
                    freeCompilerArgs.add("-Xir-minimized-member-names=false")
                }
            }
        }
    }
    
    sourceSets {
        val jsMain by getting {
            // Include source files from each JS-enabled extension
            jsSources.forEach { source ->
                // Source implementation files
                kotlin.srcDir(source.sourceDir)
            }
            
            // Include filtered KSP-generated files (excludes Extension.kt which conflicts)
            // The copyKspGeneratedFiles task filters and copies only needed files
            kotlin.srcDir(layout.buildDirectory.dir("ksp-js-sources"))
            
            // Include JS-only registration files (renamed from .kt.txt to .kt)
            // These are generated by KSP but stored with .kt.txt extension to avoid Android compilation
            kotlin.srcDir(layout.buildDirectory.dir("js-registration-sources"))
            
            dependencies {
                // These are compile-time only - runtime provides them
                implementation(project(":common"))
                implementation(project(":annotations"))
                
                val libs = project.extensions.getByType<VersionCatalogsExtension>()
                    .named("libs")
                
                // source-api provides base classes
                implementation(libs.findLibrary("ireader-core").get())
                implementation(libs.findLibrary("ksoup").get())
                implementation(libs.findLibrary("ktor-core").get())
                implementation(libs.findLibrary("ktor-client-js").get())
                implementation(libs.findLibrary("kotlinx-datetime").get())
                implementation(libs.findLibrary("serialization-json").get())
            }
        }
    }
}

// Task to copy KSP-generated files, excluding Extension.kt (which conflicts between sources)
// Only copies JsInit.kt and other source-specific generated files
val copyKspGeneratedFiles by tasks.registering(Copy::class) {
    group = "js"
    description = "Copy KSP-generated files for JS compilation (excludes Extension.kt)"
    
    val outputDir = layout.buildDirectory.dir("ksp-js-sources")
    
    jsSources.forEach { source ->
        // Use inputs.dir to declare the dependency on the generated directory
        // This tells Gradle about the implicit dependency without requiring task lookup
        val generatedDir = file(source.generatedDir)
        inputs.dir(generatedDir).optional().withPropertyName("ksp-${source.id}")
        
        from(generatedDir) {
            // Include only JS-related generated files
            include("**/js/**/*.kt")  // JsInit.kt and related
            // Exclude Extension.kt which is Android-specific and conflicts
            exclude("**/tachiyomix/extension/**")
            exclude("**/Extension.kt")
        }
    }
    
    into(outputDir)
}

// Task to copy and rename JS registration files from .kt.txt to .kt
// These files are generated by KSP with .kt.txt extension to avoid Android compilation
val copyJsRegistrationFiles by tasks.registering(Copy::class) {
    group = "js"
    description = "Copy JS registration files and rename from .kt.txt to .kt"
    
    val outputDir = layout.buildDirectory.dir("js-registration-sources")
    
    jsSources.forEach { source ->
        val jsOnlyDir = file("${source.generatedDir}/../resources/js-only")
        // Use inputs.dir to declare the dependency on the js-only directory
        inputs.dir(jsOnlyDir).optional().withPropertyName("js-only-${source.id}")
        
        from(jsOnlyDir) {
            include("**/*.kt.txt")
            rename { it.removeSuffix(".txt") }
        }
    }
    
    into(outputDir)
}

// Make sure all source files are copied before compilation
tasks.matching { it.name == "compileKotlinJs" }.configureEach {
    dependsOn("copyKspGeneratedFiles")
    dependsOn("copyJsRegistrationFiles")
}

// Build task - uses webpack to bundle everything into one file
tasks.register("buildAllJsSources") {
    group = "js"
    description = "Build JS source files for iOS (bundled with all dependencies)"
    dependsOn("jsBrowserProductionWebpack")
    
    val webpackOutputDir = layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")
    
    doLast {
        val outputDir = webpackOutputDir.get().asFile
        logger.lifecycle("JS sources built at: ${outputDir.absolutePath}")
        outputDir.listFiles()?.filter { it.extension == "js" }?.forEach {
            logger.lifecycle("  - ${it.name} (${it.length() / 1024}KB)")
        }
    }
}

// Package for distribution - copies the webpack bundled output
tasks.register<Copy>("packageForDistribution") {
    group = "js"
    description = "Package JS sources for CDN distribution (self-contained bundle)"
    dependsOn("jsBrowserProductionWebpack")
    
    val outputDir = layout.buildDirectory.dir("js-dist")
    val webpackDir = layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")
    
    // Copy the webpack bundled output (single file with everything)
    from(webpackDir) {
        include("sources-bundle.js")
        include("sources-bundle.js.map")
    }
    into(outputDir)
}

// Create index.json separately
tasks.register("createSourceIndex") {
    group = "js"
    description = "Create index.json for JS sources"
    dependsOn("packageForDistribution")
    
    val outputDir = layout.buildDirectory.dir("js-dist")
    // Init function name matches KSP-generated: init<Name>
    val sourcesList = jsSources.map { source ->
        // name is the @Extension name (e.g., "FreeWebNovelKmp")
        // id is the lowercase identifier (e.g., "freewebnovelkmp")
        Triple(source.id, source.lang, "init${source.name}")
    }
    
    outputs.file(outputDir.map { it.file("index.json") })
    
    doLast {
        val indexFile = outputDir.get().file("index.json").asFile
        val sources = sourcesList.joinToString(",\n") { (name, lang, initFunc) ->
            """    {
      "id": "$name",
      "name": "$name",
      "lang": "$lang",
      "file": "sources-bundle.js",
      "initFunction": "$initFunc"
    }"""
        }
        indexFile.writeText("""{
  "version": 1,
  "note": "These sources require runtime.js from the main IReader app",
  "sources": [
$sources
  ]
}""")
        logger.lifecycle("Created index at: ${indexFile.absolutePath}")
    }
}

// ============================================================================
// Task Dependencies & Verification
// ============================================================================

// Store paths as simple strings for configuration cache compatibility
val generatedDirPaths = jsSources.map { it.name to it.generatedDir }
val projectPaths = jsSources.map { it.name to it.projectPath }

/**
 * Task to verify KSP-generated files exist before JS compilation.
 * 
 * The JS build requires KSP-generated files from Android builds.
 * These must be generated separately before running JS compilation.
 * 
 * Build order:
 * 1. ./gradlew :extensions:individual:en:freewebnovelkmp:kspEnReleaseKotlin
 * 2. ./gradlew :js-sources:jsBrowserProductionWebpack
 */
abstract class VerifyKspFilesTask : DefaultTask() {
    @get:InputFiles
    @get:Optional
    abstract val generatedDirFiles: ConfigurableFileCollection
    
    @get:Input
    abstract val sourceNames: ListProperty<String>
    
    @get:Input
    abstract val projectPathsInput: ListProperty<String>
    
    @TaskAction
    fun verify() {
        val dirs = generatedDirFiles.files.toList()
        val names = sourceNames.get()
        val paths = projectPathsInput.get()
        
        val missingDirs = mutableListOf<String>()
        dirs.forEachIndexed { index, dir ->
            if (!dir.exists() || dir.listFiles()?.isEmpty() != false) {
                missingDirs.add("${names[index]}: ${dir.absolutePath}")
            }
        }
        
        if (missingDirs.isNotEmpty()) {
            val commands = paths.joinToString("\n") { projectPath ->
                "  ./gradlew ${projectPath}:kspEnReleaseKotlin"
            }
            throw GradleException("""
                |
                |KSP-generated files not found for JS sources:
                |${missingDirs.joinToString("\n") { "  - $it" }}
                |
                |Run the Android KSP tasks first:
                |$commands
                |
                |Then run: ./gradlew :js-sources:jsBrowserProductionWebpack
            """.trimMargin())
        }
        
        logger.lifecycle("✓ All KSP-generated directories exist")
    }
}

tasks.register<VerifyKspFilesTask>("verifyKspGeneratedFiles") {
    group = "js"
    description = "Verify that KSP-generated files exist"
    generatedDirFiles.from(jsSources.map { file(it.generatedDir) })
    sourceNames.set(jsSources.map { it.name })
    projectPathsInput.set(jsSources.map { it.projectPath })
}

// Make JS compilation depend on verification
tasks.matching { it.name == "compileKotlinJs" }.configureEach {
    dependsOn("verifyKspGeneratedFiles")
}

// ============================================================================
// Convenience Tasks
// ============================================================================

/**
 * Print the commands needed to generate KSP files for all JS-enabled sources.
 * Run this if verifyKspGeneratedFiles fails.
 */
tasks.register("printKspCommands") {
    group = "js"
    description = "Print commands to generate KSP files for all JS-enabled sources"
    
    doLast {
        if (jsSources.isEmpty()) {
            logger.lifecycle("No JS-enabled sources found.")
            return@doLast
        }
        
        logger.lifecycle("\nRun these commands to generate KSP files for JS sources:\n")
        jsSources.forEach { source ->
            logger.lifecycle("./gradlew ${source.projectPath}:ksp${source.lang.replaceFirstChar { it.uppercase() }}ReleaseKotlin")
        }
        logger.lifecycle("\nOr run all at once:")
        val allTasks = jsSources.joinToString(" ") { "${it.projectPath}:ksp${it.lang.replaceFirstChar { c -> c.uppercase() }}ReleaseKotlin" }
        logger.lifecycle("./gradlew $allTasks")
    }
}
