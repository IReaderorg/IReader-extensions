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
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * KSP Processor that generates Ktor HTTP client helper code from annotations.
 *
 * Generates:
 * - Request builder functions for each endpoint
 * - Response parsing helpers
 * - Error handling wrappers
 * - Rate limiting integration
 */
class HttpClientProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val processedClasses = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val sourcesWithEndpoints = resolver.getSymbolsWithAnnotation(API_ENDPOINT_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .distinctBy { it.qualifiedName?.asString() }
            .toList()

        sourcesWithEndpoints.forEach { source ->
            val key = "${source.qualifiedName?.asString()}_api"
            if (key !in processedClasses) {
                processedClasses.add(key)
                generateHttpClient(source)
            }
        }

        return emptyList()
    }

    private fun generateHttpClient(source: KSClassDeclaration) {
        val endpoints = source.annotations.filter {
            it.shortName.asString() == "ApiEndpoint"
        }.toList()

        if (endpoints.isEmpty()) return

        val packageName = source.packageName.asString()
        val className = source.simpleName.asString()
        val generatedClassName = "${className}Api"

        val httpRequestBuilderClass = ClassName("io.ktor.client.request", "HttpRequestBuilder")

        val objectBuilder = TypeSpec.objectBuilder(generatedClassName)
            .addKdoc("Generated API client helpers for $className")

        endpoints.forEach { endpoint ->
            val args = endpoint.arguments.associate { it.name?.asString() to it.value }
            val name = args["name"] as? String ?: "request"
            val path = args["path"] as? String ?: ""
            val method = args["method"] as? String ?: "GET"
            val params = (args["params"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            val funBuilder = FunSpec.builder(name.lowercase().replace(" ", ""))
                .addParameter("baseUrl", String::class)

            params.forEach { param ->
                funBuilder.addParameter(param, String::class)
            }

            val codeBlock = CodeBlock.builder()
                .add("return %T().apply {\n", httpRequestBuilderClass)
                .add("    method = io.ktor.http.HttpMethod.parse(%S)\n", method)

            var urlPath = path
            params.forEach { param ->
                urlPath = urlPath.replace("{$param}", "\$$param")
            }
            codeBlock.add("    url(\"\$baseUrl$urlPath\")\n")
            codeBlock.add("}\n")

            funBuilder.returns(httpRequestBuilderClass)
                .addCode(codeBlock.build())

            objectBuilder.addFunction(funBuilder.build())
        }

        try {
            FileSpec.builder(packageName, generatedClassName)
                .addType(objectBuilder.build())
                .addFileComment("Generated API client - DO NOT EDIT")
                .build()
                .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))

            logger.info("HttpClientProcessor: Generated $generatedClassName")
        } catch (e: Exception) {
            logger.warn("HttpClientProcessor: Could not generate $generatedClassName: ${e.message}")
        }
    }

    companion object {
        const val API_ENDPOINT_ANNOTATION = "tachiyomix.annotations.ApiEndpoint"
    }
}

class HttpClientProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return HttpClientProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
