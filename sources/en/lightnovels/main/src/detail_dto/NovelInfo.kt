package detail_dto

data class NovelInfo(
    val created_at: String,
    val novel_alternatives: String,
    val novel_description: String,
    val novel_id: Int,
    val novel_image: String,
    val novel_name: String,
    val novel_score: Double,
    val novel_slug: String,
    val novel_status: String,
    val updated_at: String
)