package ireader.lnmtl.chapters

import kotlinx.serialization.Serializable

@Serializable
data class LNMTLResponse(
    val current_page: Int?,
    val `data`: List<Data>,
    val from: Int?,
    val last_page: Int,
    val next_page_url: String?,
    val per_page: Int?,
    val prev_page_url: String?,
    val to: Int?,
    val total: Int?
)