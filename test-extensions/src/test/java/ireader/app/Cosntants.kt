package ireader.app

import ireader.constants.Constants
import ireader.app.mockcomponents.FakeHttpClients
import ireader.app.mockcomponents.FakePreferencesStore
import ireader.core.source.Dependencies
import ireader.core.source.HttpSource

val dependencies = Dependencies(
    FakeHttpClients(),
    FakePreferencesStore()
)
val extension: HttpSource = Constants.getExtension(deps = dependencies)
