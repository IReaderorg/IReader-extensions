package tachiyomix.compiler

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import tachiyomix.annotations.Extension
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@Suppress("unused")
@AutoService(Processor::class)
class ExtensionProcessor : AbstractProcessor() {

  private lateinit var messager: Messager

  override fun init(env: ProcessingEnvironment) {
    super.init(env)
    messager = env.messager
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
    val extAnnotations = roundEnv.getElementsAnnotatedWith(Extension::class.java)
    if (extAnnotations.size > 1) {
      logError("Only one class can be annotated with @Extension")
      return true
    }

    val sourceClass = extAnnotations.firstOrNull() ?: return true

    val className = sourceClass.simpleName.toString()
    val pkgName = processingEnv.elementUtils.getPackageOf(sourceClass).toString()

    if (!pkgName.startsWith("tachiyomix.")) {
      logError("The package name of an extension must start with \"tachiyomix.\"")
      return true
    }

    if (!checkImplementsDeepLink(sourceClass)) {
      logError("The manifest defines deep links but $pkgName.${sourceClass.simpleName} doesn't " +
        "implement DeepLinkSource")
      return true
    }

    try {
      generateClass(className, pkgName)
    } catch (e: Exception) {
      messager.printMessage(Diagnostic.Kind.ERROR, e.message)
    }

    return true
  }

  private fun generateClass(className: String, pkgName: String) {
    val fileName = "${className}Gen"
    val options = processingEnv.options
    val sourceName = options["SOURCE_NAME"] ?: className
    val lang = options["SOURCE_LANG"] as String
    val id = options["SOURCE_ID"]

    val classBuilder = TypeSpec.classBuilder(fileName)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .addParameter("deps", DEPS_CLASS)
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

  private fun checkImplementsDeepLink(sourceClass: Element): Boolean {
    val hasDeeplinks = processingEnv.options["MANIFEST_HAS_DEEPLINKS"]?.toBoolean() ?: false
    if (!hasDeeplinks) return true

    val dlInterface = processingEnv.elementUtils.getTypeElement("tachiyomi.source.DeepLinkSource")
    return processingEnv.typeUtils.isAssignable(sourceClass.asType(), dlInterface.asType())
  }

  private fun log(message: String) {
    messager.printMessage(Diagnostic.Kind.WARNING, message)
  }

  private fun logError(message: String) {
    messager.printMessage(Diagnostic.Kind.ERROR, message)
  }

  private companion object {
    const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

    val DEPS_CLASS = ClassName.bestGuess("tachiyomi.source.Dependencies")
  }

}
