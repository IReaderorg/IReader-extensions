package org.ireader.app


import ireader.kissnovellove.KissNovel
import org.ireader.app.mock_components.FakeHttpClients
import org.ireader.app.mock_components.FakePreferencesStore
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.HttpSource


const val BOOK_URL = "https://1stkissnovel.love/novel/princess-is-glamorous-in-modern-day/"
const val BOOK_NAME = "Princess is Glamorous in Modern Day"
const val CHAPTER_NAME = "Princess is Glamorous in Modern Day - Chapter 1614: Under the 1581 finale: love forever"
const val CHAPTER_URL = "https://1stkissnovel.love/novel/princess-is-glamorous-in-modern-day/chapter-1614-under-the-1581-finale-love-forever/"
val dependencies = Dependencies(
    FakeHttpClients(),
    FakePreferencesStore()
)
// change KissNovel to the name your source
val extension : HttpSource = object : KissNovel(dependencies) {

}

