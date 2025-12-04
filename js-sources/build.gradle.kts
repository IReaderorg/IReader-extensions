/**
 * JS Sources Compilation Module
 * 
 * This module compiles extension sources to JavaScript for iOS.
 * It includes:
 * - Source code from sources/
 * - KSP-generated JsInit.kt files
 * 
 * Output: build/dist/js/<source-name>.js
 */

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// List of sources to compile to JS (sources with enableJs = true)
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
        browser {
            webpackTask {
                mainOutputFileName = "sources-bundle.js"
            }
            distribution {
                outputDirectory = file("$buildDir/dist/js")
            }
        }
        binaries.executable()
        
        // Generate TypeScript declarations
        generateTypeScriptDefinitions()
        
        // Use ES2015 to support Long as BigInt
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    target.set("es2015")
                }
            }
        }
    }
    
    sourceSets {
        val jsMain by getting {
            // Include source files
            jsSources.forEach { source ->
                kotlin.srcDir(source.sourceDir)
                kotlin.srcDir(source.generatedDir)
            }
            
            dependencies {
                implementation(project(":common"))
                implementation(project(":annotations"))
                
                val libs = project.extensions.getByType<VersionCatalogsExtension>()
                    .named("libs")
                
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

// Task to build individual source JS files
jsSources.forEach { source ->
    tasks.register("build${source.name.replaceFirstChar { it.uppercase() }}Js") {
        group = "js"
        description = "Build JS bundle for ${source.name}"
        dependsOn("jsBrowserProductionWebpack")
        
        doLast {
            logger.lifecycle("Built JS for ${source.name}")
        }
    }
}

tasks.register("buildAllJsSources") {
    group = "js"
    description = "Build all JS source bundles"
    dependsOn("jsBrowserProductionWebpack")
}
