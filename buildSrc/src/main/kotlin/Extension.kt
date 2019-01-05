import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import java.security.MessageDigest

data class Extension(
  val name: String,
  val versionCode: Int,
  val libVersion: String,
  val lang: String,
  val description: String = "",
  val nsfw: Boolean = false,
  val deepLinks: List<DeepLink> = emptyList(),
  val sourceDir: String = "main",
  val id: Long = generateSourceId(name, lang),
  val flavor: String = getFlavorName(sourceDir, lang)
)

data class DeepLink(
  val host: String,
  val scheme: String = "https",
  val pathPattern: String = "",
  val path: String = ""
)

fun Project.register(extensions: List<Extension>) {
  extra["extensionList"] = extensions
  apply {
    from("$rootDir/buildSrc/extensionbuild.gradle")
    from("$rootDir/buildSrc/aptmanifest.gradle")
  }
}

fun Project.register(vararg extensions: Extension) {
  register(extensions.toList())
}

private fun getFlavorName(sourceDir: String, lang: String): String {
  return if (sourceDir == "main") lang else "$sourceDir-$lang"
}

private fun generateSourceId(name: String, lang: String, versionId: Int = 1): Long {
  val key = "${name.toLowerCase()}/$lang/$versionId"
  val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
  return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
    .reduce(Long::or) and Long.MAX_VALUE
}
