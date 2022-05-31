package ireader.wnmtl.explore

import kotlinx.serialization.Serializable

@Serializable
data class ExploreDTO(
        val code: Int,
        val `data`: Data,
        val message: String
)