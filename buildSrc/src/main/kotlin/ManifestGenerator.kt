
import groovy.util.Node
import groovy.util.NodeList
import groovy.util.XmlNodePrinter
import groovy.util.XmlParser
import org.gradle.api.GradleException
import java.io.File
import java.io.PrintWriter

@Suppress("unused")
object ManifestGenerator {

  @JvmStatic
  fun process(index: String, manifest: String, extension: Extension) {
    val indexFile = File(index)
    val manifestFile = File(manifest)

    if (!indexFile.exists() || !manifestFile.exists()) {
      throw GradleException("Can't find index or manifest file for ${extension.name}. Is this " +
        "module annotated with @Extension?")
    }

    val extClass = indexFile.readLines().first()
    val parser = XmlParser().parse(manifestFile)

    // Get package name and append the language
    val packageEnding = extension.flavor.replace("-", ".").toLowerCase()
    val applicationId = extClass.substringBeforeLast(".") + ".$packageEnding"
    parser.attributes()["package"] = applicationId

    val app = (parser["application"] as NodeList).first() as Node

    // Add source class metadata
    Node(app, "meta-data", mapOf(
      "android:name" to "source.class",
      "android:value" to extClass
    ))

    // Add deeplinks if needed
    addDeepLinks(app, extension.deepLinks)

    XmlNodePrinter(manifestFile.printWriter()).print(parser)
  }

  private fun addDeepLinks(app: Node, deeplinks: List<DeepLink>) {
    if (deeplinks.isEmpty()) return

    val activity = Node(app, "activity", mapOf(
      "android:name" to "tachiyomix.deeplink.SourceDeepLinkActivity",
      "android:theme" to "@android:style/Theme.NoDisplay"
    ))

    val filter = Node(activity, "intent-filter")
    Node(filter, "action", mapOf("android:name" to "android.intent.action.VIEW"))
    Node(filter, "category", mapOf("android:name" to "android.intent.category.DEFAULT"))
    Node(filter, "category", mapOf("android:name" to "android.intent.category.BROWSABLE"))

    deeplinks.forEach { link ->
      val data = mutableMapOf<String, String>()
      if (link.scheme.isNotEmpty()) {
        data["android:scheme"] = link.scheme
      }
      if (link.host.isNotEmpty()) {
        data["android:host"] = link.host
      }
      if (link.pathPattern.isNotEmpty()) {
        data["android:pathPattern"] = link.pathPattern
      }
      if (link.path.isNotEmpty()) {
        data["android:path"] = link.path
      }

      Node(filter, "data", data)
    }
  }

}
