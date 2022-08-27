package org.ireader.app



import ireader.kolnovel.KolNovel
import org.ireader.app.mock_components.FakeHttpClients
import org.ireader.app.mock_components.FakePreferencesStore
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource


const val BOOK_URL = "https://novel4up.com/novel/soul-land-5/"
const val BOOK_NAME = "Soul Land 5"
const val CHAPTER_NAME = "207 - تصوير فاكهة الشمس الأرجواني"
const val CHAPTER_URL = "https://novel4up.com/novel/soul-land-5/207/"
val dependencies = Dependencies(
    FakeHttpClients(),
    FakePreferencesStore()
)
// change KissNovel to the name your source
val extension : HttpSource = object : Novel4Up(dependencies) {

}
