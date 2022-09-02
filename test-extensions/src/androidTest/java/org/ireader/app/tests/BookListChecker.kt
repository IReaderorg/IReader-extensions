package org.ireader.app.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import org.ireader.app.extension
import org.ireader.core_api.source.model.Listing
import org.ireader.core_api.source.model.MangasPageInfo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookListChecker {
    lateinit var books: MangasPageInfo
    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {
            books =  extension.getMangaList(LatestListing(), 1)
            print(books)
        }
    }

    @Test
    fun checkBookListIsNotEmpty() {
        Truth.assertThat(books.mangas.isNotEmpty()).isTrue()
    }
    @Test
    fun checkBookHasTitle() {
        Truth.assertThat(books.mangas.any { book -> book.title.isNotBlank() }).isTrue()
    }
    @Test
    fun checkBookHasKey() {
        Truth.assertThat(books.mangas.any { book -> book.key.isNotBlank() }).isTrue()
    }
    @Test
    fun checkBookHasCover() {
        Truth.assertThat(books.mangas.any { book -> book.cover.isNotBlank() }).isTrue()
    }
}

class LatestListing() : Listing(name = "Latest")