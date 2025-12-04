package tachiyomix.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.ksp.writeTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * KSP Processor that automatically generates stable source IDs.
 * 
 * Features:
 * 1. Generates deterministic IDs from source name + lang + version
 * 2. Maintains a registry of all source IDs to prevent collisions
 * 3. Supports migration from manual IDs via @AutoSourceId(seed = "oldname")
 * 4. Generates a source-ids.json registry file for reference
 * 
 * Usage:
 * ```kotlin
 * @Extension
 * @AutoSourceId  // ID auto-generated from name + lang
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
 *     override val lang = "en"
 *     override val name = "My Source"
 *     // override val id = MySourceConfig.ID  // Use generated ID
 * }
 * ```
 */
class SourceIdProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var processed = false
    private val sourceRegistry = mutableListOf<SourceIdEntry>()

    @Serializable
    data class SourceIdEntry(
        val className: String,
        val packageName: String,
        val name: String,
        val lang: String,
        val id: Long,
        val seed: String,
        val version: Int
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        // Process @AutoSourceId annotations
        val autoIdSources = resolver.getSymbolsWithAnnotation(AUTO_SOURCE_ID_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        autoIdSources.forEach { classDecl ->
            processAutoSourceId(classDecl)
        }

        // Also process all @Extension classes to build a complete registry
        val allExtensions = resolver.getSymbolsWithAnnotation(EXTENSION_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        allExtensions.forEach { classDecl ->
            if (classDecl !in autoIdSources) {
                collectExistingSourceId(classDecl)
            }
        }

        // Generate the registry file
        if (sourceRegistry.isNotEmpty()) {
            generateSourceIdRegistry()
        }

        // Check for ID collisions
        checkForCollisions()

        processed = true
        return emptyList()
    }

    private fun processAutoSourceId(classDecl: KSClassDeclaration) {
        val annotation = classDecl.annotations.find { 
            it.shortName.asString() == "AutoSourceId" 
        } ?: return
        
        val seed = getAnnotationArgument(annotation, "seed", "")
        val version = getAnnotationArgument(annotation, "version", 1)
        
        // Get name and lang from build options or class properties
        val (name, lang) = extractNameAndLang(classDecl)
        
        val idSeed = seed.ifEmpty { name }
        val sourceId = generateSourceId(idSeed, lang, version)
        
        val className = classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()
        
        // Add to registry
        sourceRegistry.add(SourceIdEntry(
            className = className,
            packageName = packageName,
            name = name,
            lang = lang,
            id = sourceId,
            seed = idSeed,
            version = version
        ))
        
        logger.info("Generated ID for $name ($lang): $sourceId [seed=$idSeed, v=$version]")
        
        // Generate the ID constant file
        generateIdConstant(classDecl, sourceId, name, lang)
    }

    private fun collectExistingSourceId(classDecl: KSClassDeclaration) {
        val className = classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()
        
        // Try to get from build options
        val buildDir = getBuildDir()
        val variant = getVariant(buildDir)
        
        val name = options["${variant}_name"] ?: className
        val lang = options["${variant}_lang"] ?: "en"
        val id = options["${variant}_id"]?.toLongOrNull() ?: generateSourceId(name, lang, 1)
        
        sourceRegistry.add(SourceIdEntry(
            className = className,
            packageName = packageName,
            name = name,
            lang = lang,
            id = id,
            seed = name,
            version = 1
        ))
    }

    private fun extractNameAndLang(classDecl: KSClassDeclaration): Pair<String, String> {
        val className = classDecl.simpleName.asString()
        
        // Try to get from build options first
        val buildDir = getBuildDir()
        val variant = getVariant(buildDir)
        
        val name = options["${variant}_name"] ?: className
        val lang = options["${variant}_lang"] ?: "en"
        
        return name to lang
    }

    private fun generateSourceId(name: String, lang: String, version: Int): Long {
        val key = "${name.lowercase()}/$lang/$version"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    private fun generateIdConstant(
        classDecl: KSClassDeclaration,
        sourceId: Long,
        name: String,
        lang: String
    ) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()
        
        val fileSpec = FileSpec.builder(packageName, "${className}SourceId")
            .addFileComment(
                """
                |Auto-generated source ID for $className
                |Name: $name
                |Lang: $lang
                |
                |Usage in your source class:
                |  override val id: Long get() = ${className}SourceId.ID
                """.trimMargin()
            )
            .addType(
                com.squareup.kotlinpoet.TypeSpec.objectBuilder("${className}SourceId")
                    .addProperty(
                        PropertySpec.builder("ID", Long::class)
                            .initializer("%LL", sourceId)
                            .addKdoc("Stable source ID generated from name='$name', lang='$lang'")
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("NAME", String::class)
                            .initializer("%S", name)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("LANG", String::class)
                            .initializer("%S", lang)
                            .build()
                    )
                    .build()
            )
            .build()
        
        try {
            fileSpec.writeTo(codeGenerator, Dependencies(false, classDecl.containingFile!!))
            logger.info("Generated ${className}SourceId.kt")
        } catch (e: Exception) {
            // File might already exist
            logger.warn("Could not generate ${className}SourceId.kt: ${e.message}")
        }
    }

    private fun generateSourceIdRegistry() {
        val json = Json { 
            prettyPrint = true 
            encodeDefaults = true
        }
        
        val registryContent = json.encodeToString(sourceRegistry.sortedBy { it.name })
        
        try {
            codeGenerator.createNewFileByPath(
                Dependencies(false),
                "source-ids",
                "json"
            ).use { output ->
                output.write(registryContent.toByteArray())
            }
            logger.info("Generated source-ids.json with ${sourceRegistry.size} sources")
        } catch (e: Exception) {
            logger.warn("Could not generate source-ids.json: ${e.message}")
        }
    }

    private fun checkForCollisions() {
        val idGroups = sourceRegistry.groupBy { it.id }
        val collisions = idGroups.filter { it.value.size > 1 }
        
        if (collisions.isNotEmpty()) {
            collisions.forEach { (id, sources) ->
                val sourceList = sources.joinToString("\n") { 
                    "    • ${it.name} (${it.lang}) → ${it.packageName}.${it.className}" 
                }
                logger.error(
                    """
                    |
                    |╔══════════════════════════════════════════════════════════════════╗
                    |║  ⚠️  SOURCE ID COLLISION DETECTED!                               ║
                    |╚══════════════════════════════════════════════════════════════════╝
                    |
                    |Two or more sources have the same ID: $id
                    |
                    |Conflicting sources:
                    |$sourceList
                    |
                    |┌─────────────────────────────────────────────────────────────────┐
                    |│  HOW TO FIX:                                                    │
                    |│                                                                 │
                    |│  Add a unique seed to one of the sources:                       │
                    |│                                                                 │
                    |│    @AutoSourceId(seed = "unique_name_here")                     │
                    |│                                                                 │
                    |│  This will generate a different ID for that source.             │
                    |└─────────────────────────────────────────────────────────────────┘
                    |
                    """.trimMargin()
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getAnnotationArgument(annotation: KSAnnotation, name: String, default: T): T {
        return annotation.arguments.find { it.name?.asString() == name }?.value as? T ?: default
    }

    private fun String.covertToOsPath(): String {
        return if (System.getProperty("os.name").contains("win", true)) {
            this.replace("\\", "/")
        } else {
            this
        }
    }

    private fun getBuildDir(): String {
        return try {
            codeGenerator.generatedFile.firstOrNull()?.path?.substringBefore("/resources/")
                ?.substringBefore("\\resources\\")?.covertToOsPath() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getVariant(buildDir: String): String {
        val build = buildDir.substringAfterLast("/ksp/").substringBefore('/')
        return build.removeSuffix("Debug").removeSuffix("Release")
    }

    companion object {
        const val EXTENSION_ANNOTATION = "tachiyomix.annotations.Extension"
        const val AUTO_SOURCE_ID_ANNOTATION = "tachiyomix.annotations.AutoSourceId"
    }
}

class SourceIdProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SourceIdProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
