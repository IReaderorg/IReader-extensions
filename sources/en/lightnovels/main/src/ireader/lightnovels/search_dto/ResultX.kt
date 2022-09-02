package ireader.lightnovels.search_dto

data class ResultX(
    val chapter_name: String,
    val chapter_slug: String,
    val chapter_updated_at: String,
    val id: Int,
    val latest_chapter_id: Int,
    val novel_alternatives: String,
    val novel_image: Any,
    val novel_name: String,
    val novel_slug: String,
    val novel_updated_at: String,
    val score: Double,
    val site_id: Int,
    val status: String
)
