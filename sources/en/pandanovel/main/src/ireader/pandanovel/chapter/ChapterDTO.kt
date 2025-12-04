package ireader.pandanovel.chapter

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDTO(
    val code: Int,
    val msg: String,
    val data: Data
)
