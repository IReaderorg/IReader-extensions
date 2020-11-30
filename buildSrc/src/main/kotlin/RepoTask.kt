import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.sdklib.BuildToolInfo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

open class RepoTask : DefaultTask() {

  private val aapt by lazy { getAaptPath() }

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
        commandLine(aapt,
          "dump",
          "--include-meta-data",
          "badging",
          apkFile.absolutePath)
        standardOutput = outStream
      }
      outStream.toString().lines()
    }

    val (pkgName, vcode, vname) = PACKAGE.find(lines.first())!!.destructured

    val metadata = lines.filter { it.startsWith("meta-data") }
      .map { METADATA.find(it)!!.groupValues.let { Metadata(it[1], it[2]) } }

    val id = metadata.first { it.name == "source.id" }.value.drop(1).toLong()
    val sourceName = metadata.first { it.name == "source.name" }.value
    val lang = metadata.first { it.name == "source.lang" }.value
    val description = metadata.first { it.name == "source.description" }.value
    val nsfw = metadata.first { it.name == "source.nsfw" }.value == "1"

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
        val icon = zip.getEntry("res/mipmap-xhdpi-v4/ic_launcher.png")
        val dest = File(destDir, "${apkFile.nameWithoutExtension}.png")
        zip.getInputStream(icon).use { input ->
          dest.outputStream().use { input.copyTo(it) }
        }
      }
    }
  }

  private fun generateRepo(repoDir: File, badgings: List<Badging>) {
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
    val sdkComponents = globalScope.sdkComponents.get()
    val buildToolInfo = sdkComponents.buildToolInfoProvider.get()
    return buildToolInfo.getPath(BuildToolInfo.PathId.AAPT)
  }

  private companion object {
    val PACKAGE = Regex("^package: name='([^']+)' versionCode='([0-9]*)' versionName='([^']*)'.*$")
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
