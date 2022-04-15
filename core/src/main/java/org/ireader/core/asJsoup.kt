package org.ireader.core

import io.ktor.client.statement.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

suspend fun HttpResponse.asJsoup(html: String? = null): Document {

    return Jsoup.parse(html ?: this.bodyAsText(), request.url.toString())
}
suspend fun String.asJsoup(html: String? = null): Document {

    return Jsoup.parse(html ?: this)
}