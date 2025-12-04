package tachiyomix.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * KSP Processor that generates deep link handling code from annotations.
 * 
 * Generates:
 * - URL pattern matchers
 * - Deep link handlers
 * - Intent filter XML (as comments for manual copy)
 */
class DeepLinkProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val sourcesWithDeepLinks = resolver.getSymbolsWithAnnotation(DEEP_LINK_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .distinctBy { it.qualifiedName?.asString() }
            .toList()

        sourcesWithDeepLinks.forEach { generateDeepLinkHandler(it) }

        return emptyList()
    }

    private fun generateDeepLinkHandler(source: KSClassDeclaration) {
        val deepLinks = source.annotations.filter { 
            it.shortName.asString() == "SourceDeepLink" 
        }.toList()

        if (deepLinks.isEmpty()) return

        val packageName = source.packageName.asString()
        val className = source.simpleName.asString()
        val generatedClassName = "${className}DeepLinks"

        val uriClass = ClassName("android.net", "Uri")
        val mangaInfoClass = ClassName("ireader.core.source.model", "MangaInfo")
        val chapterInfoClass = ClassName("ireader.core.source.model", "ChapterInfo")

        val objectBuilder = TypeSpec.objectBuilder(generatedClassName)
            .addKdoc("Generated deep link handlers for $className\n\n")
            .addKdoc("Add the following to your AndroidManifest.xml:\n")

        // Generate manifest XML as KDoc
        val manifestXml = StringBuilder()
        manifestXml.append("```xml\n")
        deepLinks.forEach { deepLink ->
            val args = deepLink.arguments.associate { it.name?.asString() to it.value }
            val host = args["host"] as? String ?: ""
            val scheme = args["scheme"] as? String ?: "https"
            val pathPattern = args["pathPattern"] as? String ?: ""

            manifestXml.append("<intent-filter>\n")
            manifestXml.append("    <action android:name=\"android.intent.action.VIEW\" />\n")
            manifestXml.append("    <category android:name=\"android.intent.category.DEFAULT\" />\n")
            manifestXml.append("    <category android:name=\"android.intent.category.BROWSABLE\" />\n")
            manifestXml.append("    <data\n")
            manifestXml.append("        android:scheme=\"$scheme\"\n")
            manifestXml.append("        android:host=\"$host\"\n")
            if (pathPattern.isNotEmpty()) {
                manifestXml.append("        android:pathPattern=\"$pathPattern\"\n")
            }
            manifestXml.append("    />\n")
            manifestXml.append("</intent-filter>\n")
        }
        manifestXml.append("```\n")
        objectBuilder.addKdoc(manifestXml.toString())

        // Store deep link patterns
        val patternsBuilder = CodeBlock.builder()
            .add("listOf(\n")
        
        deepLinks.forEachIndexed { index, deepLink ->
            val args = deepLink.arguments.associate { it.name?.asString() to it.value }
            val host = args["host"] as? String ?: ""
            val scheme = args["scheme"] as? String ?: "https"
            val pathPattern = args["pathPattern"] as? String ?: ""
            val type = args["type"] as? String ?: "MANGA"

            patternsBuilder.add("    DeepLinkPattern(\n")
            patternsBuilder.add("        host = %S,\n", host)
            patternsBuilder.add("        scheme = %S,\n", scheme)
            patternsBuilder.add("        pathPattern = %S,\n", pathPattern)
            patternsBuilder.add("        type = DeepLinkType.$type\n")
            patternsBuilder.add("    )")
            if (index < deepLinks.size - 1) {
                patternsBuilder.add(",")
            }
            patternsBuilder.add("\n")
        }
        patternsBuilder.add(")")

        // Add DeepLinkType enum
        val deepLinkTypeEnum = TypeSpec.enumBuilder("DeepLinkType")
            .addEnumConstant("MANGA")
            .addEnumConstant("CHAPTER")
            .addEnumConstant("SEARCH")
            .build()

        objectBuilder.addType(deepLinkTypeEnum)

        // Add DeepLinkPattern data class
        val deepLinkPatternClass = TypeSpec.classBuilder("DeepLinkPattern")
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("host", String::class)
                    .addParameter("scheme", String::class)
                    .addParameter("pathPattern", String::class)
                    .addParameter("type", ClassName(packageName, generatedClassName, "DeepLinkType"))
                    .build()
            )
            .addProperty(PropertySpec.builder("host", String::class).initializer("host").build())
            .addProperty(PropertySpec.builder("scheme", String::class).initializer("scheme").build())
            .addProperty(PropertySpec.builder("pathPattern", String::class).initializer("pathPattern").build())
            .addProperty(PropertySpec.builder("type", ClassName(packageName, generatedClassName, "DeepLinkType")).initializer("type").build())
            .build()

        objectBuilder.addType(deepLinkPatternClass)

        // Add patterns property
        val patternListType = List::class.asClassName().parameterizedBy(
            ClassName(packageName, generatedClassName, "DeepLinkPattern")
        )
        objectBuilder.addProperty(
            PropertySpec.builder("patterns", patternListType)
                .initializer(patternsBuilder.build())
                .build()
        )

        // Add canHandle function
        objectBuilder.addFunction(
            FunSpec.builder("canHandle")
                .addParameter("url", String::class)
                .returns(Boolean::class)
                .addStatement("return patterns.any { pattern ->")
                .addStatement("    url.startsWith(\"\${pattern.scheme}://\${pattern.host}\")")
                .addStatement("}")
                .build()
        )

        // Add getType function
        objectBuilder.addFunction(
            FunSpec.builder("getType")
                .addParameter("url", String::class)
                .returns(ClassName(packageName, generatedClassName, "DeepLinkType").copy(nullable = true))
                .addStatement("return patterns.find { pattern ->")
                .addStatement("    url.startsWith(\"\${pattern.scheme}://\${pattern.host}\")")
                .addStatement("}?.type")
                .build()
        )

        // Add extractPath function
        objectBuilder.addFunction(
            FunSpec.builder("extractPath")
                .addParameter("url", String::class)
                .returns(String::class.asTypeName().copy(nullable = true))
                .addStatement("return try {")
                .addStatement("    val uri = %T.parse(url)", uriClass)
                .addStatement("    uri.path")
                .addStatement("} catch (e: Exception) {")
                .addStatement("    null")
                .addStatement("}")
                .build()
        )

        // Add extractMangaKey function
        objectBuilder.addFunction(
            FunSpec.builder("extractMangaKey")
                .addParameter("url", String::class)
                .returns(String::class.asTypeName().copy(nullable = true))
                .addKdoc("Extract manga/novel key from deep link URL.\n")
                .addKdoc("Override this in your source if the URL pattern is different.\n")
                .addStatement("val path = extractPath(url) ?: return null")
                .addStatement("// Default: use the full path as the key")
                .addStatement("return url")
                .build()
        )

        FileSpec.builder(packageName, generatedClassName)
            .addType(objectBuilder.build())
            .addFileComment("Generated deep link handlers - DO NOT EDIT")
            .build()
            .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))

        logger.info("Generated deep link handler: $packageName.$generatedClassName")
    }

    companion object {
        const val DEEP_LINK_ANNOTATION = "tachiyomix.annotations.SourceDeepLink"
    }
}

class DeepLinkProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DeepLinkProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
