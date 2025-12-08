package ireader.common.utils

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.Elements
import ireader.core.source.attrOrNull

// ═══════════════════════════════════════════════════════════════════════════
// KSOUP COMPATIBILITY HELPERS
// These provide utility methods for ksoup Elements/Element that mirror
// common Jsoup patterns but work with Kotlin Multiplatform
// ═══════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
// ELEMENT EXTENSION FUNCTIONS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get all sibling elements (both before and after) for this element.
 *
 * @return Elements containing all siblings (excluding this element)
 */
fun Element.siblings(): Elements {
    val result = Elements()
    val parent = this.parent() ?: return result
    for (child in parent.children()) {
        if (child != this) {
            result.add(child)
        }
    }
    return result
}

/**
 * Get all ancestor elements up to the root.
 *
 * @return Elements containing all ancestors from parent to root
 */
fun Element.ancestors(): Elements {
    val result = Elements()
    var current = this.parent()
    while (current != null) {
        result.add(current)
        current = current.parent()
    }
    return result
}

/**
 * Get the nth parent of this element.
 *
 * @param level How many levels up (1 = parent, 2 = grandparent, etc.)
 * @return The ancestor at the specified level, or null if not found
 */
fun Element.nthParent(level: Int): Element? {
    var current: Element? = this
    repeat(level) {
        current = current?.parent()
    }
    return current
}

/**
 * Get text content with whitespace normalized.
 *
 * @return Text with multiple whitespace collapsed to single spaces
 */
fun Element.normalizedText(): String {
    return this.text().replace(Regex("\\s+"), " ").trim()
}

/**
 * Get own text (excluding children's text).
 *
 * @return Only the direct text content of this element
 */
fun Element.ownTextTrimmed(): String {
    return this.ownText().trim()
}

/**
 * Check if element has a specific class.
 *
 * @param className The class name to check
 * @return true if element has the class
 */
fun Element.hasClassName(className: String): Boolean {
    return this.hasClass(className)
}

/**
 * Get attribute value or null if empty/missing.
 *
 * @param attributeKey The attribute name
 * @return Attribute value or null if empty
 */
fun Element.attrOrNull(attributeKey: String): String? {
    val value = this.attr(attributeKey)
    return value.ifBlank { null }
}

/**
 * Get absolute URL from an attribute.
 *
 * @param attributeKey The attribute containing the URL (e.g., "href", "src")
 * @return Absolute URL string
 */
fun Element.absUrl(attributeKey: String): String {
    return this.attr("abs:$attributeKey")
}

// ─────────────────────────────────────────────────────────────────────────────
// ELEMENTS EXTENSION FUNCTIONS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get the next sibling element for each element in the set.
 *
 * @return Elements containing the next sibling of each element (if exists)
 */
fun Elements.next(): Elements {
    val result = Elements()
    for (element in this) {
        element.nextElementSibling()?.let { result.add(it) }
    }
    return result
}

/**
 * Get the previous sibling element for each element in the set.
 *
 * @return Elements containing the previous sibling of each element (if exists)
 */
fun Elements.prev(): Elements {
    val result = Elements()
    for (element in this) {
        element.previousElementSibling()?.let { result.add(it) }
    }
    return result
}

/**
 * Get the parent element for each element in the set.
 *
 * @return Elements containing the parent of each element (if exists)
 */
fun Elements.parents(): Elements {
    val result = Elements()
    for (element in this) {
        element.parent()?.let { result.add(it) }
    }
    return result
}

/**
 * Get all sibling elements for each element in the set.
 *
 * @return Elements containing all siblings of all elements
 */
fun Elements.siblings(): Elements {
    val result = Elements()
    for (element in this) {
        result.addAll(element.siblings())
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// STANDALONE FUNCTIONS WITH ELEMENTS PARAMETER
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get the next sibling element for each element in the set.
 *
 * @param elements The elements to process
 * @return Elements containing the next sibling of each element (if exists)
 */
fun next(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        element.nextElementSibling()?.let { result.add(it) }
    }
    return result
}

/**
 * Get the previous sibling element for each element in the set.
 *
 * @param elements The elements to process
 * @return Elements containing the previous sibling of each element (if exists)
 */
fun prev(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        element.previousElementSibling()?.let { result.add(it) }
    }
    return result
}

