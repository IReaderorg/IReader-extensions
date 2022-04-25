package books_dto

data class Result(
    val chapter_name: String,
    val chapter_slug: String,
    val novel_image: String,
    val novel_name: String,
    val novel_slug: String
)