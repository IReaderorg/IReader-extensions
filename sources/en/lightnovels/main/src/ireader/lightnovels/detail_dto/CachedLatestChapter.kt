package ireader.lightnovels.detail_dto

data class CachedLatestChapter(
    val chapter_index: Int,
    val chapter_name: String,
    val slug: String,
    val updated_at: String
)