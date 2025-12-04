import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.services.DslServices
import com.android.sdklib.BuildToolInfo
import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

@OptIn(ExperimentalSerializationApi::class)
open class RepoTask : DefaultTask() {

    private val aapt2 by lazy { getAapt2Path() }

    private val prettyJson by lazy {
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }

    init {
        val repoTask = this
        project.gradle.projectsEvaluated {
            project.subprojects.forEach {
                it.tasks.findByName("assembleDebug")?.let { repoTask.mustRunAfter(it) }
                it.tasks.findByName("assembleRelease")?.let { repoTask.mustRunAfter(it) }
            }
        }
    }

    @TaskAction
    fun generate() {
        val repoDir = File(project.buildDir, "repo")
        val apkDir = File(repoDir, "apk")
        val iconDir = File(repoDir, "icon")
        val jsDir = File(repoDir, "js")

        repoDir.deleteRecursively()
        repoDir.mkdirs()

        extractApks(apkDir)
        generateJars(apkDir, repoDir)
        
        // Generate JS bundles for iOS
        val jsSources = generateJsBundles(jsDir)
        
        val rawBadgings = parseBadgings(apkDir) ?: return
        val badgings = ensureValidState(rawBadgings)
        extractIcons(apkDir, iconDir, badgings)

        // Generate repo index with JS info
        generateRepo(repoDir, badgings, jsSources)
        
        // Create .gitignore
        createGitignore(repoDir)
    }
    
    private fun createGitignore(repoDir: File) {
        File(repoDir, ".gitignore").writeText("""
            # Ignore nothing - all files should be committed
            !*
        """.trimIndent())
    }
    
    /**
     * Generate JS bundles for iOS and return list of JS source info.
     * 
     * All JS-enabled sources are compiled into a single bundle (sources-bundle.js).
     * Each source has its own init function (e.g., initFreeWebNovelKmp).
     * The iOS app loads the bundle once and calls the appropriate init function.
     */
    private fun generateJsBundles(jsDir: File): List<JsSourceInfo> {
        jsDir.mkdirs()
        val jsSources = mutableListOf<JsSourceInfo>()
        
        // Copy webpack bundled JS from js-sources module (self-contained, includes all deps)
        val webpackDir = File(project.rootDir, "js-sources/build/kotlin-webpack/js/productionExecutable")
        val jsSourcesDistDir = File(project.rootDir, "js-sources/build/js-dist")
        
        if (webpackDir.exists()) {
            print("Copying JS sources from js-sources module (webpack bundle)...\n")
            
            // Parse js-sources index.json to get source info
            val indexFile = File(jsSourcesDistDir, "index.json")
            if (indexFile.exists()) {
                try {
                    val indexJson = Json.decodeFromString<JsIndex>(indexFile.readText())
                    
                    // Copy the single webpack bundle (contains ALL sources)
                    val bundleFile = File(webpackDir, "sources-bundle.js")
                    if (bundleFile.exists()) {
                        val destFile = File(jsDir, "sources-bundle.js")
                        bundleFile.copyTo(destFile, overwrite = true)
                        print("  - ${destFile.name} (${destFile.length() / 1024}KB) - contains ${indexJson.sources.size} source(s)\n")
                        
                        // Copy source map
                        val mapFile = File(webpackDir, "sources-bundle.js.map")
                        if (mapFile.exists()) {
                            mapFile.copyTo(File(jsDir, "sources-bundle.js.map"), overwrite = true)
                        }
                        
                        // Add each source to the index (all use the same bundle file)
                        indexJson.sources.forEach { source ->
                            jsSources.add(JsSourceInfo(
                                id = source.id,
                                name = source.name,
                                lang = source.lang,
                                file = "sources-bundle.js",  // All sources share the same bundle
                                initFunction = source.initFunction
                            ))
                            print("    - ${source.name} (${source.lang}) -> ${source.initFunction}()\n")
                        }
                    }
                } catch (e: Exception) {
                    print("Warning: Could not parse js-sources index.json: ${e.message}\n")
                }
            } else {
                // Fallback: just copy the bundle file
                val bundleFile = File(webpackDir, "sources-bundle.js")
                if (bundleFile.exists()) {
                    val destFile = File(jsDir, "sources.js")
                    bundleFile.copyTo(destFile, overwrite = true)
                    print("  - ${destFile.name} (${destFile.length() / 1024}KB)\n")
                }
            }
        } else {
            print("No JS bundles found. Run ':js-sources:jsBrowserProductionWebpack' first to generate JS output.\n")
        }
        
        return jsSources
    }

    private fun parseBadgings(apkDir: File): List<Badging>? {
        print("Parsing Badging for ${apkDir.name}...\n")
        return apkDir.listFiles()
            ?.filter {
                it.extension == "apk"
            }
            ?.map { apk -> parseBadging(apk) }
    }

