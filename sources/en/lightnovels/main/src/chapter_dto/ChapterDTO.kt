package chapter_dto

data class ChapterDTO(
    val limit: Int,
    val results: List<Result>,
    val size: Int,
    val start: Int,
    val total: Int
)