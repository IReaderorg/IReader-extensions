package ireader.app.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import ireader.app.BOOK_NAME
import ireader.app.BOOK_URL
import ireader.app.extension
import ireader.core.source.model.MangaInfo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    fun checkBookName() {
        assertThat(book.title.isNotBlank()).isTrue()
    }
    @Test
    fun checkBookAuthor() {
        assertThat(book.author.isNotBlank()).isTrue()
    }

    @Test
    fun checkBookCover() {
        assertThat(book.cover.isNotBlank()).isTrue()
    }
    @Test
    fun checkBookDescription() {
        assertThat(book.description.isNotBlank()).isTrue()
    }
    @Test
    fun checkBookGenres() {
        assertThat(book.genres.isNotEmpty()).isTrue()
    }
    @Test
    fun checkBookStatus() {
        assertThat(book.status != MangaInfo.UNKNOWN).isTrue()
    }
}
