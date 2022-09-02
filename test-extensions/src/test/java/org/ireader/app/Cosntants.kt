package org.ireader.app

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import ireader.armtl.ArMtl
import org.ireader.app.mock_components.FakeHttpClients
import org.ireader.app.mock_components.FakePreferencesStore
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource

val dependencies = Dependencies(
    FakeHttpClients(),
    FakePreferencesStore()
)
val extension: HttpSource = object : ArMtl(
    dependencies,
) {
    override val client: HttpClient
        get() = HttpClient(OkHttp)
}