/**
 * Get the parent element for each element in the set.
 *
 * @param elements The elements to process
 * @return Elements containing the parent of each element (if exists)
 */
fun parents(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        element.parent()?.let { result.add(it) }
    }
    return result
}

/**
 * Get all sibling elements for each element in the set.
 *
 * @param elements The elements to process
 * @return Elements containing all siblings of all elements
 */
fun siblings(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        result.addAll(element.siblings())
    }
    return result
}

/**
 * Get attribute values from all elements.
 *
 * @param elements The elements to process
 * @param attributeKey The attribute name
 * @return List of attribute values (empty strings filtered out)
 */
fun attrs(elements: Elements, attributeKey: String): List<String> {
    return elements.mapNotNull { it.attrOrNull(attributeKey) }
}

/**
 * Get text content from all elements as a list.
 *
 * @param elements The elements to process
 * @return List of text content from each element
 */
fun texts(elements: Elements): List<String> {
    return elements.map { it.text() }
}

/**
 * Get text content from all elements, filtered for non-blank.
 *
 * @param elements The elements to process
 * @return List of non-blank text content
 */
fun textsNotBlank(elements: Elements): List<String> {
    return elements.map { it.text().trim() }.filter { it.isNotBlank() }
}

/**
 * Get own text from all elements as a list.
 *
 * @param elements The elements to process
 * @return List of own text content from each element
 */
fun ownTexts(elements: Elements): List<String> {
    return elements.map { it.ownText() }
}

/**
 * Filter elements that have a specific attribute.
 *
 * @param elements The elements to process
 * @param attributeKey The attribute name to check
 * @return Elements that have the specified attribute
 */
