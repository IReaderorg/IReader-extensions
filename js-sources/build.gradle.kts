/**
 * JS Sources Compilation Module
 * 
 * Compiles extension sources to a SELF-CONTAINED JavaScript bundle.
 * 
 * The output bundle includes ALL dependencies:
 * - Kotlin stdlib (~550KB)
 * - Ktor HTTP client with JS engine (~450KB)
 * - Ksoup HTML parser (~520KB)
 * - kotlinx-serialization (~200KB)
 * - fleeksoft-io charset support (~520KB)
 * - All source implementations (~100KB)
 * 
 * This allows the bundle to be used standalone by any JS application
 * without requiring IReader's runtime.js or any external dependencies.
 * 
 * Output: sources-bundle.js (~1.6MB self-contained bundle)
 * 
 * NOTE: This module is SKIPPED during Android CI builds (when CI_CHUNK_NUM is set).
 *       JS builds run separately via build_js.yml workflow.
 */

import java.security.MessageDigest

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// Check if running in CI chunked mode (Android builds)
val isAndroidCiBuild = System.getenv("CI_CHUNK_NUM") != null

if (isAndroidCiBuild) {
    logger.lifecycle("js-sources: Android CI mode - all tasks will be disabled")
}

data class JsSourceConfig(
    val name: String,
    val id: String,
    val lang: String,
    val projectPath: String,
    val sourceDir: String,
    val generatedDir: String,
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val description: String = "",
    val nsfw: Boolean = false,
    val sourceId: Long = 0,
    val pkg: String = "",
    val assetsDir: String = ""
)

fun discoverJsSources(): List<JsSourceConfig> {
    if (isAndroidCiBuild) return emptyList()
    
    val sources = mutableListOf<JsSourceConfig>()
    val sourcesDir = rootDir.resolve("sources")
    
    sourcesDir.listFiles()?.filter { it.isDirectory && it.name != "multisrc" }?.forEach { langDir ->
        langDir.listFiles()?.filter { it.isDirectory }?.forEach { sourceDir ->
            val buildFile = sourceDir.resolve("build.gradle.kts")
            if (buildFile.exists()) {
                parseExtensionConfig(buildFile, langDir.name, sourceDir.name, "individual")?.let {
                    sources.add(it)
                }
            }
        }
    }
    
    val multisrcDir = sourcesDir.resolve("multisrc")
    if (multisrcDir.exists()) {
        multisrcDir.listFiles()?.filter { it.isDirectory }?.forEach { themeDir ->
            val buildFile = themeDir.resolve("build.gradle.kts")
            if (buildFile.exists()) {
                sources.addAll(parseMultisrcExtensionConfigs(buildFile, themeDir.name))
            }
        }
    }
    
    return sources
}

fun parseExtensionConfig(buildFile: File, lang: String, sourceName: String, type: String): JsSourceConfig? {
    val content = buildFile.readText()
    
    if (!content.contains("enableJs") || (!content.contains("enableJs = true") && !content.contains("enableJs=true"))) {
        return null
    }
    
    val extensionName = """Extension\s*\([^)]*name\s*=\s*"([^"]+)"""".toRegex().find(content)?.groupValues?.get(1) ?: sourceName
    val sourceSubDir = """sourceDir\s*=\s*"([^"]+)"""".toRegex().find(content)?.groupValues?.get(1) ?: "main"
    val versionCode = """versionCode\s*=\s*(\d+)""".toRegex().find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    val libVersion = """libVersion\s*=\s*"([^"]+)"""".toRegex().find(content)?.groupValues?.get(1) ?: "1"
    val description = """description\s*=\s*"([^"]*)"""".toRegex().find(content)?.groupValues?.get(1) ?: ""
    val nsfw = content.contains("nsfw = true") || content.contains("nsfw=true")
    val assetsDir = """assetsDir\s*=\s*"([^"]+)"""".toRegex().find(content)?.groupValues?.get(1) ?: ""
    
    val projectPath = ":extensions:$type:$lang:$sourceName"
    val baseDir = if (type == "individual") "sources/$lang/$sourceName" else "sources/multisrc/$sourceName"
    
    return JsSourceConfig(
        name = extensionName,
        id = sourceName.lowercase(),
        lang = lang,
        projectPath = projectPath,
        sourceDir = "../$baseDir/$sourceSubDir/src",
        generatedDir = "../$baseDir/build/generated/ksp/${lang}Release/kotlin",
        versionCode = versionCode,
        versionName = "$libVersion.$versionCode",
        description = description,
        nsfw = nsfw,
        sourceId = generateSourceId(extensionName, lang),
        pkg = "ireader.$sourceName.$lang".lowercase().replace(Regex("[^\\w\\d.]"), "."),
        assetsDir = assetsDir
    )
}

