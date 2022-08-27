package org.ireader.app



import ireader.kolnovel.KolNovel
import org.ireader.app.mock_components.FakeHttpClients
import org.ireader.app.mock_components.FakePreferencesStore
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource


const val BOOK_URL = "https://kolnovel.com/series/overgeared/"
const val BOOK_NAME = "مدجج بالعتاد"
const val CHAPTER_NAME = "chapter"
const val CHAPTER_URL = "https://kolnovel.com/overgeared-184879/"
val dependencies = Dependencies(
    FakeHttpClients(),
    FakePreferencesStore()
)
// change KissNovel to the name your source
val extension : HttpSource = object : KolNovel(dependencies) {

}

