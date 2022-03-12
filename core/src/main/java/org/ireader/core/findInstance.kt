package org.ireader.core

inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T