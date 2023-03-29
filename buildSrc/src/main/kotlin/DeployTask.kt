import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File


open class DeployTask : DefaultTask() {

init {
    val deployTask = this
    val buildTask = project.tasks.findByName("assembleDebug")
    deployTask.dependsOn(buildTask)

}
    @TaskAction
    fun deploy() {
        val outputs = File(project.buildDir,"/outputs/apk/")
        var language = ""
        val apkFile = outputs.listFiles()
            ?.firstOrNull {
                language = it.name
                it.nameWithoutExtension.length == 2
            }
            ?.let { File(it, "/debug/") }
            ?.listFiles()
            .orEmpty()
            .first() { it.extension == "apk" }
        val ireaderDir = File(getCacheDir(),"/Extensions/")
        if (!ireaderDir.exists()) {
            throw java.lang.Exception("IReader Desktop is not installed.")
        }
        if (language.isBlank()) {
            throw java.lang.Exception("Language is not found.")
        }
        val apkName = apkFile.nameWithoutExtension.substringAfter(language.plus("-")).substringBefore("-v")
        val nameDir = "ireader.$apkName.$language"
        val destinationDir = File(ireaderDir,nameDir)
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val destApk = File(destinationDir,nameDir.plus(".apk"))
        val destJar = File(destinationDir,nameDir.plus(".jar"))
        if (!destApk.exists()) {
            destApk.createNewFile()
        }
        if (!destJar.exists()) {
            destApk.createNewFile()
        }
        apkFile.copyRecursively(destApk,overwrite = true)
        RepoTask.dex2jar(destApk,destJar,nameDir)
    }
}
enum class OperatingSystem {
    Android, IOS, Windows, Linux, MacOS, Unknown
}

private val currentOperatingSystem: OperatingSystem
    get() {
        val operSys = java.lang.System.getProperty("os.name").toLowerCase()
        return if (operSys.contains("win")) {
            OperatingSystem.Windows
        } else if (operSys.contains("nix") || operSys.contains("nux") ||
            operSys.contains("aix")
        ) {
            OperatingSystem.Linux
        } else if (operSys.contains("mac")) {
            OperatingSystem.MacOS
        } else {
            OperatingSystem.Unknown
        }
    }

private fun getCacheDir(): File  {
    val ApplicationName = "IReader"
    return when (currentOperatingSystem) {
        OperatingSystem.Windows -> File(System.getenv("AppData"), "$ApplicationName/cache/")
        OperatingSystem.Linux -> File(System.getProperty("user.home"), ".cache/$ApplicationName/")
        OperatingSystem.MacOS -> File(System.getProperty("user.home"), "Library/Caches/$ApplicationName/")
        else -> throw IllegalStateException("Unsupported operating system")
    }
}
