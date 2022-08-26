package org.ireader.app.tests

import com.google.common.truth.Truth.assertThat
import org.ireader.app.CHAPTER_NAME
import org.ireader.app.CHAPTER_URL
import org.ireader.app.extension
import org.ireader.app.mock_components.FakeHttpClients
import org.ireader.app.mock_components.FakePreferencesStore
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.Page
import org.junit.Before
import org.junit.Test

class ContentChecker {
    var page: List<Page> = emptyList()
    @Before
    fun setup() {
        kotlinx.coroutines.runBlocking {
            page =  extension.getPageList(ChapterInfo(key = CHAPTER_URL, name = CHAPTER_NAME), emptyList())
            print(page)
        }
    }

    @Test
    fun `check  whether page list is empty `() {
        assertThat(page.isNotEmpty()).isTrue()
    }
}

