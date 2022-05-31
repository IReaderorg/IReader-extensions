package ireader.wnmtl.chapter

import kotlinx.serialization.Serializable

@Serializable
data class Data(
    val list: List<Result>,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int
)