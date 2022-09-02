package org.ireader.app

import ireader.Constants
import org.ireader.app.mock_components.FakeHttpClients
import org.ireader.app.mock_components.FakePreferencesStore
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource

val dependencies = Dependencies(
    FakeHttpClients(),
    FakePreferencesStore()
)
val extension: HttpSource = Constants.getExtension(deps = dependencies)
