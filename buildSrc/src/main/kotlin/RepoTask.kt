import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.sdklib.BuildToolInfo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

open class RepoTask : DefaultTask() {

  @TaskAction
  fun generate() {
    val repoDir = "${project.buildDir}/repo"
    val apkDir = "$repoDir/apk"
    val repoDirFile = File(repoDir)
    val apkDirFile = File(apkDir)
    val iconDirFile = File(repoDir, "icon")

    repoDirFile.deleteRecursively()
    repoDirFile.mkdirs()

    extractApks(apkDir)

    if (apkDirFile.listFiles().isEmpty()) {
      throw GradleException("The repo directory doesn't have any apk. Rerun this task after " +
        "executing the :assembleDebug or :assembleRelease tasks")
    }

    val badgings = parseBadgings(apkDirFile, getAaptPath())
    ensureValidState(badgings)
    extractIcons(apkDirFile, iconDirFile, badgings)
    generateRepo(repoDir, badgings)
  }

  private fun parseBadgings(apkDir: File, aaptPath: String): List<Badging> {
    return apkDir.listFiles()
      .filter { it.extension == "apk" }
      .map { apk -> parseBadging(apk, aaptPath) }
  }

  private fun parseBadging(apkFile: File, aaptPath: String): Badging {
    val lines = ByteArrayOutputStream().use { outStream ->
      project.exec {
        commandLine(aaptPath,
          "dump",
          "--include-meta-data",
          "badging",
          apkFile.absolutePath)
        standardOutput = outStream
      }
      outStream.toString().lines()
    }

    val (pkgName, vcode, vname) = PATTERN.find(lines.first())!!.destructured

    val metadata = lines.filter { it.startsWith("meta-data") }
      .map { METADATA.find(it)!!.groupValues.let { Metadata(it[1], it[2]) } }

    val sourceName = metadata.find { it.name == "source.name" }?.value ?: ""
    val lang = metadata.find { it.name == "source.lang" }?.value ?: ""
    val description = metadata.find { it.name == "source.description" }?.value ?: ""
    val nsfw = metadata.find { it.name == "source.nsfw" }?.value == "1"
    val id = metadata.find { it.name == "source.id" }?.value?.drop(1)?.toLong() ?: 0

    return Badging(pkgName, apkFile.name, sourceName, id, lang, vcode.toInt(), vname, description,
      nsfw)
  }

  private fun ensureValidState(badgings: List<RepoTask.Badging>) {
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

  private fun extractApks(destDir: String) {
    project.copy {
      from(project.subprojects.map { it.buildDir })
      include("**/*.apk")
      into(destDir)
      eachFile { path = name }
      includeEmptyDirs = false
    }
  }

  private fun extractIcons(apkDir: File, destDir: File, badgings: List<RepoTask.Badging>) {
    destDir.mkdirs()

    badgings.forEach { badging ->
      val apkFile = File(apkDir, badging.apk)
      ZipFile(apkFile).use { zip ->
        val icon = zip.getEntry("res/mipmap-xhdpi-v4/ic_launcher.png")
        val dest = File(destDir, "${apkFile.nameWithoutExtension}.png")
        zip.getInputStream(icon).use { input ->
          dest.outputStream().use { input.copyTo(it) }
        }
      }
    }
  }

  private fun generateRepo(repoDir: String, badgings: List<Badging>) {
    File(repoDir, "index.min.json").writer().use {
      Gson().toJson(badgings, it)
    }

    File(repoDir, "index.json").writer().use {
      GsonBuilder().setPrettyPrinting().create().toJson(badgings, it)
    }
  }

  private fun getAaptPath(): String {
    val androidProject = project.subprojects.first { it.hasProperty("android") }
    val androidExtension = androidProject.properties["android"] as BaseExtension
    val globalScope = BaseExtension::class.java.getDeclaredField("globalScope").apply {
      isAccessible = true
    }.get(androidExtension) as GlobalScope
    return globalScope.targetInfo.buildTools.getPath(BuildToolInfo.PathId.AAPT)
  }

  private companion object {
    val PATTERN = Regex("^package: name='([^']+)' versionCode='([0-9]*)' versionName='([^']*)'.*$")
    val METADATA = Regex("^meta-data: name='([^']*)' value='([^']*)")
  }

  private data class Metadata(val name: String, val value: String)

  private data class Badging(
    val pkg: String,
    val apk: String,
    val name: String,
    val id: Long,
    val lang: String,
    val code: Int,
    val version: String,
    val description: String,
    val nsfw: Boolean
  )
}
