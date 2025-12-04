package tachiyomix.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * KSP Processor that generates the final Extension class from @Extension annotated sources.
 * 
 * This processor:
 * 1. Finds classes annotated with @Extension
 * 2. Validates they are open/abstract and implement Source
 * 3. Generates a concrete Extension class with name, lang, id properties
 * 
 * Supports multi-round processing for sources that reference generated code
 * (e.g., SunovelsGenerated from SourceFactoryProcessor).
 */
class ExtensionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        val allExtensions = resolver.getSymbolsWithAnnotation(EXTENSION_FQ_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        if (allExtensions.isEmpty()) {
            val extensionGenerated = resolver.getClassDeclarationByName(EXTENSION_FQ_CLASS) != null
            if (!extensionGenerated) {
                return emptyList()
            }
            processed = true
            return emptyList()
        }

        val validExtensions = allExtensions.filter { it.validate() }
        val deferredExtensions = allExtensions.filterNot { it.validate() }

        if (validExtensions.isEmpty() && deferredExtensions.isNotEmpty()) {
            return deferredExtensions
        }

        val extension = getClassToGenerate(validExtensions)

        if (extension == null) {
            if (deferredExtensions.isNotEmpty()) {
                return deferredExtensions
            }
            val extensionGenerated = resolver.getClassDeclarationByName(EXTENSION_FQ_CLASS) != null
            check(extensionGenerated) {
                "No extension found. Please ensure at least one Source is annotated with @Extension"
            }
            processed = true
            return emptyList()
        }

        val extensionType = extension.asStarProjectedType()

        val buildDir = getBuildDir()
        val variant = getVariant(buildDir)
        val arguments = parseArguments(variant)

        check(extension.isOpen()) {
            "[$extension] must be open or abstract"
        }

        val sourceClass = resolver.getClassDeclarationByName(SOURCE_FQ_CLASS) 
            ?: throw Exception("This class is not implementing the Source interface")

        check(sourceClass.asStarProjectedType().isAssignableFrom(extensionType)) {
            "$extension doesn't implement $sourceClass"
        }

        if (arguments.hasDeeplinks) {
            val deepLinkClass = resolver.getClassDeclarationByName(DEEPLINKSOURCE_FQ_CLASS)!!
            check(deepLinkClass.asStarProjectedType().isAssignableFrom(extensionType)) {
                "Deep links of $extension were defined but the extension doesn't implement $deepLinkClass"
            }
        }

        checkMatchesPkgName(extension, buildDir)

        val dependencies = resolver.getClassDeclarationByName(DEPENDENCIES_FQ_CLASS)!!
        extension.accept(SourceVisitor(arguments, dependencies), Unit)

        processed = true
        return deferredExtensions
    }

    private fun getClassToGenerate(extensions: List<KSClassDeclaration>): KSClassDeclaration? {
        return when (extensions.size) {
            0 -> null
            1 -> extensions.first()
            else -> {
                val candidate = extensions.find { candidate ->
                    val type = candidate.asStarProjectedType()
                    extensions.all { it === candidate || it.asStarProjectedType().isAssignableFrom(type) }
                }
                checkNotNull(candidate) {
                    "Found [${extensions.joinToString()}] annotated with @Extension but they don't" +
                        " inherit each other. Only one class can be generated"
                }
                candidate
            }
        }
    }

    private fun checkMatchesPkgName(source: KSClassDeclaration, buildDir: String) {
        if ("/sources/multisrc" in buildDir || "\\sources\\multisrc" in buildDir) return

        val pkgName = source.packageName.asString()
        val normalizedBuildDir = buildDir.replace("\\", "/")
        val sourceDir = normalizedBuildDir.substringBeforeLast("/build/").substringAfterLast("/")
        val expectedPkgName = "ireader.$sourceDir"

        val isValidPackage = Regex("^$expectedPkgName\\.?.*?").matches(pkgName)
        check(isValidPackage) {
            "The package name of the extension $source must start with \"$expectedPkgName\" which right now is \"$pkgName\""
        }
    }

    private inner class SourceVisitor(
        val arguments: Arguments,
        val dependencies: KSClassDeclaration,
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

    private fun String.convertToOsPath(): String {
        return if (System.getProperty("os.name").contains("win", true)) {
            this.replace("\\", "/")
        } else {
            this
        }
    }

    private fun CodeGenerator.collectVariantName(fileName: String): String {
        createNewFileByPath(Dependencies(false), fileName, "txt")
        return generatedFile.first().run {
            this.path.substringBefore("\\resources\\").substringBefore("/resources/")
        }
    }

    private fun getBuildDir(): String {
        return codeGenerator.collectVariantName("").convertToOsPath()
    }

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
        const val SOURCE_FQ_CLASS = "ireader.core.source.Source"
        const val DEEPLINKSOURCE_FQ_CLASS = "ireader.core.source.DeepLinkSource"
        const val DEPENDENCIES_FQ_CLASS = "ireader.core.source.Dependencies"
        const val EXTENSION_FQ_ANNOTATION = "tachiyomix.annotations.Extension"
        const val EXTENSION_PACKAGE = "tachiyomix.extension"
        const val EXTENSION_CLASS = "Extension"
        const val EXTENSION_FQ_CLASS = "$EXTENSION_PACKAGE.$EXTENSION_CLASS"
    }
}

class ExtensionProcessorFactory : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ExtensionProcessor(environment.codeGenerator, environment.logger, environment.options)
    }
}