fun filterByAttr(elements: Elements, attributeKey: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.hasAttr(attributeKey)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Filter elements that have a specific class.
 *
 * @param elements The elements to process
 * @param className The class name to check
 * @return Elements that have the specified class
 */
fun filterByClass(elements: Elements, className: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.hasClass(className)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements at even indices (0, 2, 4, ...).
 *
 * @param elements The elements to process
 * @return Elements at even positions
 */
fun even(elements: Elements): Elements {
    val result = Elements()
    elements.forEachIndexed { index, element ->
        if (index % 2 == 0) result.add(element)
    }
    return result
}

/**
 * Get elements at odd indices (1, 3, 5, ...).
 *
 * @param elements The elements to process
 * @return Elements at odd positions
 */
fun odd(elements: Elements): Elements {
    val result = Elements()
    elements.forEachIndexed { index, element ->
        if (index % 2 == 1) result.add(element)
    }
    return result
}

/**
 * Take first n elements.
 *
 * @param elements The elements to process
 * @param n Number of elements to take
 * @return First n elements
 */
fun take(elements: Elements, n: Int): Elements {
    val result = Elements()
    for (i in 0 until minOf(n, elements.size)) {
        result.add(elements[i])
    }
    return result
}

/**
 * Drop first n elements.
 *
 * @param elements The elements to process
 * @param n Number of elements to drop
 * @return Elements after dropping first n
 */
fun drop(elements: Elements, n: Int): Elements {
    val result = Elements()
    for (i in n until elements.size) {
        result.add(elements[i])
    }
    return result
}

/**
 * Take last n elements.
 *
 * @param elements The elements to process
 * @param n Number of elements to take from end
 * @return Last n elements
 */
fun takeLast(elements: Elements, n: Int): Elements {
    val result = Elements()
    val start = maxOf(0, elements.size - n)
    for (i in start until elements.size) {
        result.add(elements[i])
    }
    return result
}

/**
 * Drop last n elements.
 *
 * @param elements The elements to process
 * @param n Number of elements to drop from end
 * @return Elements after dropping last n
 */
fun dropLast(elements: Elements, n: Int): Elements {
    val result = Elements()
    val end = maxOf(0, elements.size - n)
    for (i in 0 until end) {
        result.add(elements[i])
    }
    return result
}

/**
 * Get attribute values from all elements.
 *
 * @param attributeKey The attribute name
 * @return List of attribute values (empty strings filtered out)
 */
fun Elements.attrs(attributeKey: String): List<String> {
    return this.mapNotNull { it.attrOrNull(attributeKey) }
}

/**
 * Get text content from all elements as a list.
 *
 * @return List of text content from each element
 */
fun Elements.texts(): List<String> {
    return this.map { it.text() }
}

/**
 * Get text content from all elements, filtered for non-blank.
 *
 * @return List of non-blank text content
 */
fun Elements.textsNotBlank(): List<String> {
    return this.map { it.text().trim() }.filter { it.isNotBlank() }
}

/**
 * Get own text from all elements as a list.
 *
 * @return List of own text content from each element
 */
fun Elements.ownTexts(): List<String> {
    return this.map { it.ownText() }
}

/**
 * Filter elements that have a specific attribute.
 *
 * @param attributeKey The attribute name to check
 * @return Elements that have the specified attribute
 */
fun Elements.hasAttr(attributeKey: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.hasAttr(attributeKey)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Filter elements that have a specific class.
 *
 * @param className The class name to check
 * @return Elements that have the specified class
 */
fun Elements.hasClass(className: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.hasClass(className)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements at even indices (0, 2, 4, ...).
 *
 * @return Elements at even positions
 */
fun Elements.even(): Elements {
    val result = Elements()
    this.forEachIndexed { index, element ->
        if (index % 2 == 0) result.add(element)
    }
    return result
}

/**
 * Get elements at odd indices (1, 3, 5, ...).
 *
 * @return Elements at odd positions
 */
fun Elements.odd(): Elements {
    val result = Elements()
    this.forEachIndexed { index, element ->
        if (index % 2 == 1) result.add(element)
    }
    return result
}

/**
 * Get element at specific index, or null if out of bounds.
 *
 * @param index The index
 * @return Element at index or null
 */
fun Elements.getOrNull(index: Int): Element? {
    return if (index in 0 until this.size) this[index] else null
}

/**
 * Take first n elements.
 *
 * @param n Number of elements to take
 * @return First n elements
 */
fun Elements.take(n: Int): Elements {
    val result = Elements()
    for (i in 0 until minOf(n, this.size)) {
        result.add(this[i])
    }
    return result
}

/**
 * Drop first n elements.
 *
 * @param n Number of elements to drop
 * @return Elements after dropping first n
 */
fun Elements.drop(n: Int): Elements {
    val result = Elements()
    for (i in n until this.size) {
        result.add(this[i])
    }
    return result
}

/**
 * Take last n elements.
 *
 * @param n Number of elements to take from end
 * @return Last n elements
 */
fun Elements.takeLast(n: Int): Elements {
    val result = Elements()
    val start = maxOf(0, this.size - n)
    for (i in start until this.size) {
        result.add(this[i])
    }
    return result
}

/**
 * Drop last n elements.
 *
 * @param n Number of elements to drop from end
 * @return Elements after dropping last n
 */
fun Elements.dropLast(n: Int): Elements {
    val result = Elements()
    val end = maxOf(0, this.size - n)
    for (i in 0 until end) {
        result.add(this[i])
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// STRING EXTENSION FUNCTIONS FOR HTML PARSING
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Clean HTML entities from string.
 *
 * @return String with common HTML entities decoded
 */
fun String.decodeHtmlEntities(): String {
    return this
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&#x27;", "'")
        .replace("&#x2F;", "/")
        .replace("&#x60;", "`")
        .replace("&#x3D;", "=")
}

/**
 * Remove HTML tags from string.
 *
 * @return Plain text without HTML tags
 */
fun String.stripHtmlTags(): String {
    return this.replace(Regex("<[^>]*>"), "")
}

/**
 * Normalize whitespace in string.
 *
 * @return String with multiple whitespace collapsed to single spaces
 */
fun String.normalizeWhitespace(): String {
    return this.replace(Regex("\\s+"), " ").trim()
}

/**
 * Extract URLs from string.
 *
 * @return List of URLs found in the string
 */
fun String.extractUrls(): List<String> {
    val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
    return urlPattern.findAll(this).map { it.value }.toList()
}
