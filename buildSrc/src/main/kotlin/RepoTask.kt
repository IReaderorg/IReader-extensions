import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.sdklib.BuildToolInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

@OptIn(ExperimentalSerializationApi::class)
open class RepoTask : DefaultTask() {

  private val aapt2 by lazy { getAapt2Path() }

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

    repoDir.deleteRecursively()
    repoDir.mkdirs()

    extractApks(apkDir)

    val badgings = parseBadgings(apkDir)
    ensureValidState(badgings)
    extractIcons(apkDir, iconDir, badgings)
    generateRepo(repoDir, badgings)
  }

  private fun parseBadgings(apkDir: File): List<Badging> {
    return apkDir.listFiles()
      .filter { it.extension == "apk" }
      .map { apk -> parseBadging(apk) }
  }

  private fun parseBadging(apkFile: File): Badging {
    val lines = ByteArrayOutputStream().use { outStream ->
      project.exec {
        commandLine(aapt2,
          "dump",
          "badging",
          "--include-meta-data",
          apkFile.absolutePath)
        standardOutput = outStream
      }
      outStream.toString().lines()
    }

    val (pkgName, vcode, vname) = PACKAGE.find(lines.first())!!.destructured

    val iconPath = lines
      .last { it.startsWith("application-icon-") }
      .let { APPLICATION_ICON.find(it)!!.groupValues.let { it[1] } }

    val metadata = lines
      .filter { it.startsWith("meta-data") }
      .map { METADATA.find(it)!!.groupValues.let { Metadata(it[1], it[2]) } }

    val id = metadata.first { it.name == "source.id" }.value.drop(1).toLong()
    val sourceName = metadata.first { it.name == "source.name" }.value
    val lang = metadata.first { it.name == "source.lang" }.value
    val description = metadata.first { it.name == "source.description" }.value
    val nsfw = metadata.first { it.name == "source.nsfw" }.value == "1"

    return Badging(pkgName, apkFile.name, sourceName, id, lang, vcode.toInt(), vname, description,
      nsfw, iconPath)
  }

  private fun ensureValidState(badgings: List<Badging>) {
    val samePkgs = badgings.groupBy { it.pkg }
      .filter { it.value.size > 1 }
      .map { it.key }

    if (samePkgs.isNotEmpty()) {
      throw GradleException("${samePkgs.joinToString()} have duplicate package names. Check your " +
        "build")
    }

    val sameIds = badgings.groupBy { it.id }
      .filter { it.value.size > 1 }

    if (sameIds.isNotEmpty()) {
      val sameIdPkgs = sameIds.flatMap { it.value.map { it.pkg } }
      throw GradleException("${sameIdPkgs.joinToString()} have duplicate ids. Check your build")
    }
  }

  private fun extractApks(destDir: File) {
    project.copy {
      from(project.subprojects.map { it.buildDir })
      include("**/*.apk")
      into(destDir)
      eachFile { path = name }
      includeEmptyDirs = false
    }

    if (destDir.listFiles().orEmpty().isEmpty()) {
      throw GradleException("The repo directory doesn't have any apk. Rerun this task after " +
        "executing the :assembleDebug or :assembleRelease tasks")
    }
  }

  private fun extractIcons(apkDir: File, destDir: File, badgings: List<RepoTask.Badging>) {
    destDir.mkdirs()

    badgings.forEach { badging ->
      val apkFile = File(apkDir, badging.apk)
      ZipFile(apkFile).use { zip ->
        val icon = zip.getEntry(badging.iconPath)
        val dest = File(destDir, "${apkFile.nameWithoutExtension}.png")
        zip.getInputStream(icon).use { input ->
          dest.outputStream().use { input.copyTo(it) }
        }
      }
    }
  }

  private fun generateRepo(repoDir: File, badgings: List<Badging>) {
    File(repoDir, "index.min.json").writer().use {
      it.write(Json.encodeToString(badgings))
    }

    File(repoDir, "index.json").writer().use {
      it.write(Json {
        prettyPrint = true
        prettyPrintIndent = "  "
      }.encodeToString(badgings))
    }
  }

  private fun getAapt2Path(): String {
    val androidProject = project.subprojects.first { it.hasProperty("android") }
    val androidExtension = androidProject.properties["android"] as BaseExtension
    val globalScope = BaseExtension::class.java.getDeclaredField("globalScope").apply {
      isAccessible = true
    }.get(androidExtension) as GlobalScope
    val buildToolInfo = globalScope.versionedSdkLoader.get().buildToolInfoProvider.get()
    return buildToolInfo.getPath(BuildToolInfo.PathId.AAPT2)
  }

  private companion object {
    val PACKAGE = Regex("^package: name='([^']+)' versionCode='([0-9]*)' versionName='([^']*)'.*$")
    val METADATA = Regex("^meta-data: name='([^']*)' value='([^']*)")
    val APPLICATION_ICON = Regex("^application-icon-\\d+:'([^']*)'$")
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
    val iconPath: String = "",
  )
}
