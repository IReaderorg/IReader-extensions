package org.ireader.app.tests

import com.google.common.truth.Truth
import org.ireader.app.extension
import org.ireader.core_api.source.model.Listing
import org.ireader.core_api.source.model.MangasPageInfo
import org.junit.Before
import org.junit.Test

class BookListChecker {
    lateinit var books: MangasPageInfo
    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {

            books = extension.getMangaList(LatestListing(), 1)
            print(books)
        }
    }

    @Test
    fun `check  whether books list is empty `() {
        Truth.assertThat(books.mangas.isNotEmpty()).isTrue()
    }
    @Test
    fun `check  whether book has title `() {
        Truth.assertThat(books.mangas.any { book -> book.title.isNotBlank() }).isTrue()
    }
    @Test
    fun `check  whether book has key `() {
        Truth.assertThat(books.mangas.any { book -> book.key.isNotBlank() }).isTrue()
    }
    @Test
    fun `check  whether book has cover `() {
        Truth.assertThat(books.mangas.any { book -> book.cover.isNotBlank() }).isTrue()
    }
}

class LatestListing() : Listing(name = "Latest")