fun generateSourceId(name: String, lang: String, versionId: Int = 1): Long {
    val key = "${name.lowercase()}/$lang/$versionId"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
}

fun parseMultisrcExtensionConfigs(buildFile: File, themeName: String): List<JsSourceConfig> {
    val content = buildFile.readText()
    val configs = mutableListOf<JsSourceConfig>()
    
    """Extension\s*\(([^)]+enableJs\s*=\s*true[^)]*)\)""".toRegex().findAll(content).forEach { match ->
        val block = match.groupValues[1]
        val name = """name\s*=\s*"([^"]+)"""".toRegex().find(block)?.groupValues?.get(1) ?: return@forEach
        val lang = """lang\s*=\s*"([^"]+)"""".toRegex().find(block)?.groupValues?.get(1) ?: "en"
        val sourceDir = """sourceDir\s*=\s*"([^"]+)"""".toRegex().find(block)?.groupValues?.get(1) ?: "main"
        val versionCode = """versionCode\s*=\s*(\d+)""".toRegex().find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val libVersion = """libVersion\s*=\s*"([^"]+)"""".toRegex().find(block)?.groupValues?.get(1) ?: "1"
        val description = """description\s*=\s*"([^"]*)"""".toRegex().find(block)?.groupValues?.get(1) ?: ""
        val nsfw = block.contains("nsfw = true") || block.contains("nsfw=true")
        val assetsDir = """assetsDir\s*=\s*"([^"]+)"""".toRegex().find(block)?.groupValues?.get(1) ?: ""
        val flavor = if (sourceDir == "main") lang else "$sourceDir-$lang"
        
        configs.add(JsSourceConfig(
            name = name,
            id = name.lowercase().replace(" ", ""),
            lang = lang,
            projectPath = ":extensions:multisrc:$themeName",
            sourceDir = "../sources/multisrc/$themeName/$sourceDir/src",
            generatedDir = "../sources/multisrc/$themeName/build/generated/ksp/${flavor}Release/kotlin",
            versionCode = versionCode,
            versionName = "$libVersion.$versionCode",
            description = description,
            nsfw = nsfw,
            sourceId = generateSourceId(name, lang),
            pkg = "ireader.$themeName.$flavor".lowercase().replace(Regex("[^\\w\\d.]"), "."),
            assetsDir = assetsDir
        ))
    }
    return configs
}

val jsSources: List<JsSourceConfig> = discoverJsSources().filter { source ->
    rootProject.findProject(source.projectPath) != null
}.also { sources ->
    if (!isAndroidCiBuild) {
        if (sources.isNotEmpty()) {
            logger.lifecycle("Discovered ${sources.size} JS-enabled source(s):")
            sources.forEach { logger.lifecycle("  - ${it.name} (${it.lang})") }
        } else {
            logger.lifecycle("No JS-enabled sources found.")
        }
    }
}

// Pre-generate JSON strings at configuration time for configuration cache compatibility
val jsIndexMinJson: String = run {
    val iconBaseUrl = "https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/icon"
    "[" + jsSources.joinToString(",") { source ->
        val iconUrl = "$iconBaseUrl/ireader-${source.lang}-${source.id}-v${source.versionName}.png"
        """{"pkg":"${source.pkg}","name":"${source.name}","id":${source.sourceId},"lang":"${source.lang}","code":${source.versionCode},"version":"${source.versionName}","description":"${source.description}","nsfw":${source.nsfw},"file":"sources-bundle.js","initFunction":"init${source.name}","iconUrl":"$iconUrl"}"""
    } + "]"
}

val jsIndexPrettyJson: String = run {
    val iconBaseUrl = "https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/icon"
    "[\n" + jsSources.joinToString(",\n") { source ->
        val iconUrl = "$iconBaseUrl/ireader-${source.lang}-${source.id}-v${source.versionName}.png"
        """  {"pkg":"${source.pkg}","name":"${source.name}","id":${source.sourceId},"lang":"${source.lang}","code":${source.versionCode},"version":"${source.versionName}","description":"${source.description}","nsfw":${source.nsfw},"file":"sources-bundle.js","initFunction":"init${source.name}","iconUrl":"$iconUrl"}"""
    } + "\n]"
}

