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
 */

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// Sources to compile (those with enableJs = true)
val jsSources = listOf(
    JsSourceConfig(
        name = "freewebnovelkmp",
        lang = "en",
        sourceDir = "../sources/en/freewebnovelkmp/main/src",
        generatedDir = "../sources/en/freewebnovelkmp/build/generated/ksp/enRelease/kotlin"
    )
)

data class JsSourceConfig(
    val name: String,
    val lang: String,
    val sourceDir: String,
    val generatedDir: String
)

kotlin {
    js(IR) {
        // Use nodejs target for simpler module output (no webpack)
        // The main app's runtime.js provides all dependencies
        nodejs()
        
        // Library mode - produces clean ES modules
        binaries.library()
        
        // Generate TypeScript declarations for iOS interop
        generateTypeScriptDefinitions()
        
        // Compiler options for smaller output
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    // Optimize for size
                    freeCompilerArgs.add("-Xir-minimized-member-names")
                }
            }
        }
    }
    
    sourceSets {
        val jsMain by getting {
            // Include source files and KSP-generated init files
            jsSources.forEach { source ->
                kotlin.srcDir(source.sourceDir)
                kotlin.srcDir(source.generatedDir)
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

// Build task
tasks.register("buildAllJsSources") {
    group = "js"
    description = "Build JS source files for iOS"
    dependsOn("jsBrowserProductionLibrary")
    
    doLast {
        val outputDir = file("$buildDir/dist/js/productionLibrary")
        logger.lifecycle("JS sources built at: ${outputDir.absolutePath}")
        outputDir.listFiles()?.filter { it.extension == "js" }?.forEach {
            logger.lifecycle("  - ${it.name} (${it.length() / 1024}KB)")
        }
    }
}

// Package for distribution
tasks.register<Copy>("packageForDistribution") {
    group = "js"
    description = "Package JS sources for CDN distribution (source-only, no runtime deps)"
    dependsOn("compileProductionLibraryKotlinJs")
    
    val outputDir = layout.buildDirectory.dir("js-dist")
    val sourceDir = layout.buildDirectory.dir("compileSync/js/main/productionLibrary/kotlin")
    
    // Source files are in compileSync output (raw Kotlin/JS output)
    from(sourceDir) {
        // ONLY include the extension source file - NOT runtime dependencies
        // Runtime deps (kotlin-stdlib, ktor, ksoup, etc.) come from main app's runtime.js
        include("IReader-extensions-js-sources.js")
        include("IReader-extensions-js-sources.js.map")
        include("*.d.ts")
    }
    into(outputDir)
    
    // Rename to match source name
    rename("IReader-extensions-js-sources.js", "sources-bundle.js")
    rename("IReader-extensions-js-sources.js.map", "sources-bundle.js.map")
}

// Create index.json separately
tasks.register("createSourceIndex") {
    group = "js"
    description = "Create index.json for JS sources"
    dependsOn("packageForDistribution")
    
    val outputDir = layout.buildDirectory.dir("js-dist")
    val sourcesList = jsSources.map { Triple(it.name, it.lang, "init${it.name.replaceFirstChar { c -> c.uppercase() }}") }
    
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
