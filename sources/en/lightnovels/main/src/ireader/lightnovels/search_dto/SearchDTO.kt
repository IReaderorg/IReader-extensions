package ireader.lightnovels.search_dto

data class SearchDTO(
    val index: Int,
    val limit: Int,
    val results: List<ResultX>,
    val total: Int
)