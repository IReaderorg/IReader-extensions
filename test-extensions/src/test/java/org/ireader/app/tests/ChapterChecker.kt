package org.ireader.app.tests

import com.google.common.truth.Truth.assertThat
import org.ireader.app.BOOK_NAME
import org.ireader.app.BOOK_URL
import org.ireader.app.extension
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.MangaInfo
import org.junit.Before
import org.junit.Test

class ChapterChecker {
    var chepters: List<ChapterInfo> = emptyList()
    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {
            chepters = extension.getChapterList(MangaInfo(key = BOOK_URL, title = BOOK_NAME), emptyList())
            print(chepters)
        }
    }

    @Test
    fun `check  whether chapter list is empty `() {
        assertThat(chepters.isNotEmpty()).isTrue()
    }
    @Test
    fun `check  whether chapters has name `() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.name.isNotBlank() }).isTrue()
    }
    @Test
    fun `check  whether chapters has keys `() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.key.isNotBlank() }).isTrue()
    }
    @Test
    fun `check  whether chapters has dateUpload `() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.dateUpload != 0L }).isTrue()
    }
    @Test
    fun `check  whether chapters has number `() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.number != -1f }).isTrue()
    }
    @Test
    fun `check  whether chapters has translator `() {
        assertThat(chepters.any { chapterInfo -> chapterInfo.scanlator.isNotBlank() }).isTrue()
    }
}
