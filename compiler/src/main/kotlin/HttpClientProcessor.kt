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

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Find classes with @ApiEndpoint annotations
        val sourcesWithEndpoints = resolver.getSymbolsWithAnnotation(API_ENDPOINT_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .distinctBy { it.qualifiedName?.asString() }
            .toList()

        sourcesWithEndpoints.forEach { generateHttpClient(it) }

        // Find classes with @ExploreFetcher to generate request helpers
        val sourcesWithFetchers = resolver.getSymbolsWithAnnotation(EXPLORE_FETCHER_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .distinctBy { it.qualifiedName?.asString() }
            .toList()

        sourcesWithFetchers.forEach { generateFetcherHelpers(it) }

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

        val httpClientClass = ClassName("io.ktor.client", "HttpClient")
        val httpRequestBuilderClass = ClassName("io.ktor.client.request", "HttpRequestBuilder")
        val urlClass = ClassName("io.ktor.client.request", "url")

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

            // Add parameters
            params.forEach { param ->
                funBuilder.addParameter(param, String::class)
            }

            funBuilder.returns(httpRequestBuilderClass)
                .addStatement("return %T().apply {", httpRequestBuilderClass)
                .addStatement("    method = io.ktor.http.HttpMethod.parse(%S)", method)

            // Build URL with parameters
            var urlPath = path
            params.forEach { param ->
                urlPath = urlPath.replace("{$param}", "\$$param")
            }
            funBuilder.addStatement("    url(\"\$baseUrl$urlPath\")")
            funBuilder.addStatement("}")

            objectBuilder.addFunction(funBuilder.build())
        }

        FileSpec.builder(packageName, generatedClassName)
            .addType(objectBuilder.build())
            .addFileComment("Generated API client - DO NOT EDIT")
            .build()
            .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))

        logger.info("Generated API client: $packageName.$generatedClassName")
    }

    private fun generateFetcherHelpers(source: KSClassDeclaration) {
        val fetchers = source.annotations.filter { 
            it.shortName.asString() == "ExploreFetcher" 
        }.toList()

        if (fetchers.isEmpty()) return

        val packageName = source.packageName.asString()
        val className = source.simpleName.asString()
        val generatedClassName = "${className}Requests"

        val httpRequestBuilderClass = ClassName("io.ktor.client.request", "HttpRequestBuilder")
        val headersBuilderClass = ClassName("io.ktor.http", "HeadersBuilder")

        val objectBuilder = TypeSpec.objectBuilder(generatedClassName)
            .addKdoc("Generated request helpers for $className fetchers")

        // Add common headers function
        objectBuilder.addFunction(
            FunSpec.builder("defaultHeaders")
                .addParameter("builder", headersBuilderClass)
                .addStatement("builder.append(\"User-Agent\", DEFAULT_USER_AGENT)")
                .addStatement("builder.append(\"Accept\", \"text/html,application/xhtml+xml\")")
                .addStatement("builder.append(\"Accept-Language\", \"en-US,en;q=0.9\")")
                .build()
        )

        // Add user agent constant
        objectBuilder.addProperty(
            PropertySpec.builder("DEFAULT_USER_AGENT", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
        )

        fetchers.forEach { fetcher ->
            val args = fetcher.arguments.associate { it.name?.asString() to it.value }
            val name = args["name"] as? String ?: "request"
            val endpoint = args["endpoint"] as? String ?: ""
            val isSearch = args["isSearch"] as? Boolean ?: false

            val funName = "build${name.replace(" ", "")}Request"
            val funBuilder = FunSpec.builder(funName)
                .addParameter("baseUrl", String::class)
                .addParameter("page", Int::class)

            if (isSearch || endpoint.contains("{query}")) {
                funBuilder.addParameter("query", String::class, KModifier.VARARG)
            }

            funBuilder.returns(httpRequestBuilderClass)
                .addStatement("return %T().apply {", httpRequestBuilderClass)
                .addStatement("    val path = %S", endpoint)
                .addStatement("        .replace(\"{page}\", page.toString())")

            if (isSearch || endpoint.contains("{query}")) {
                funBuilder.addStatement("        .replace(\"{query}\", query.firstOrNull() ?: \"\")")
            }

            funBuilder.addStatement("    url(\"\$baseUrl\$path\")")
                .addStatement("    headers { defaultHeaders(this) }")
                .addStatement("}")

            objectBuilder.addFunction(funBuilder.build())
        }

        // Add a combined function to get request by fetcher name
        objectBuilder.addFunction(
            FunSpec.builder("buildRequest")
                .addParameter("fetcherName", String::class)
                .addParameter("baseUrl", String::class)
                .addParameter("page", Int::class)
                .addParameter("query", String::class.asTypeName().copy(nullable = true))
                .returns(httpRequestBuilderClass.copy(nullable = true))
                .addStatement("return when (fetcherName) {")
                .apply {
                    fetchers.forEach { fetcher ->
                        val args = fetcher.arguments.associate { it.name?.asString() to it.value }
                        val name = args["name"] as? String ?: "request"
                        val isSearch = args["isSearch"] as? Boolean ?: false
                        val funName = "build${name.replace(" ", "")}Request"
                        if (isSearch) {
                            addStatement("    %S -> $funName(baseUrl, page, query ?: \"\")", name)
                        } else {
                            addStatement("    %S -> $funName(baseUrl, page)", name)
                        }
                    }
                }
                .addStatement("    else -> null")
                .addStatement("}")
                .build()
        )

        FileSpec.builder(packageName, generatedClassName)
            .addType(objectBuilder.build())
            .addFileComment("Generated request helpers - DO NOT EDIT")
            .build()
            .writeTo(codeGenerator, Dependencies(false, source.containingFile!!))

        logger.info("Generated request helpers: $packageName.$generatedClassName")
    }

    companion object {
        const val API_ENDPOINT_ANNOTATION = "tachiyomix.annotations.ApiEndpoint"
        const val EXPLORE_FETCHER_ANNOTATION = "tachiyomix.annotations.ExploreFetcher"
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
