package ireader.app.tests

import com.google.common.truth.Truth.assertThat
import ireader.app.CHAPTER_NAME
import ireader.app.CHAPTER_URL
import ireader.app.extension
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Page
import org.junit.Before
import org.junit.Test

class ContentChecker {
    var page: List<Page> = emptyList()
    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {
            page = extension.getPageList(ChapterInfo(key = CHAPTER_URL, name = CHAPTER_NAME), emptyList())
            print(page)
        }
    }

    @Test
    fun `check  whether page list is empty `() {
        assertThat(page.isNotEmpty()).isTrue()
    }
}
