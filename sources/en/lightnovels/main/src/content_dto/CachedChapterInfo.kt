package content_dto

data class CachedChapterInfo(
    val chapter_id: Int,
    val chapter_index: Int,
    val chapter_name: String,
    val chapter_slug: String,
    val chapter_source_id: Int,
    val content: String,
    val novel: Novel,
    val novel_id: Int,
    val novel_source_id: Int,
    val site_id: Int
)