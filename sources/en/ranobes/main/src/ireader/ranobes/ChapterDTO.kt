package ireader.ranobes

import kotlinx.serialization.Serializable


@Serializable
data class ChapterDTO(
    val book_id: Int,
    val book_link: String,
    val book_title: String,
    val chapters: List<Chapter>,
    val count_all: Int,
    val cstart: Int,
    val default: List<String>,
    val limit: Int,
    val pages_count: Int,
    val search: String,
    val searchTimeout: Long
)
