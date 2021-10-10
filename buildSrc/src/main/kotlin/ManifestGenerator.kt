import com.android.builder.model.SourceProvider
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlNodePrinter
import groovy.xml.XmlParser
import org.gradle.api.GradleException
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_RELATIVE_PATHS
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@Suppress("unused")
object ManifestGenerator {

  private val project by lazy {
    KotlinCoreEnvironment.createForProduction(
      Disposer.newDisposable(),
      CompilerConfiguration().apply {
        put(
          CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
          PrintingMessageCollector(System.err, PLAIN_RELATIVE_PATHS, false)
        )
      },
      EnvironmentConfigFiles.JVM_CONFIG_FILES
    ).project
  }

  @JvmStatic
  fun process(manifest: String, extension: Extension, sources: List<SourceProvider>) {
    val manifestFile = File(manifest)

    if (!manifestFile.exists()) {
      throw GradleException("Can't find manifest file for ${extension.name}")
    }

    val extClass = findExtension(extension, sources) + "Gen" // This is a suffix added by kapt
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

  private fun findExtension(extension: Extension, sources: List<SourceProvider>): String {
    val srcDirs = sources.reversed()
      .flatMap { it.javaDirectories }
      .toSet()

    for (srcDir in srcDirs) {
      val foundExtensions = findExtensionClasses(srcDir)
      when (foundExtensions.size) {
        0 -> {}
        1 -> return foundExtensions.first()
        else -> throw GradleException("More than one class annotated with @Extension was " +
          "found for ${extension.name}")
      }
    }

    throw GradleException("Can't find any class annotated with @Extension for ${extension.name}")
  }

  private fun findExtensionClasses(sourceDir: File): List<String> {
    if (!sourceDir.exists()) {
      return emptyList()
    }

    return sourceDir.walkTopDown()
      .toList()
      .filter { it.isFile && it.extension == "kt" }
      .flatMap { file ->
        val kt = createKtFile(file.readText(), file.name)
        kt.children
          .filterIsInstance<KtClass>()
          .filter { cls ->
            cls.annotationEntries.any { it.shortName?.identifier == "Extension" }
          }
          .mapNotNull { it.fqName }
      }
      .map { it.asString() }
  }

  private fun createKtFile(codeString: String, fileName: String): KtFile {
    return PsiManager.getInstance(project)
      .findFile(LightVirtualFile(fileName, KotlinFileType.INSTANCE, codeString)) as KtFile
  }
}
