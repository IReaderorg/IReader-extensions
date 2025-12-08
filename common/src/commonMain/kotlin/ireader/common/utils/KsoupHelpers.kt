package ireader.common.utils

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.Elements
import ireader.core.source.attrOrNull


// ─────────────────────────────────────────────────────────────────────────────
// FUNCTIONS WITH ELEMENTS AS PARAMETER (Elements : Nodes<Element>)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get all sibling elements for each element in the collection.
 *
 * @param elements The elements collection
 * @return Elements containing all siblings
 */
fun siblings(elements: Elements): Elements {
    return elements.siblings()
}

/**
 * Get all ancestor elements for each element in the collection.
 *
 * @param elements The elements collection
 * @return Elements containing all ancestors (deduplicated)
 */
fun ancestors(elements: Elements): Elements {
    return elements.ancestors()
}

/**
 * Get the nth parent for each element in the collection.
 *
 * @param elements The elements collection
 * @param level How many levels up (1 = parent, 2 = grandparent, etc.)
 * @return Elements containing the ancestor at the specified level
 */
fun nthParent(elements: Elements, level: Int): Elements {
    return elements.nthParent(level)
}

/**
 * Get normalized text content from all elements.
 *
 * @param elements The elements collection
 * @return List of text content with whitespace normalized
 */
fun normalizedTexts(elements: Elements): List<String> {
    return elements.normalizedTexts()
}

/**
 * Get own text (trimmed) from all elements.
 *
 * @param elements The elements collection
 * @return List of own text content (trimmed)
 */
fun ownTextsTrimmed(elements: Elements): List<String> {
    return elements.ownTextsTrimmed()
}

/**
 * Get absolute URLs from an attribute for all elements.
 *
 * @param elements The elements collection
 * @param attributeKey The attribute containing the URL
 * @return List of absolute URL strings
 */
fun absUrls(elements: Elements, attributeKey: String): List<String> {
    return elements.absUrls(attributeKey)
}

/**
 * Get the next sibling element for each element.
 *
 * @param elements The elements collection
 * @return Elements containing the next sibling of each element
 */
fun next(elements: Elements): Elements {
    return elements.next()
}

/**
 * Get the previous sibling element for each element.
 *
 * @param elements The elements collection
 * @return Elements containing the previous sibling of each element
 */
fun prev(elements: Elements): Elements {
    return elements.prev()
}

/**
 * Get the parent element for each element.
 *
 * @param elements The elements collection
 * @return Elements containing the parent of each element
 */
fun parents(elements: Elements): Elements {
    return elements.parents()
}

/**
 * Get attribute values from all elements.
 *
 * @param elements The elements collection
 * @param attributeKey The attribute name
 * @return List of attribute values
 */
fun attrs(elements: Elements, attributeKey: String): List<String> {
    return elements.attrs(attributeKey)
}

/**
 * Get text content from all elements.
 *
 * @param elements The elements collection
 * @return List of text content
 */
fun texts(elements: Elements): List<String> {
    return elements.texts()
}

/**
 * Get text content from all elements, filtered for non-blank.
 *
 * @param elements The elements collection
 * @return List of non-blank text content
 */
fun textsNotBlank(elements: Elements): List<String> {
    return elements.textsNotBlank()
}

/**
 * Get own text from all elements.
 *
 * @param elements The elements collection
 * @return List of own text content
 */
fun ownTexts(elements: Elements): List<String> {
    return elements.ownTexts()
}

/**
 * Filter elements that have a specific attribute.
 *
 * @param elements The elements collection
 * @param attributeKey The attribute name to check
 * @return Elements that have the specified attribute
 */
fun filterByAttr(elements: Elements, attributeKey: String): Elements {
    return elements.filterByAttr(attributeKey)
}

/**
 * Filter elements that have a specific class.
 *
 * @param elements The elements collection
 * @param className The class name to check
 * @return Elements that have the specified class
 */
fun filterByClass(elements: Elements, className: String): Elements {
    return elements.filterByClass(className)
}

/**
 * Get elements at even indices.
 *
 * @param elements The elements collection
 * @return Elements at even positions
 */
fun even(elements: Elements): Elements {
    return elements.even()
}

/**
 * Get elements at odd indices.
 *
 * @param elements The elements collection
 * @return Elements at odd positions
 */
fun odd(elements: Elements): Elements {
    return elements.odd()
}

/**
 * Get element at specific index, or null if out of bounds.
 *
 * @param elements The elements collection
 * @param index The index
 * @return Element at index or null
 */
fun getOrNull(elements: Elements, index: Int): Element? {
    return elements.getOrNull(index)
}

/**
 * Take first n elements.
 *
 * @param elements The elements collection
 * @param n Number of elements to take
 * @return First n elements
 */
