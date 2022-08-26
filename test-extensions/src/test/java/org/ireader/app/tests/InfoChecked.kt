package org.ireader.app.tests

import com.google.common.truth.Truth.assertThat
import ireader.kissnovellove.KissNovel
import org.ireader.app.BOOK_NAME
import org.ireader.app.BOOK_URL
import org.ireader.app.extension
import org.ireader.app.mock_components.FakeHttpClients
import org.ireader.app.mock_components.FakePreferencesStore
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.model.MangaInfo
import org.junit.Before
import org.junit.Test

class InfoChecked {

    var book: MangaInfo = MangaInfo(key = "", title = "")

    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {
            book = extension.getMangaDetails(MangaInfo(key = BOOK_URL, title = BOOK_NAME), emptyList())
            print(book)
        }
    }

    @Test
    fun `check book name`() {
        assertThat(book.title.isNotBlank()).isTrue()
    }
    @Test
    fun `check book author`() {
        assertThat(book.author.isNotBlank()).isTrue()
    }

    @Test
    fun `check book cover`() {
        assertThat(book.cover.isNotBlank()).isTrue()
    }
    @Test
    fun `check book description`() {
        assertThat(book.description.isNotBlank()).isTrue()
    }
    @Test
    fun `check book genres`() {
        assertThat(book.genres.isNotEmpty()).isTrue()
    }
    @Test
    fun `check book status`() {
        assertThat(book.status != MangaInfo.UNKNOWN).isTrue()
    }

}