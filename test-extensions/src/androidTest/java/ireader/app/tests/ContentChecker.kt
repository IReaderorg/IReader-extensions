package ireader.app.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import ireader.app.CHAPTER_NAME
import ireader.app.CHAPTER_URL
import ireader.app.extension
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Page
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    fun checkContentListIsNotEmpty() {
        assertThat(page.isNotEmpty()).isTrue()
    }
}