    private fun parseBadging(apkFile: File): Badging {

        val lines = ByteArrayOutputStream().use { outStream ->
            project.exec {
                commandLine(
                    aapt2,
                    "dump",
                    "badging",
                    "--include-meta-data",
                    apkFile.absolutePath
                )
                standardOutput = outStream
            }
            outStream.toString().lines()
        }
        val appResources = ByteArrayOutputStream().use { outStream ->
            project.exec {
                commandLine(
                    aapt2,
                    "dump",
                    "resources",
                    apkFile.absolutePath
                )
                standardOutput = outStream
            }
            outStream.toString().lines()
        }

        val (pkgName, vcode, vname) = PACKAGE.find(lines.first())!!.destructured

        val resourceIcon = appResources
            .firstOrNull { it.endsWith("type=PNG") && it.contains("xxxhdpi") }
            ?.let { it ->
                RESOURCE_ICON.find(it)?.groupValues.let {
                    it?.getOrNull(1)
                }
            }

        val iconPath = lines.lastOrNull { it.startsWith("application-icon-") }
            ?.let { it -> APPLICATION_ICON.find(it)?.groupValues.let { it?.get(1) } }

        val metadata = lines
            .filter { it.startsWith("meta-data") }
            .mapNotNull { line ->
                METADATA.find(line)?.groupValues?.let { value ->
                    Metadata(
                        value[1],
                        value[2]
                    )
                }
            }

        val id = metadata.first { it.name == "source.id" }.value.drop(1).toLong()
        val sourceName = metadata.first { it.name == "source.name" }.value
        val lang = metadata.first { it.name == "source.lang" }.value
        val description = metadata.find { it.name == "source.description" }?.value.orEmpty()
        val nsfw = metadata.first { it.name == "source.nsfw" }.value == "1"
        val sourceDir = metadata.firstOrNull { it.name == "source.dir" }?.value
        val assetsDir = metadata.firstOrNull { it.name == "source.assets" }?.value
        return Badging(
            pkgName,
            apkFile.name,
            sourceName,
            id,
            lang,
            vcode.toInt(),
            vname,
            description,
            nsfw,
            iconResourcePath = resourceIcon ?: iconPath,
            sourceDir = sourceDir,
            assetsDir = assetsDir
        )
    }

    private fun ensureValidState(badgings: List<Badging>): List<Badging> {
        val samePkgs = badgings.groupBy { it.pkg }
            .filter { it.value.size > 1 }

        if (samePkgs.isNotEmpty()) {
            print("WARNING: Duplicate package names found. Keeping latest version only:\n")
            samePkgs.forEach { (pkg, dupes) ->
                print("  - $pkg: ${dupes.map { it.apk }.joinToString(", ")}\n")
            }
        }

        val sameIds = badgings.groupBy { it.id }
            .filter { it.value.size > 1 }

        if (sameIds.isNotEmpty()) {
            print("WARNING: Duplicate source IDs found. Keeping latest version only:\n")
            sameIds.forEach { (id, dupes) ->
                print("  - $id: ${dupes.map { it.apk }.joinToString(", ")}\n")
            }
        }
        
        // Return deduplicated list - keep the one with highest version code
        return badgings.groupBy { it.pkg }
            .map { (_, versions) -> versions.maxByOrNull { it.code } ?: versions.first() }
    }