val jsSourceCount: Int = jsSources.size

kotlin {
    js(IR) {
        browser {
            webpackTask {
                mainOutputFileName = "sources-bundle.js"
                
                // Configure webpack for self-contained bundle
                output.libraryTarget = "umd"  // Universal Module Definition - works in browser, Node.js, AMD
                output.library = "IReaderSources"  // Global name for the library
            }
            
            // Development webpack config
            runTask {
                mainOutputFileName = "sources-bundle.js"
            }
        }
        
        // Executable mode ensures all code is bundled (not just exported)
        binaries.executable()
        
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
            // Main entry point and registry
            kotlin.srcDir("src/jsMain/kotlin")
            
            // Include source files from each JS-enabled extension
            jsSources.forEach { kotlin.srcDir(it.sourceDir) }
            kotlin.srcDir(layout.buildDirectory.dir("ksp-js-sources"))
            kotlin.srcDir(layout.buildDirectory.dir("js-registration-sources"))
            
            dependencies {
                // All these dependencies will be bundled into the output JS file
                implementation(project(":common"))
                implementation(project(":annotations"))
                
                val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
                
                // Core source API - provides base classes like SourceFactory
                implementation(libs.findLibrary("ireader-core").get())
                
                // HTML parsing (with network support for JS)
                implementation(libs.findLibrary("ksoup").get())
                implementation(libs.findLibrary("ksoup-network").get())
                
                // HTTP client for network requests
                implementation(libs.findLibrary("ktor-core").get())
                implementation(libs.findLibrary("ktor-client-js").get())
                implementation(libs.findLibrary("ktor-contentNegotiation").get())
                implementation(libs.findLibrary("ktor-serialization").get())
                
                // Date/time handling
                implementation(libs.findLibrary("kotlinx-datetime").get())
                
                // JSON serialization
                implementation(libs.findLibrary("serialization-json").get())
            }
        }
    }
}

val copyKspGeneratedFiles by tasks.registering(Copy::class) {
    group = "js"
    jsSources.forEach { from(it.generatedDir) { include("**/js/**/*.kt"); exclude("**/Extension.kt") } }
    into(layout.buildDirectory.dir("ksp-js-sources"))
}

val copyJsRegistrationFiles by tasks.registering(Copy::class) {
    group = "js"
    jsSources.forEach {
        from(file("${it.generatedDir}/../resources/js-only")) { include("**/*.kt.txt"); rename { n -> n.removeSuffix(".txt") } }
    }
    into(layout.buildDirectory.dir("js-registration-sources"))
}

tasks.matching { it.name == "compileKotlinJs" }.configureEach {
    dependsOn(copyKspGeneratedFiles, copyJsRegistrationFiles)
}

tasks.register<Copy>("packageForDistribution") {
    group = "js"
    dependsOn("jsBrowserProductionWebpack")
    from(layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) { include("sources-bundle.js*") }
    into(layout.buildDirectory.dir("js-dist"))
}

tasks.register("createSourceIndex") {
    group = "js"
    dependsOn("packageForDistribution")
    val outputDir = layout.buildDirectory.dir("js-dist")
    
    // Use pre-generated strings (captured at configuration time)
    val minJsonContent = jsIndexMinJson
    val prettyJsonContent = jsIndexPrettyJson
    val sourceCount = jsSourceCount
    
    outputs.file(outputDir.map { it.file("js-index.json") })
    outputs.file(outputDir.map { it.file("js-index.min.json") })
    
    doLast {
        val outDir = outputDir.get().asFile.apply { mkdirs() }
        File(outDir, "js-index.min.json").writeText(minJsonContent)
        File(outDir, "js-index.json").writeText(prettyJsonContent)
        logger.lifecycle("Created JS index with $sourceCount sources")
    }
}

// Disable ALL tasks when in Android CI build mode
if (isAndroidCiBuild) {
    tasks.configureEach { enabled = false }
    
    // Also disable root-level Kotlin/JS tasks that get triggered
    rootProject.tasks.matching { 
        it.name == "kotlinNpmInstall" || 
        it.name == "kotlinStoreYarnLock" ||
        it.name == "kotlinNpmCachesSetup" ||
        it.name == "kotlinRestoreYarnLock"
    }.configureEach { 
        enabled = false 
    }
}
