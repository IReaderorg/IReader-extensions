package ireader.lightnovels.chapter_dto

data class Result(
    val chapter_index: Int,
    val chapter_name: String,
    val id: Int,
    val slug: String,
    val updated_at: String
)
