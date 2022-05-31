package ireader.wnmtl.chapter

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDTO(
    val code: Int,
    val `data`: Data,
    val message: String
)