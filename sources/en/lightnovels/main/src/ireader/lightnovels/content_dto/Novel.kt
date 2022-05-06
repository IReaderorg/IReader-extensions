package ireader.lightnovels.content_dto

data class Novel(
    val genre_name: String,
    val genre_slug: String,
    val novel_id: Int,
    val novel_image: String,
    val novel_name: String,
    val novel_slug: String
)