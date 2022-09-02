package ireader.wnmtl.content

import kotlinx.serialization.Serializable

@Serializable
data class ContentDTO(
    val code: Int,
    val `data`: Data,
    val message: String
)
