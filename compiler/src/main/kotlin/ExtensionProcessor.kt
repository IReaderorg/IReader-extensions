package tachiyomix.compiler

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import tachiyomix.annotations.Extension
import java.io.File
import java.nio.file.Paths
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.StandardLocation

@Suppress("unused")
@AutoService(Processor::class)
class ExtensionProcessor : AbstractProcessor() {

  private lateinit var logger: Logger
  private lateinit var types: Types
  private lateinit var elements: Elements
  private lateinit var sourceTypes: SourceTypes

  override fun init(env: ProcessingEnvironment) {
    super.init(env)
    logger = Logger(env.messager)
    types = env.typeUtils
    elements = env.elementUtils
    sourceTypes = SourceTypes(elements)
  }

  override fun getSupportedAnnotationTypes(): Set<String> {
    return setOf(Extension::class.java.canonicalName)
  }

  override fun getSupportedOptions(): Set<String> {
    return setOf(
      "SOURCE_NAME",
      "SOURCE_ID",
      "SOURCE_LANG",
      "MANIFEST_HAS_DEEPLINKS"
    )
  }

  override fun getSupportedSourceVersion(): SourceVersion {
    return SourceVersion.latestSupported()
  }

  override fun process(set: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
    val sourceClass = getClassToGenerate(roundEnv) ?: return true

    if (!checkMatchesPkgName(sourceClass)) {
      return true
    }
    if (!checkImplementsDeepLink(sourceClass)) {
      return true
    }

    try {
      generateClass(sourceClass)
    } catch (e: Exception) {
      logger.e(e.message)
    }

    return true
  }

  /**
   * Returns the source class to generate, or null if more than one was detected and they don't
   * inherit each other.
   */
  private fun getClassToGenerate(roundEnv: RoundEnvironment): Element? {
    val candidates = roundEnv.getElementsAnnotatedWith(Extension::class.java)
    val classToGenerate = when (candidates.size) {
      0 -> null
      1 -> candidates.first()
      else -> {
        val candidate = candidates.find { candidate ->
          val candidateType = candidate.asType()
          candidates.all { it === candidate || types.isSubtype(candidateType, it.asType()) }
        }
        if (candidate == null) {
          logger.e("Found [${candidates.joinToString()}] annotated with @Extension but they don't" +
            " inherit each other and only one class can be generated")
        }
        candidate
      }
    } ?: return null

    if (!types.isAssignable(classToGenerate.asType(), sourceTypes.source)) {
      logger.e("$classToGenerate doesn't implement ${sourceTypes.source}")
      return null
    }
    return classToGenerate
  }

  /**
   * Returns true if the package name of the given source class is valid.
   */
  private fun checkMatchesPkgName(sourceClass: Element): Boolean {
    val buildDir = getBuildDir()
    if ("/samples/" in buildDir) return true // Disable pkgname checks for the samples

    val pkgName = elements.getPackageOf(sourceClass).toString()

    val sourceDir = buildDir.substringBeforeLast("/build/").substringAfterLast("/")
    val expectedPkgName = "tachiyomix.$sourceDir"

    if (!Regex("^$expectedPkgName\\.?.*?").matches(pkgName)) {
      logger.e("The package name of the extension $sourceClass must start with " +
        "\"$expectedPkgName\"")
      return false
    }

    return true
  }

  /**
   * Returns true if the given source class doesn't define deep links or implements DeepLinkSource.
   */
  private fun checkImplementsDeepLink(sourceClass: Element): Boolean {
    val hasDeeplinks = processingEnv.options["MANIFEST_HAS_DEEPLINKS"]?.toBoolean() ?: false
    if (!hasDeeplinks) return true

    return if (!types.isAssignable(sourceClass.asType(), sourceTypes.deeplinksource)) {
      logger.e("The manifest of $sourceClass defines deep links but the class doesn't " +
        "implement ${sourceTypes.deeplinksource}")
      false
    } else {
      true
    }
  }

  /**
   * Generates the class for the given source.
   */
  private fun generateClass(sourceClass: Element) {
    val className = sourceClass.simpleName.toString()
    val pkgName = elements.getPackageOf(sourceClass).toString()
    val fileName = "${className}Gen"
    val options = processingEnv.options
    val sourceName = options["SOURCE_NAME"] ?: className
    val lang = options["SOURCE_LANG"] as String
    val id = options["SOURCE_ID"]

    val classBuilder = TypeSpec.classBuilder(fileName)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .addParameter("deps", sourceTypes.dependencies.asTypeName())
          .build()
      )
      .superclass(ClassName.bestGuess(className))
      .addSuperclassConstructorParameter("%L", "deps")
      .addProperty(
        PropertySpec.builder("name", String::class, KModifier.OVERRIDE)
          .initializer("%S", sourceName)
          .build()
      )
      .addProperty(
        PropertySpec.builder("lang", String::class, KModifier.OVERRIDE)
          .initializer("%S", lang)
          .build()
      )
      .addProperty(
        PropertySpec.builder("id", Long::class, KModifier.OVERRIDE)
          .initializer("%L", id)
          .build()
      )

    val file = FileSpec.builder(pkgName, fileName)
      .addType(classBuilder.build())
      .build()

    val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
    file.writeTo(File(kaptKotlinGeneratedDir))

    File(kaptKotlinGeneratedDir, "index.txt").writeText("$pkgName.$fileName\n")
  }

  /**
   * Returns the build directory for this processor instance.
   */
  private fun getBuildDir(): String {
    val filer = processingEnv.filer
    val resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "tmp", null)
    val outputDir = Paths.get(resource.toUri()).toString()
    resource.delete()
    return outputDir
  }

  private companion object {
    const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
  }

}
