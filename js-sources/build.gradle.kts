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
 *   ./gradlew :extensions:individual:en:freewebnovelkmp:kspEnReleaseKotlin
 *   ./gradlew :js-sources:jsBrowserProductionWebpack
 * 
 * Or use the convenience task:
 *   ./gradlew :js-sources:buildJsWithDeps
 */

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// Sources to compile (those with enableJs = true)
// name should match the @Extension name for proper init function naming
val jsSources = listOf(
    JsSourceConfig(
        name = "FreeWebNovelKmp",  // Must match @Extension name
        id = "freewebnovelkmp",    // Lowercase ID for file naming
        lang = "en",
        projectPath = ":extensions:individual:en:freewebnovelkmp",
        sourceDir = "../sources/en/freewebnovelkmp/main/src",
        generatedDir = "../sources/en/freewebnovelkmp/build/generated/ksp/enRelease/kotlin"
    )
)

data class JsSourceConfig(
    val name: String,      // Display name (matches @Extension)
    val id: String,        // Lowercase ID for file naming
    val lang: String,
    val projectPath: String,  // Gradle project path for dependency
    val sourceDir: String,
    val generatedDir: String
)

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
            // Include source files and KSP-generated files
            jsSources.forEach { source ->
                // Source implementation files
                kotlin.srcDir(source.sourceDir)
                // KSP-generated JsInit.kt (platform-agnostic)
                kotlin.srcDir(source.generatedDir)
                // KSP-generated JS registration files (JS-specific)
                // These are in the same generated dir with @JsExport functions
            }
            
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
