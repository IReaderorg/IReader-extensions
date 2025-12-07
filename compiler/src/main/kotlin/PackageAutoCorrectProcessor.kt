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
import java.io.File

/**
 * KSP Processor that validates package names against directory structure
 * and generates fix scripts when mismatches are detected.
 *
 * Expected pattern: ireader.{sourceDir}
 * Example: sources/en/daonovel/main/src/ireader/daonovel/DaoNovel.kt
 *          -> package ireader.daonovel
 *
 * If the package is "ireader.dao" but directory is "daonovel",
 * this processor will:
 * 1. Log a warning
 * 2. Generate a fix script to auto-correct the package
 */
class PackageAutoCorrectProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var processed = false
    private val packageMismatches = mutableListOf<PackageMismatch>()

    data class PackageMismatch(
        val filePath: String,
        val className: String,
        val currentPackage: String,
        val expectedPackage: String,
        val sourceDir: String
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        // Check all @Extension annotated classes
        val extensions = resolver.getSymbolsWithAnnotation(EXTENSION_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }

        extensions.forEach { classDecl ->
            checkPackage(classDecl)
        }

        // Generate fix report if there are mismatches
        if (packageMismatches.isNotEmpty()) {
            generateFixReport()
            generateFixScript()
        }

        processed = true
        return emptyList()
    }

    private fun checkPackage(classDecl: KSClassDeclaration) {
        val containingFile = classDecl.containingFile ?: return
        val filePath = containingFile.filePath.replace("\\", "/")

        // Skip multisrc and generated files
        if ("/multisrc/" in filePath || "/build/" in filePath) return

        val currentPackage = classDecl.packageName.asString()
        val sourceDir = extractSourceDir(filePath) ?: return
        val expectedPackage = "ireader.$sourceDir"

        // Check if package matches expected pattern
        if (!currentPackage.startsWith(expectedPackage)) {
            val mismatch = PackageMismatch(
                filePath = filePath,
                className = classDecl.simpleName.asString(),
                currentPackage = currentPackage,
                expectedPackage = expectedPackage,
                sourceDir = sourceDir
            )
            packageMismatches.add(mismatch)

            logger.warn(
                """
                |
                |╔══════════════════════════════════════════════════════════════════╗
                |║  📦 PACKAGE NAME MISMATCH                                        ║
                |╚══════════════════════════════════════════════════════════════════╝
                |
                |Class: ${mismatch.className}
                |
                |Your package:    ${mismatch.currentPackage}
                |Expected:        ${mismatch.expectedPackage}
                |
                |The package name should match your folder name!
                |Folder: sources/.../[${mismatch.sourceDir}]/
                |
                |┌─────────────────────────────────────────────────────────────────┐
                |│  HOW TO FIX:                                                    │
                |│                                                                 │
                |│  Open: ${mismatch.filePath.substringAfterLast("/")}
                |│                                                                 │
                |│  Change the first line from:                                    │
                |│    package ${mismatch.currentPackage}                           │
                |│                                                                 │
                |│  To:                                                            │
                |│    package ${mismatch.expectedPackage}                          │
                |└─────────────────────────────────────────────────────────────────┘
                |
                """.trimMargin()
            )
        }
    }

    private fun extractSourceDir(filePath: String): String? {
        // Pattern: sources/{lang}/{sourceDir}/...
        val sourcesMatch = Regex(".*/sources/[^/]+/([^/]+)/.*").find(filePath)
        if (sourcesMatch != null) {
            return sourcesMatch.groupValues[1]
        }

        // Pattern: sources-v5-batch/{lang}/{sourceDir}/...
        val v5Match = Regex(".*/sources-v5-batch/[^/]+/([^/]+)/.*").find(filePath)
        if (v5Match != null) {
            return v5Match.groupValues[1]
        }

        return null
    }

    private fun generateFixReport() {
        try {
            codeGenerator.createNewFileByPath(
                Dependencies(false),
                "package-mismatch-report",
                "md"
            ).use { output ->
                val content = buildString {
                    appendLine("# Package Mismatch Report")
                    appendLine()
                    appendLine("The following sources have package names that don't match their directory structure:")
                    appendLine()

                    packageMismatches.forEach { mismatch ->
                        appendLine("## ${mismatch.className}")
                        appendLine("- **File:** `${mismatch.filePath}`")
                        appendLine("- **Current package:** `${mismatch.currentPackage}`")
                        appendLine("- **Expected package:** `${mismatch.expectedPackage}`")
                        appendLine()
                        appendLine("```kotlin")
                        appendLine("// Change from:")
                        appendLine("package ${mismatch.currentPackage}")
                        appendLine()
                        appendLine("// To:")
                        appendLine("package ${mismatch.expectedPackage}")
                        appendLine("```")
                        appendLine()
                    }

                    appendLine("---")
                    appendLine("*Generated by PackageAutoCorrectProcessor*")
                }
                output.write(content.toByteArray())
            }
            logger.info("Generated package-mismatch-report.md with ${packageMismatches.size} issues")
        } catch (e: Exception) {
            logger.warn("Could not generate fix report: ${e.message}")
        }
    }

    private fun generateFixScript() {
        // Generate a Kotlin script that can fix the packages
        try {
            codeGenerator.createNewFileByPath(
                Dependencies(false),
                "fix-packages",
                "kts"
            ).use { output ->
                val content = buildString {
                    appendLine("#!/usr/bin/env kotlin")
                    appendLine()
                    appendLine("/**")
                    appendLine(" * Auto-generated script to fix package mismatches.")
                    appendLine(" * Run with: kotlin fix-packages.kts")
                    appendLine(" */")
                    appendLine()
                    appendLine("import java.io.File")
                    appendLine()
                    appendLine("val fixes = listOf(")

                    packageMismatches.forEach { mismatch ->
                        appendLine("    Triple(")
                        appendLine("        \"${mismatch.filePath}\",")
                        appendLine("        \"package ${mismatch.currentPackage}\",")
                        appendLine("        \"package ${mismatch.expectedPackage}\"")
                        appendLine("    ),")
                    }

                    appendLine(")")
                    appendLine()
                    appendLine("fixes.forEach { (path, oldPkg, newPkg) ->")
                    appendLine("    val file = File(path)")
                    appendLine("    if (file.exists()) {")
                    appendLine("        val content = file.readText()")
                    appendLine("        if (content.contains(oldPkg)) {")
                    appendLine("            file.writeText(content.replace(oldPkg, newPkg))")
                    appendLine("            println(\"Fixed: \$path\")")
                    appendLine("        }")
                    appendLine("    }")
                    appendLine("}")
                    appendLine()
                    appendLine("println(\"Done! Fixed \${fixes.size} files.\")")
                }
                output.write(content.toByteArray())
            }
            logger.info("Generated fix-packages.kts script")
        } catch (e: Exception) {
            logger.warn("Could not generate fix script: ${e.message}")
        }
    }

    companion object {
        const val EXTENSION_ANNOTATION = "tachiyomix.annotations.Extension"
    }
}

class PackageAutoCorrectProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return PackageAutoCorrectProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