fun take(elements: Elements, n: Int): Elements {
    return elements.take(n)
}

/**
 * Drop first n elements.
 *
 * @param elements The elements collection
 * @param n Number of elements to drop
 * @return Elements after dropping first n
 */
fun drop(elements: Elements, n: Int): Elements {
    return elements.drop(n)
}

/**
 * Take last n elements.
 *
 * @param elements The elements collection
 * @param n Number of elements to take from end
 * @return Last n elements
 */
fun takeLast(elements: Elements, n: Int): Elements {
    return elements.takeLast(n)
}

/**
 * Drop last n elements.
 *
 * @param elements The elements collection
 * @param n Number of elements to drop from end
 * @return Elements after dropping last n
 */
fun dropLast(elements: Elements, n: Int): Elements {
    return elements.dropLast(n)
}

/**
 * Check if any element has a specific class.
 *
 * @param elements The elements collection
 * @param className The class name to check
 * @return true if any element has the class
 */
fun hasClassName(elements: Elements, className: String): Boolean {
    return elements.hasClassName(className)
}

/**
 * Get attribute value or null from the first element.
 *
 * @param elements The elements collection
 * @param attributeKey The attribute name
 * @return Attribute value or null
 */
fun attrOrNull(elements: Elements, attributeKey: String): String? {
    return elements.attrOrNull(attributeKey)
}

/**
 * Get the element at the specified index (jQuery-style eq).
 *
 * @param elements The elements collection
 * @param index The index (supports negative indices)
 * @return Elements containing only the element at that index
 */
fun eq(elements: Elements, index: Int): Elements {
    return elements.eq(index)
}

/**
 * Get the first element wrapped in Elements.
 *
 * @param elements The elements collection
 * @return Elements containing only the first element
 */
fun firstAsElements(elements: Elements): Elements {
    return elements.firstAsElements()
}

/**
 * Get the last element wrapped in Elements.
 *
 * @param elements The elements collection
 * @return Elements containing only the last element
 */
fun lastAsElements(elements: Elements): Elements {
    return elements.lastAsElements()
}

/**
 * Filter elements that do NOT match the selector.
 *
 * @param elements The elements collection
 * @param selector CSS selector to exclude
 * @return Elements that don't match the selector
 */
fun not(elements: Elements, selector: String): Elements {
    return elements.not(selector)
}

/**
 * Check if any element matches the selector.
 *
 * @param elements The elements collection
 * @param selector CSS selector to check
 * @return true if any element matches
 */
fun isMatch(elements: Elements, selector: String): Boolean {
    return elements.`is`(selector)
}

/**
 * Check if any element has descendants matching the selector.
 *
 * @param elements The elements collection
 * @param selector CSS selector to check
 * @return true if any element has matching descendants
 */
fun has(elements: Elements, selector: String): Boolean {
    return elements.has(selector)
}

/**
 * Filter elements that have descendants matching the selector.
 *
 * @param elements The elements collection
 * @param selector CSS selector to check
 * @return Elements that have matching descendants
 */
fun filterHas(elements: Elements, selector: String): Elements {
    return elements.filterHas(selector)
}

/**
 * Get the closest ancestor matching the selector for each element.
 *
 * @param elements The elements collection
 * @param selector CSS selector to match
 * @return Elements containing the closest matching ancestor
 */
fun closest(elements: Elements, selector: String): Elements {
    return elements.closest(selector)
}

/**
 * Get all children of all elements.
 *
 * @param elements The elements collection
 * @return Elements containing all children
 */
fun children(elements: Elements): Elements {
    return elements.children()
}

/**
 * Get all descendants matching the selector.
 *
 * @param elements The elements collection
 * @param selector CSS selector
 * @return Elements containing all matching descendants
 */
fun find(elements: Elements, selector: String): Elements {
    return elements.find(selector)
}

/**
 * Add a class to all elements.
 *
 * @param elements The elements collection
 * @param className The class name to add
 * @return The modified Elements
 */
fun addClass(elements: Elements, className: String): Elements {
    return elements.addClass(className)
}

/**
 * Remove a class from all elements.
 *
 * @param elements The elements collection
 * @param className The class name to remove
 * @return The modified Elements
 */
fun removeClass(elements: Elements, className: String): Elements {
    return elements.removeClass(className)
}

/**
 * Toggle a class on all elements.
 *
 * @param elements The elements collection
 * @param className The class name to toggle
 * @return The modified Elements
 */
fun toggleClass(elements: Elements, className: String): Elements {
    return elements.toggleClass(className)
}

/**
 * Set an attribute on all elements.
 *
 * @param elements The elements collection
 * @param attributeKey The attribute name
 * @param attributeValue The attribute value
 * @return The modified Elements
 */
