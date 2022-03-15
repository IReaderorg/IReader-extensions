package org.ireader.core

import io.ktor.client.response.*
import io.ktor.client.statement.*
import io.ktor.client.statement.HttpResponse
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

suspend fun HttpResponse.asJsoup(html: String? = null): Document {

    return Jsoup.parse(html ?: this.readText(), request.url.toString())
}