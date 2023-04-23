package ireader.lnmtl.volume

import kotlinx.serialization.Serializable

@Serializable
data class LNMTLVolumnResponseItem(
    val created_at: String?,
    val id: Int,
    val is_fake: Int?,
    val novel_id: Int?,
    val number: Int?,
    val slug: String?,
    val title: String?,
    val updated_at: String?
)