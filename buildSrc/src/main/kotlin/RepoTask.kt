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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption
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
        // Captured here rather than read from `project` below: Task.project is not
        // allowed at execution time under the configuration cache, and RepoTask
        // touches it in many helpers. Reading it once in the action is still a
        // configuration-cache problem, but a single one instead of 700, and keeps
        // the task usable until the properties are properly captured at
        // configuration time.
        val project = project
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

            // Parse the js-sources index (written by :js-sources:createSourceIndex).
            // The module emits js-index.json / js-index.min.json as a flat array; the
            // older index.json shape is still accepted for backwards compatibility.
            val indexFile = listOf("js-index.json", "js-index.min.json", "index.json")
                .map { File(jsSourcesDistDir, it) }
                .firstOrNull { it.exists() }
            if (indexFile != null) {
                try {
                    val indexJson = parseJsIndex(indexFile.readText())

                    // Copy the single webpack bundle (contains ALL sources)
                    val bundleFile = File(webpackDir, "sources-bundle.js")
                    if (bundleFile.exists()) {
                        val destFile = File(jsDir, "sources-bundle.js")
                        bundleFile.copyTo(destFile, overwrite = true)
                        print("  - ${destFile.name} (${destFile.length() / 1024}KB) - contains ${indexJson.size} source(s)\n")

                        // Copy source map
                        val mapFile = File(webpackDir, "sources-bundle.js.map")
                        if (mapFile.exists()) {
                            mapFile.copyTo(File(jsDir, "sources-bundle.js.map"), overwrite = true)
                        }

                        // Add each source to the index (all use the same bundle file)
                        indexJson.forEach { source ->
                            jsSources.add(source.copy(file = "sources-bundle.js"))
                            print("    - ${source.name} (${source.lang}) -> ${source.initFunction}()\n")
                        }
                    }
                } catch (e: Exception) {
                    print("Warning: Could not parse js-sources index.json: ${e.message}\n")
                }
            } else {
                // Fallback: copy the bundle even when the index is missing, keeping the
                // canonical name so the deployed js/ directory stays consistent.
                print("Warning: js-sources index not found in ${jsSourcesDistDir.absolutePath}\n")
                val bundleFile = File(webpackDir, "sources-bundle.js")
                if (bundleFile.exists()) {
                    val destFile = File(jsDir, "sources-bundle.js")
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
            val packageName = badging.pkg.substringAfter(".").substringBefore(".")
            val destFile = File(destDir, "${apkFile.nameWithoutExtension}.png")

            // Priority 1: Try assets folder from source directory first
            var iconFound = false

            if (!badging.assetsDir.isNullOrBlank()) {
                // Use assetsDir if specified (for multisrc)
                print("Trying assets from ${badging.assetsDir}...\n")
                val assetsPath = File("${project.rootDir}/sources/${badging.assetsDir}")
                val assetIcon = assetsPath.walkTopDown().find { it.extension == "png" }
                if (assetIcon != null && assetIcon.exists()) {
                    assetIcon.copyTo(destFile, overwrite = true)
                    iconFound = true
                    print("Got icon from assetsDir: ${assetIcon.path}\n")
                }
            }

            if (!iconFound) {
                // Try source's assets folder
                val sourceAssetsPath = File("${project.rootDir}/sources/${badging.lang}/${packageName}/${badging.sourceDir}/assets")
                val sourceIcon = sourceAssetsPath.listFiles()?.find { it.extension == "png" }
                if (sourceIcon != null && sourceIcon.exists()) {
                    sourceIcon.copyTo(destFile, overwrite = true)
                    iconFound = true
                    print("Got icon from source assets: ${sourceIcon.path}\n")
                }
            }

            // Priority 2: Try xxxhdpi resource from APK (skip mdpi placeholder)
            if (!iconFound && badging.iconResourcePath != null && badging.iconResourcePath.contains("xxxhdpi")) {
                print("Getting xxxhdpi icon from APK for ${badging.name}...\n")
                try {
                    ZipFile(apkFile).use { zip ->
                        val icon = zip.getEntry(badging.iconResourcePath)
                        if (icon != null) {
                            zip.getInputStream(icon).use { input ->
                                destFile.outputStream().use { input.copyTo(it) }
                            }
                            iconFound = true
                        }
                    }
                } catch (e: Exception) {
                    print("Failed to extract icon from APK: ${e.message}\n")
                }
            }

            if (!iconFound) {
                print(
                    "WARNING: There is no Icon for $packageName, ${apkFile.nameWithoutExtension}" +
                        " Make sure that app has same name in build.gradle.kts as subproject name\n"
                )
            }
        }
    }

    private fun generateRepo(repoDir: File, badgings: List<Badging>, jsSources: List<JsSourceInfo>) {
        val sortedBadgings = badgings.sortedBy { it.pkg }
        // The JS index lives under js/ (next to the bundle), not at the repo root.
        // build_js.yml deploys js-dist/* into repov2/js/, and IReader's
        // CatalogGithubApi rewrites .../index.min.json -> .../js/js-index.min.json.
        val jsDir = File(repoDir, "js").apply { mkdirs() }

        // Generate index.min.json as a flat array for backward compatibility with merge script
        // The merge script expects: [{"pkg":...}, {"pkg":...}]
        File(repoDir, "index.min.json").writer().use {
            it.write(Json.encodeToString(sortedBadgings))
        }

        File(repoDir, "index.json").writer().use {
            it.write(prettyJson.encodeToString(sortedBadgings))
        }

        // Generate separate JS index if there are JS sources.
        // Shape matches what the iOS runtime consumes: a flat array of source entries.
        if (jsSources.isNotEmpty()) {
            val repoJsSources = jsSources.map { source ->
                RepoJsSource(
                    pkg = source.pkg ?: source.id,
                    name = source.name,
                    id = source.sourceId ?: 0L,
                    lang = source.lang,
                    code = source.code,
                    version = source.version,
                    description = source.description,
                    nsfw = source.nsfw,
                    file = source.file,
                    initFunction = source.initFunction,
                    iconUrl = source.iconUrl
                )
            }
            File(jsDir, "js-index.json").writer().use {
                it.write(prettyJson.encodeToString(repoJsSources))
            }
            File(jsDir, "js-index.min.json").writer().use {
                it.write(Json.encodeToString(repoJsSources))
            }
        }

        print("Generated repo index with ${sortedBadgings.size} extensions and ${jsSources.size} JS sources\n")
    }

    fun generateJars(apkDir: File, repoDir: File) {
        val jarDir = File(repoDir, "jar/")
        jarDir.mkdirs()
        apkDir.listFiles()
            ?.forEach { apk ->
                print("Generating Jar for ${apk.name}...\n")
                // Validate APK is a valid zip file before processing
                if (!isValidZipFile(apk)) {
                    print("WARNING: Skipping corrupted APK: ${apk.name}\n")
                    return@forEach
                }
                val jarFile = File(jarDir, apk.name.replace(".apk", ".jar"))

                // Try to find pre-compiled JVM classes first (from Kotlin compilation)
                // These are in build/tmp/kotlin-classes/{flavor}Release/
                val flavorName = extractFlavorFromApkName(apk.name)
                val classesDir = File(project.buildDir, "tmp/kotlin-classes/${flavorName}Release")

                if (classesDir.exists() && classesDir.isDirectory) {
                    print("  Using pre-compiled JVM classes from: ${classesDir.path}\n")
                    createJarFromClasses(classesDir, jarFile)
                } else {
                    // Fallback to dex2jar if pre-compiled classes not found
                    print("  Pre-compiled classes not found, using dex2jar\n")
                    dex2jar(apk, jarFile, apk.name)
                }
            }
    }

    /**
     * Extract flavor name from APK filename.
     * e.g., "ireader-en-freewebnovel-v2.12.apk" -> "enFreewebnovel"
     */
    private fun extractFlavorFromApkName(apkName: String): String {
        // Format: ireader-{lang}-{name}-v{version}.apk
        val parts = apkName.removePrefix("ireader-").removeSuffix(".apk").split("-")
        if (parts.size >= 2) {
            val lang = parts[0]
            val name = parts.subList(1, parts.size - 1).joinToString("") {
                it.replaceFirstChar { c -> c.uppercase() }
            }.replaceFirstChar { it.lowercase() }
            return "$lang${name.replaceFirstChar { it.uppercase() }}"
        }
        return apkName.removeSuffix(".apk")
    }

    /**
     * Create a JAR file from a directory of compiled .class files.
     */
    private fun createJarFromClasses(classesDir: File, jarFile: File) {
        java.util.jar.JarOutputStream(jarFile.outputStream()).use { jos ->
            classesDir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    val entryName = classFile.relativeTo(classesDir).path.replace("\\", "/")
                    jos.putNextEntry(java.util.jar.JarEntry(entryName))
                    classFile.inputStream().use { it.copyTo(jos) }
                    jos.closeEntry()
                }
        }
    }

    private fun isValidZipFile(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                zip.entries().hasMoreElements()
            }
            true
        } catch (e: Exception) {
            false
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
                // Read file bytes instead of using InputStream - MultiDexFileReader needs random access
                // to read the ZIP structure and find DEX files inside APKs
                val fileBytes = java.nio.file.Files.readAllBytes(dexFile.toPath())
                val reader = MultiDexFileReader.open(fileBytes)
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
                    .dontSanitizeNames(true)
                    .to(jarFilePath)

                // Post-process: Fix stackmap frames by stripping them and downgrading to Java 6
                // Java 6 classes don't require stackmap frames, so the JVM uses type inference
                fixStackmapFrames(jarFile)
            } catch (e: Exception) {
                print(e)
            }
        }

        /**
         * Fix stackmap frames in a JAR by stripping StackMapTable attributes and
         * downgrading class version to Java 6 (50). This avoids VerifyError from
         * invalid stackmap frames produced by dex2jar for Kotlin coroutines.
         */
        private fun fixStackmapFrames(jarFile: File) {
            val jarPath = jarFile.toPath()
            FileSystems.newFileSystem(jarPath, null as ClassLoader?)?.use { fs ->
                Files.walk(fs.getPath("/"))
                    .filter { it.toString().endsWith(".class") }
                    .forEach { classPath ->
                        try {
                            val bytes = Files.readAllBytes(classPath)
                            if (bytes.size >= 4) {
                                val fixed = stripFramesAndDowngrade(bytes)
                                Files.write(classPath, fixed,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING)
                            }
                        } catch (e: Exception) {
                            // Skip files that can't be processed
                        }
                    }
            }
        }

        /**
         * Strip StackMapTable frames and downgrade class to Java 6.
         */
        private fun stripFramesAndDowngrade(classBytes: ByteArray): ByteArray {
            val cr = ClassReader(classBytes)
            val cw = ClassWriter(0)

            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String?,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    // Downgrade to Java 6 (V1_6 = 50) which doesn't require StackMapTable
                    super.visit(Opcodes.V1_6, access, name, signature, superName, interfaces)
                }

                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitFrame(
                            type: Int,
                            numLocal: Int,
                            local: Array<out Any>?,
                            numStack: Int,
                            stack: Array<out Any>?
                        ) {
                            // Skip all frame instructions - not needed for Java 6
                        }
                    }
                }
            }, ClassReader.SKIP_FRAMES)

            return cw.toByteArray()
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

    // JS source info for iOS, as written into the repo's js-index.json.
    @Serializable
    private data class JsSourceInfo(
        val id: String,
        val name: String,
        val lang: String,
        val file: String,
        val initFunction: String,
        val pkg: String? = null,
        val code: Int? = null,
        val version: String? = null,
        val description: String? = null,
        val nsfw: Boolean = false,
        val iconUrl: String? = null,
        val sourceId: Long? = null,
    )

    /**
     * Entry shape emitted by :js-sources:createSourceIndex. The source id is numeric
     * there while the string form lives in "pkg", so "id" is read leniently.
     */
    @Serializable
    private data class JsIndexEntry(
        val id: JsonElement? = null,
        val pkg: String? = null,
        val name: String,
        val lang: String,
        val file: String,
        val initFunction: String,
        val code: Int? = null,
        val version: String? = null,
        val description: String? = null,
        val nsfw: Boolean = false,
        val iconUrl: String? = null,
    )

    /**
     * Parse a js-sources index, accepting both the flat-array shape written by
     * createSourceIndex and the older `{version, sources}` envelope.
     */
    private fun parseJsIndex(text: String): List<JsSourceInfo> {
        val entries = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<JsIndexEntry>>(text)
        }.getOrElse {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<JsIndex>(text)
                .sources
                .map { s ->
                    JsIndexEntry(
                        id = JsonPrimitive(s.id),
                        name = s.name,
                        lang = s.lang,
                        file = s.file,
                        initFunction = s.initFunction,
                    )
                }
        }

        return entries.map { entry ->
            val stringId = entry.pkg
                ?: entry.id?.let { id ->
                    // Older indexes stored the slug directly; newer ones store a number.
                    (id as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
                ?: entry.name.lowercase()

            JsSourceInfo(
                id = stringId,
                name = entry.name,
                lang = entry.lang,
                file = entry.file,
                initFunction = entry.initFunction,
                pkg = entry.pkg ?: stringId,
                code = entry.code,
                version = entry.version,
                description = entry.description,
                nsfw = entry.nsfw,
                iconUrl = entry.iconUrl,
                sourceId = (entry.id as? JsonPrimitive)?.content?.toLongOrNull(),
            )
        }
    }

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

    // For parsing the older {version, sources} js-sources index envelope.
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

    // For writing the repo's js-index.json
    @Serializable
    private data class RepoJsSource(
        val pkg: String,
        val name: String,
        val id: Long,
        val lang: String,
        val code: Int? = null,
        val version: String? = null,
        val description: String? = null,
        val nsfw: Boolean = false,
        val file: String,
        val initFunction: String,
        val iconUrl: String? = null
    )
}
