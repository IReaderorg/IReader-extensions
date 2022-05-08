package ireader.ranobes

data class ChapterDTO(
    val book_id: Int,
    val book_link: String,
    val book_title: String,
    val chapters: List<Chapter>,
    val count_all: Int,
    val cstart: Int,
    val default: List<Any>,
    val limit: Int,
    val pages_count: Int,
    val search: String,
    val searchTimeout: Any
)