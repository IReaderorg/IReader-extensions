package search_dto

data class SearchResult(
    val index: Int,
    val limit: Int,
    val results: List<Result>,
    val total: Int
)