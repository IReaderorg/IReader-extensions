package tachiyomix.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate

/**
 * KSP Processor that validates CSS selectors at compile time.
 * 
 * This catches common selector errors early:
 * - Unbalanced brackets
 * - Invalid pseudo-selectors
 * - Empty selectors
 * - Common typos
 */
class SelectorValidatorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    // Common valid pseudo-classes and pseudo-elements
    private val validPseudoClasses = setOf(
        "first-child", "last-child", "nth-child", "nth-last-child",
        "first-of-type", "last-of-type", "nth-of-type", "nth-last-of-type",
        "only-child", "only-of-type", "empty", "not", "has",
        "hover", "active", "focus", "visited", "link",
        "checked", "disabled", "enabled", "required", "optional",
        "valid", "invalid", "in-range", "out-of-range",
        "root", "target", "lang", "contains"
    )

    private val validPseudoElements = setOf(
        "before", "after", "first-line", "first-letter",
        "selection", "placeholder", "marker"
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Validate selectors in @DetailSelectors
        resolver.getSymbolsWithAnnotation(DETAIL_SELECTORS_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .forEach { validateDetailSelectors(it) }

        // Validate selectors in @ChapterSelectors
        resolver.getSymbolsWithAnnotation(CHAPTER_SELECTORS_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .forEach { validateChapterSelectors(it) }

        // Validate selectors in @ContentSelectors
        resolver.getSymbolsWithAnnotation(CONTENT_SELECTORS_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .forEach { validateContentSelectors(it) }

        // Validate selectors in @ExploreFetcher
        resolver.getSymbolsWithAnnotation(EXPLORE_FETCHER_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .forEach { validateExploreFetchers(it) }

        return emptyList()
    }

    private fun validateDetailSelectors(source: KSClassDeclaration) {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "DetailSelectors" 
        } ?: return

        val args = annotation.arguments.associate { it.name?.asString() to it.value }
        val className = source.simpleName.asString()

        listOf("title", "cover", "author", "description", "genres", "status").forEach { field ->
            val selector = args[field] as? String ?: ""
            if (selector.isNotEmpty()) {
                validateSelector(selector, "$className.DetailSelectors.$field")
            }
        }
    }

    private fun validateChapterSelectors(source: KSClassDeclaration) {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "ChapterSelectors" 
        } ?: return

        val args = annotation.arguments.associate { it.name?.asString() to it.value }
        val className = source.simpleName.asString()

        listOf("list", "name", "link", "date").forEach { field ->
            val selector = args[field] as? String ?: ""
            if (selector.isNotEmpty()) {
                validateSelector(selector, "$className.ChapterSelectors.$field")
            }
        }
    }

    private fun validateContentSelectors(source: KSClassDeclaration) {
        val annotation = source.annotations.find { 
            it.shortName.asString() == "ContentSelectors" 
        } ?: return

        val args = annotation.arguments.associate { it.name?.asString() to it.value }
        val className = source.simpleName.asString()

        val content = args["content"] as? String ?: ""
        if (content.isNotEmpty()) {
            validateSelector(content, "$className.ContentSelectors.content")
        }

        val title = args["title"] as? String ?: ""
        if (title.isNotEmpty()) {
            validateSelector(title, "$className.ContentSelectors.title")
        }

        val removeSelectors = (args["removeSelectors"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        removeSelectors.forEachIndexed { index, selector ->
            validateSelector(selector, "$className.ContentSelectors.removeSelectors[$index]")
        }
    }

    private fun validateExploreFetchers(source: KSClassDeclaration) {
        val annotations = source.annotations.filter { 
            it.shortName.asString() == "ExploreFetcher" 
        }

        val className = source.simpleName.asString()

        annotations.forEach { annotation ->
            val args = annotation.arguments.associate { it.name?.asString() to it.value }
            val name = args["name"] as? String ?: "unknown"

            listOf("selector", "nameSelector", "linkSelector", "coverSelector").forEach { field ->
                val selector = args[field] as? String ?: ""
                if (selector.isNotEmpty()) {
                    validateSelector(selector, "$className.ExploreFetcher($name).$field")
                }
            }
        }
    }

    private fun validateSelector(selector: String, location: String) {
        val errors = mutableListOf<String>()

        // Check for empty selector
        if (selector.isBlank()) {
            errors.add("Empty selector")
        }

        // Check for unbalanced brackets
        val brackets = mapOf('(' to ')', '[' to ']', '{' to '}')
        val stack = mutableListOf<Char>()
        for (char in selector) {
            when (char) {
                '(', '[', '{' -> stack.add(char)
                ')', ']', '}' -> {
                    if (stack.isEmpty() || brackets[stack.removeLast()] != char) {
                        errors.add("Unbalanced brackets")
                        break
                    }
                }
            }
        }
        if (stack.isNotEmpty()) {
            errors.add("Unclosed brackets: ${stack.joinToString("")}")
        }

        // Check for invalid pseudo-selectors
        val pseudoPattern = Regex(":([a-z-]+)")
        pseudoPattern.findAll(selector).forEach { match ->
            val pseudo = match.groupValues[1]
            if (pseudo !in validPseudoClasses && pseudo !in validPseudoElements) {
                // Check if it's nth-child with argument
                if (!pseudo.startsWith("nth-") && !pseudo.startsWith("not") && !pseudo.startsWith("has")) {
                    errors.add("Unknown pseudo-selector ':$pseudo'")
                }
            }
        }

        // Check for common typos
        val commonTypos = mapOf(
            "clas" to "class",
            "calss" to "class",
            "frist" to "first",
            "lsat" to "last",
            "chidl" to "child",
            "slector" to "selector"
        )
        commonTypos.forEach { (typo, correct) ->
            if (selector.contains(typo, ignoreCase = true)) {
                errors.add("Possible typo: '$typo' should be '$correct'")
            }
        }

        // Check for double dots (common mistake)
        if (selector.contains("..")) {
            errors.add("Double dots '..' found - possible typo")
        }

        // Check for spaces in class/id names (invalid)
        if (Regex("\\.[a-z]+\\s+[a-z]+", RegexOption.IGNORE_CASE).containsMatchIn(selector)) {
            // This is actually valid (descendant selector), so skip
        }

        // Report errors
        errors.forEach { error ->
            logger.warn("Selector validation [$location]: $error in '$selector'")
        }

        if (errors.isEmpty()) {
            logger.info("Selector validated: $location")
        }
    }

    companion object {
        const val DETAIL_SELECTORS_ANNOTATION = "tachiyomix.annotations.DetailSelectors"
        const val CHAPTER_SELECTORS_ANNOTATION = "tachiyomix.annotations.ChapterSelectors"
        const val CONTENT_SELECTORS_ANNOTATION = "tachiyomix.annotations.ContentSelectors"
        const val EXPLORE_FETCHER_ANNOTATION = "tachiyomix.annotations.ExploreFetcher"
    }
}

class SelectorValidatorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SelectorValidatorProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
