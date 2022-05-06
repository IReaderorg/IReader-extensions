package ireader.lightnovels.books_dto

data class BookListDTO(
    val index: Int,
    val limit: Int,
    val results: List<Result>,
    val size: Int,
    val total: Int
)