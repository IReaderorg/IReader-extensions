package tachiyomix.compiler

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.File

@KotlinPoetKspPreview
class ExtensionProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val options: Map<String, String>
) : SymbolProcessor {

  override fun process(resolver: Resolver): List<KSAnnotated> {
    // Get all classes annotated with the Extension annotation
    val extensions = resolver.getSymbolsWithAnnotation(EXTENSION_FQ_ANNOTATION)

    // Find the extension that will be instantiated by Tachiyomi
    val extension = getClassToGenerate(extensions)

    // If no extension is found, we either have already processed it or we forgot to annotate our
    // implementation, so make sure we always have one
    if (extension == null) {
      val extensionGenerated = resolver.getClassDeclarationByName(EXTENSION_FQ_CLASS) != null
      check(extensionGenerated) {
        "No extension found. Please ensure at least one Source is annotated with @Extension"
      }
      return emptyList()
    }

    val extensionType = extension.asStarProjectedType()

    val buildDir = getBuildDir()
    val variant = getVariant(buildDir)
    val arguments = parseArguments(variant)

    // Check that the extension is open or abstract
    check(extension.isOpen()) {
      "[$extension] must be open or abstract"
    }

    // Check that the extension implements the Source interface
    val sourceClass = resolver.getClassDeclarationByName(SOURCE_FQ_CLASS)!!
    check(sourceClass.asStarProjectedType().isAssignableFrom(extensionType)) {
      "$extension doesn't implement $sourceClass"
    }

    // Check that the extension implements the DeepLinkSource interface if the manifest has them
    if (arguments.hasDeeplinks) {
      val deepLinkClass = resolver.getClassDeclarationByName(DEEPLINKSOURCE_FQ_CLASS)!!
      check(deepLinkClass.asStarProjectedType().isAssignableFrom(extensionType)) {
        "Deep links of $extension were defined but the extension doesn't implement $deepLinkClass"
      }
    }

    // Check that the extension matches its package name
    checkMatchesPkgName(extension, buildDir)

    // Generate the source implementation
    val dependencies = resolver.getClassDeclarationByName(DEPENDENCIES_FQ_CLASS)!!
    extension.accept(SourceVisitor(arguments, dependencies), Unit)

    return emptyList()
  }

  /**
   * Returns the source class to generate, or null if more than one was detected and they don't
   * inherit each other.
   */
  private fun getClassToGenerate(extensions: Sequence<KSAnnotated>): KSClassDeclaration? {
    val candidates = extensions.filterIsInstance<KSClassDeclaration>()
      .filter { it.validate() }
      .toList()

    val classToGenerate = when (candidates.size) {
      0 -> return null
      1 -> candidates.first()
      else -> {
        val candidate = candidates.find { candidate ->
          val type = candidate.asStarProjectedType()
          candidates.all { it === candidate || it.asStarProjectedType().isAssignableFrom(type) }
        }
        checkNotNull(candidate) {
          "Found [${candidates.joinToString()}] annotated with @Extension but they don't" +
                  " inherit each other. Only one class can be generated"
        }
        candidate
      }
    }
    return classToGenerate
  }

  private fun checkMatchesPkgName(source: KSClassDeclaration, buildDir: String) {
    if ("/samples/" in buildDir) return // Disable pkgname checks for the samples

    val pkgName = source.packageName.asString()

    val sourceDir = buildDir.substringBeforeLast("/build/").substringAfterLast("/")
    val expectedPkgName = "tachiyomix.$sourceDir"

    val isValidPackage = Regex("^$expectedPkgName\\.?.*?").matches(pkgName)
    check(isValidPackage) {
      "The package name of the extension $source must start with \"$expectedPkgName\""
    }
  }

  private inner class SourceVisitor(
    val arguments: Arguments,
    val dependencies: KSClassDeclaration
  ) : KSVisitorVoid() {
    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
      val classSpec = TypeSpec.classBuilder(EXTENSION_CLASS)
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .addParameter("deps", dependencies.toClassName())
            .build()
        )
        .superclass(classDeclaration.toClassName())
        .addSuperclassConstructorParameter("%L", "deps")
        .addProperty(
          PropertySpec.builder("name", String::class, KModifier.OVERRIDE)
            .initializer("%S", arguments.name)
            .build()
        )
        .addProperty(
          PropertySpec.builder("lang", String::class, KModifier.OVERRIDE)
            .initializer("%S", arguments.lang)
            .build()
        )
        .addProperty(
          PropertySpec.builder("id", Long::class, KModifier.OVERRIDE)
            .initializer("%L", arguments.id)
            .build()
        )
        .build()

      FileSpec.builder(EXTENSION_PACKAGE, EXTENSION_CLASS)
        .addType(classSpec)
        .build()
        .writeTo(codeGenerator, Dependencies(false, classDeclaration.containingFile!!))
    }
  }

  private fun getBuildDir(): String {
    val pathOf = codeGenerator::class.java
      .getDeclaredMethod("pathOf", String::class.java, String::class.java, String::class.java)
    val stubFile = pathOf.invoke(codeGenerator, "", "a", "kt") as String
    return File(stubFile).parentFile.parent
  }

  // TODO: this is temporary until ksp configurations are applied per variant rather than globally
  private fun getVariant(buildDir: String): String {
    val build = buildDir.substringAfterLast("/ksp/").substringBefore('/')
    return build.removeSuffix("Debug").removeSuffix("Release")
  }

  private fun parseArguments(variant: String): Arguments {
    return Arguments(
      name = options["${variant}_name"]!!,
      lang = options["${variant}_lang"]!!,
      id = options["${variant}_id"]!!.toLong(),
      hasDeeplinks = options["${variant}_has_deeplinks"].toBoolean()
    )
  }

  private data class Arguments(
    val name: String,
    val lang: String,
    val id: Long,
    val hasDeeplinks: Boolean
  )

  private companion object {
    const val SOURCE_FQ_CLASS = "tachiyomi.source.Source"
    const val DEEPLINKSOURCE_FQ_CLASS = "tachiyomi.source.DeepLinkSource"
    const val DEPENDENCIES_FQ_CLASS = "tachiyomi.source.Dependencies"
    const val EXTENSION_FQ_ANNOTATION = "tachiyomix.annotations.Extension"
    const val EXTENSION_PACKAGE = "tachiyomix.extension"
    const val EXTENSION_CLASS = "Extension"
    const val EXTENSION_FQ_CLASS = "$EXTENSION_PACKAGE.$EXTENSION_CLASS"
  }

}

@KotlinPoetKspPreview
class ExtensionProcessorFactory : SymbolProcessorProvider {

  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return ExtensionProcessor(environment.codeGenerator, environment.logger, environment.options)
  }

}