    private fun extractApks(destDir: File) {
        project.copy {
            from(project.subprojects.map { it.buildDir })
            include("**/*.apk")
            into(destDir)
            eachFile { path = name }
            includeEmptyDirs = false
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        if (destDir.listFiles().orEmpty().isEmpty()) {
            throw GradleException(
                "The repo directory doesn't have any apk. Rerun this task after " +
                        "executing the :assembleDebug or :assembleRelease tasks"
            )
        }
    }

    private fun extractIcons(apkDir: File, destDir: File, badgings: List<RepoTask.Badging>) {
        destDir.mkdirs()
        badgings.forEach { badging ->
            print("Generating Icon for ${badging.name}...\n")
            val apkFile = File(apkDir, badging.apk)
            if (badging.iconResourcePath != null) {
                print("Getting Assets Icon for ${badging.name}...\n")
                ZipFile(apkFile).use { zip ->
                    val icon = zip.getEntry(badging.iconResourcePath)
                    val dest = File(destDir, "${apkFile.nameWithoutExtension}.png")
                    zip.getInputStream(icon).use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                }
            } else {
                print("Getting Package Icon for ${badging.name}...\n")
                val packageName = badging.pkg.substringAfter(".").substringBefore(".")
                if (badging.assetsDir.isNullOrBlank()) {
                    project.copy {
                        from("${project.rootDir}/sources/${badging.lang}/${packageName}/${badging.sourceDir}/")
                        include("**/assets/*.png")
                        into(destDir)
                        eachFile {
                            path = "${apkFile.nameWithoutExtension}.png"
                        }
                        includeEmptyDirs = false
                        duplicatesStrategy = DuplicatesStrategy.INCLUDE

                    }
                } else {
                    print("Getting Default Assets Icon for ${badging.name}...\n")
                    project.copy {
                        from("${project.rootDir}/sources/${badging.assetsDir}")
                        include("**/*.png")
                        into(destDir)
                        eachFile {
                            path = "${apkFile.nameWithoutExtension}.png"
                        }
                        includeEmptyDirs = false
                        duplicatesStrategy = DuplicatesStrategy.INCLUDE

                    }
                }

                val iconFile =
                    File("${project.buildDir}/repo/icon/${apkFile.nameWithoutExtension}.png")
                if (!iconFile.exists()) {
                    print(
                        "WARNING: There is no Icon for $packageName, ${apkFile.nameWithoutExtension}" +
                                " Make sure that app has same name in build.gradle.kts as subproject name\n"
                    )
                }

            }
        }
    }

    private fun generateRepo(repoDir: File, badgings: List<Badging>, jsSources: List<JsSourceInfo>) {
        val sortedBadgings = badgings.sortedBy { it.pkg }
        
        // Create full repo index with both APK and JS info
        val repoIndex = RepoIndex(
            extensions = sortedBadgings,
            js = if (jsSources.isNotEmpty()) JsSection(
                note = "JS sources require runtime.js from the main IReader app",
                sources = jsSources
            ) else null
        )
        
        File(repoDir, "index.min.json").writer().use {
            it.write(Json.encodeToString(repoIndex))
        }

        File(repoDir, "index.json").writer().use {
            it.write(prettyJson.encodeToString(repoIndex))
        }
        
        print("Generated repo index with ${sortedBadgings.size} extensions and ${jsSources.size} JS sources\n")
    }

    fun generateJars(apkDir: File, repoDir: File) {
        val jarDir = File(repoDir, "jar/")
        jarDir.mkdirs()
        apkDir.listFiles()
            ?.forEach { apk ->
                print("Generating Jar for ${apk.name}...\n")
                val jarFile = File(jarDir, apk.name.replace(".apk", ".jar"))
                dex2jar(apk, jarFile, apk.name)
            }
    }


    private fun getAapt2Path(): String {
        val androidProject = project.subprojects.first { it.hasProperty("android") }
        val androidExtension = androidProject.properties["android"] as BaseExtension
        val dslServices = BaseExtension::class.java.getDeclaredField("dslServices").apply {
            isAccessible = true
        }.get(androidExtension) as DslServices

        @Suppress("deprecation")
        val buildToolInfo =
            dslServices.versionedSdkLoaderService.versionedSdkLoader.get().buildToolInfoProvider.get()
        return buildToolInfo.getPath(BuildToolInfo.PathId.AAPT2)
    }

    companion object {
        val PACKAGE =
            Regex("^package: name='([^']+)' versionCode='([0-9]*)' versionName='([^']*)'.*$")
        val METADATA = Regex("^meta-data: name='([^']*)' value='([^']*)")
        val APPLICATION_ICON = Regex("^application-icon-\\d+:'([^']*)'$")
        val RESOURCE_ICON = Regex("\\(xxxhdpi\\) \\(file\\) (res/[A-Z]*.png) type=PNG")

        @Suppress("NewApi")
        fun dex2jar(dexFile: File, jarFile: File, fileNameWithoutType: String) {
            // adopted from com.googlecode.dex2jar.tools.Dex2jarCmd.doCommandLine
            // source at: https://github.com/DexPatcher/dex2jar/tree/v2.1-20190905-lanchon/dex-tools/src/main/java/com/googlecode/dex2jar/tools/Dex2jarCmd.java
            try {
                val jarFilePath = jarFile.toPath()
                val reader = MultiDexFileReader.open(dexFile.inputStream())
                val handler = BaksmaliBaseDexExceptionHandler()
                Dex2jar
                    .from(reader)
                    .withExceptionHandler(handler)
                    .reUseReg(false)
                    .topoLogicalSort()
                    .skipDebug(true)
                    .optimizeSynchronized(false)
                    .printIR(false)
                    .noCode(false)
                    .skipExceptions(false)
                    .to(jarFilePath)
            } catch (e: Exception) {
                print(e)
            }

        }
    }

    private data class Metadata(
        val name: String,
        val value: String,
    )

    @Serializable
    private data class Badging(
        val pkg: String,
        val apk: String,
        val name: String,
        val id: Long,
        val lang: String,
        val code: Int,
        val version: String,
        val description: String,
        val nsfw: Boolean,
        @Transient
        val iconResourcePath: String? = null,
        val sourceDir: String? = null,
        val assetsDir: String? = null,
    )
    
    // JS source info for iOS
    @Serializable
    private data class JsSourceInfo(
        val id: String,
        val name: String,
        val lang: String,
        val file: String,
        val initFunction: String
    )
    
    @Serializable
    private data class JsSection(
        val note: String,
        val sources: List<JsSourceInfo>
    )
    
    @Serializable
    private data class RepoIndex(
        val extensions: List<Badging>,
        val js: JsSection? = null
    )
    
    // For parsing js-sources index.json
    @Serializable
    private data class JsIndex(
        val version: Int = 1,
        val note: String? = null,
        val sources: List<JsIndexSource> = emptyList()
    )
    
    @Serializable
    private data class JsIndexSource(
        val id: String,
        val name: String,
        val lang: String,
        val file: String,
        val initFunction: String
    )
}