fun attr(elements: Elements, attributeKey: String, attributeValue: String): Elements {
    return elements.attr(attributeKey, attributeValue)
}

/**
 * Remove an attribute from all elements.
 *
 * @param elements The elements collection
 * @param attributeKey The attribute name to remove
 * @return The modified Elements
 */
fun removeAttr(elements: Elements, attributeKey: String): Elements {
    return elements.removeAttr(attributeKey)
}

/**
 * Get the combined outer HTML of all elements.
 *
 * @param elements The elements collection
 * @return Combined outer HTML string
 */
fun outerHtml(elements: Elements): String {
    return elements.outerHtml()
}

/**
 * Get the combined inner HTML of all elements.
 *
 * @param elements The elements collection
 * @return Combined inner HTML string
 */
fun html(elements: Elements): String {
    return elements.html()
}

/**
 * Wrap each element with the specified HTML.
 *
 * @param elements The elements collection
 * @param html The wrapping HTML
 * @return The modified Elements
 */
fun wrap(elements: Elements, html: String): Elements {
    return elements.wrap(html)
}

/**
 * Unwrap each element (replace with its children).
 *
 * @param elements The elements collection
 * @return The modified Elements
 */
fun unwrap(elements: Elements): Elements {
    return elements.unwrap()
}

/**
 * Remove all elements from the DOM.
 *
 * @param elements The elements collection
 * @return The modified Elements
 */
fun removeElements(elements: Elements): Elements {
    return elements.remove()
}

/**
 * Empty all elements (remove all children).
 *
 * @param elements The elements collection
 * @return The modified Elements
 */
fun empty(elements: Elements): Elements {
    return elements.empty()
}

/**
 * Get the tag name of the first element.
 *
 * @param elements The elements collection
 * @return Tag name or null if no elements
 */
fun tagName(elements: Elements): String? {
    return elements.tagName()
}

/**
 * Get all unique tag names in the set.
 *
 * @param elements The elements collection
 * @return Set of tag names
 */
fun tagNames(elements: Elements): Set<String> {
    return elements.tagNames()
}

/**
 * Get the value attribute of the first element.
 *
 * @param elements The elements collection
 * @return Value or empty string
 */
fun value(elements: Elements): String {
    return elements.`val`()
}

/**
 * Set the value attribute on all elements.
 *
 * @param elements The elements collection
 * @param value The value to set
 * @return The modified Elements
 */
fun value(elements: Elements, value: String): Elements {
    return elements.`val`(value)
}

/**
 * Get data attribute value from the first element.
 *
 * @param elements The elements collection
 * @param key The data key (without "data-" prefix)
 * @return Data value or null
 */
fun data(elements: Elements, key: String): String? {
    return elements.data(key)
}

/**
 * Get all data attributes from the first element.
 *
 * @param elements The elements collection
 * @return Map of data attribute keys to values
 */
fun dataAttributes(elements: Elements): Map<String, String> {
    return elements.dataAttributes()
}

/**
 * Filter elements by tag name.
 *
 * @param elements The elements collection
 * @param tagName The tag name to filter by
 * @return Elements with the specified tag name
 */
fun filterByTag(elements: Elements, tagName: String): Elements {
    return elements.filterByTag(tagName)
}

/**
 * Get elements that contain the specified text.
 *
 * @param elements The elements collection
 * @param text The text to search for (case-insensitive)
 * @return Elements containing the text
 */
fun filterByText(elements: Elements, text: String): Elements {
    return elements.filterByText(text)
}

/**
 * Get elements that have the specified attribute value.
 *
 * @param elements The elements collection
 * @param attributeKey The attribute name
 * @param attributeValue The attribute value to match
 * @return Elements with matching attribute value
 */
fun filterByAttrValue(elements: Elements, attributeKey: String, attributeValue: String): Elements {
    return elements.filterByAttrValue(attributeKey, attributeValue)
}

/**
 * Get elements that have attribute value containing the specified text.
 *
 * @param elements The elements collection
 * @param attributeKey The attribute name
 * @param text The text to search for in attribute value
 * @return Elements with attribute value containing the text
 */
fun filterByAttrContaining(elements: Elements, attributeKey: String, text: String): Elements {
    return elements.filterByAttrContaining(attributeKey, text)
}

/**
 * Reverse the order of elements.
 *
 * @param elements The elements collection
 * @return Elements in reversed order
 */
fun reversed(elements: Elements): Elements {
    return elements.reversed()
}

/**
 * Get distinct elements (remove duplicates).
 *
 * @param elements The elements collection
 * @return Elements with duplicates removed
 */
fun distinct(elements: Elements): Elements {
    return elements.distinct()
}

// ─────────────────────────────────────────────────────────────────────────────
// FUNCTIONS WITH ELEMENTS
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
