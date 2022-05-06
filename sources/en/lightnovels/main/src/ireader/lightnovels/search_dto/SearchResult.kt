package ireader.lightnovels.search_dto

import ireader.lightnovels.search_dto.Result

data class SearchResult(
    val index: Int,
    val limit: Int,
    val results: List<Result>,
    val total: Int
)