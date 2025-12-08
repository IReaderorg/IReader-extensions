/*
 * Copyright (C) IReader Project
 * SPDX-License-Identifier: Apache-2.0
 */

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * KSP Processor that generates a repository index JSON file
 * containing metadata for all sources in the project.
 *
 * This enables automatic repository index generation at compile time.
 */
class SourceIndexProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val sources = mutableListOf<SourceInfo>()
    private var processed = false

    @Serializable
    data class SourceInfo(
        val name: String,
        val id: Long,
        val lang: String,
        val baseUrl: String,
        val description: String = "",
        val nsfw: Boolean = false,
        val icon: String = "",
        val version: Int = 1,
        val tags: List<String> = emptyList()
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        // Collect all @Extension annotated classes
        val extensions = resolver.getSymbolsWithAnnotation(EXTENSION_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        extensions.forEach { collectSourceInfo(it, resolver) }

        // Also collect @MadaraSource
        val madaraSources = resolver.getSymbolsWithAnnotation(MADARA_SOURCE_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        madaraSources.forEach { collectMadaraSourceInfo(it) }

        // Also collect @ThemeSource
        val themeSources = resolver.getSymbolsWithAnnotation(THEME_SOURCE_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        themeSources.forEach { collectThemeSourceInfo(it) }

        // Generate index if we have sources
        if (sources.isNotEmpty()) {
            generateIndex()
        }

        processed = true
        return emptyList()
    }

    private fun collectSourceInfo(extension: KSClassDeclaration, resolver: Resolver) {
        // Try to extract info from the class properties
        val className = extension.simpleName.asString()
        val packageName = extension.packageName.asString()

        // Check for @SourceMeta annotation
        val metaAnnotation = extension.annotations.find {
            it.shortName.asString() == "SourceMeta"
        }

        val meta = metaAnnotation?.let { ann ->
            val args = ann.arguments.associate { it.name?.asString() to it.value }
            SourceMeta(
                description = args["description"] as? String ?: "",
                nsfw = args["nsfw"] as? Boolean ?: false,
                icon = args["icon"] as? String ?: "",
                tags = (args["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }

        // Extract source properties from the class
        val properties = extension.getAllProperties().associate {
            it.simpleName.asString() to it
        }

        val name = extractStringProperty(properties["name"]) ?: className
        val id = extractLongProperty(properties["id"]) ?: generateId(className, packageName)
        val lang = extractStringProperty(properties["lang"]) ?: "en"
        val baseUrl = extractStringProperty(properties["baseUrl"]) ?: ""

        sources.add(SourceInfo(
            name = name,
            id = id,
            lang = lang,
            baseUrl = baseUrl,
            description = meta?.description ?: "",
            nsfw = meta?.nsfw ?: false,
            icon = meta?.icon ?: "",
            tags = meta?.tags ?: emptyList()
        ))

        logger.info("Collected source: $name ($lang) - ID: $id")
    }

    private fun collectMadaraSourceInfo(config: KSClassDeclaration) {
        val annotation = config.annotations.first {
            it.shortName.asString() == "MadaraSource"
        }

        val args = annotation.arguments.associate { it.name?.asString() to it.value }

        val metaAnnotation = config.annotations.find {
            it.shortName.asString() == "SourceMeta"
        }
        val meta = metaAnnotation?.let { ann ->
            val metaArgs = ann.arguments.associate { it.name?.asString() to it.value }
            SourceMeta(
                description = metaArgs["description"] as? String ?: "",
                nsfw = metaArgs["nsfw"] as? Boolean ?: false,
                icon = metaArgs["icon"] as? String ?: "",
                tags = (metaArgs["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }

        sources.add(SourceInfo(
            name = args["name"] as String,
            id = args["id"] as Long,
            lang = args["lang"] as String,
            baseUrl = args["baseUrl"] as String,
            description = meta?.description ?: "",
            nsfw = meta?.nsfw ?: false,
            icon = meta?.icon ?: "",
            tags = meta?.tags ?: emptyList()
        ))
    }

    private fun collectThemeSourceInfo(config: KSClassDeclaration) {
        val annotation = config.annotations.first {
            it.shortName.asString() == "ThemeSource"
        }

        val args = annotation.arguments.associate { it.name?.asString() to it.value }

        val metaAnnotation = config.annotations.find {
            it.shortName.asString() == "SourceMeta"
        }
        val meta = metaAnnotation?.let { ann ->
            val metaArgs = ann.arguments.associate { it.name?.asString() to it.value }
            SourceMeta(
                description = metaArgs["description"] as? String ?: "",
                nsfw = metaArgs["nsfw"] as? Boolean ?: false,
                icon = metaArgs["icon"] as? String ?: "",
                tags = (metaArgs["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }

        sources.add(SourceInfo(
            name = args["name"] as String,
            id = args["id"] as Long,
            lang = args["lang"] as String,
            baseUrl = args["baseUrl"] as String,
            description = meta?.description ?: "",
            nsfw = meta?.nsfw ?: false,
            icon = meta?.icon ?: "",
            tags = meta?.tags ?: emptyList()
        ))
    }

    private fun generateIndex() {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        val indexContent = json.encodeToString(sources.sortedBy { it.name })

        // Generate as a resource file
        try {
            codeGenerator.createNewFileByPath(
                Dependencies(false),
                "source-index",
                "json"
            ).use { output ->
                output.write(indexContent.toByteArray())
            }
            logger.info("Generated source-index.json with ${sources.size} sources")
        } catch (e: Exception) {
            logger.warn("Could not generate source-index.json: ${e.message}")
        }
    }

    private fun extractStringProperty(property: com.google.devtools.ksp.symbol.KSPropertyDeclaration?): String? {
        // This is a simplified extraction - in practice you'd need to evaluate the initializer
        return null
    }

    private fun extractLongProperty(property: com.google.devtools.ksp.symbol.KSPropertyDeclaration?): Long? {
        return null
    }

    private fun generateId(name: String, lang: String): Long {
        val key = "${name.lowercase()}/$lang/1"
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    private data class SourceMeta(
        val description: String,
        val nsfw: Boolean,
        val icon: String,
        val tags: List<String>
    )

    companion object {
        const val EXTENSION_ANNOTATION = "tachiyomix.annotations.Extension"
        const val MADARA_SOURCE_ANNOTATION = "tachiyomix.annotations.MadaraSource"
        const val THEME_SOURCE_ANNOTATION = "tachiyomix.annotations.ThemeSource"
    }
}

class SourceIndexProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SourceIndexProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
