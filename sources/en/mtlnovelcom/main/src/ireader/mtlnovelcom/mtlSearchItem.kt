package ireader.mtlnovelcom

import kotlinx.serialization.Serializable

@Serializable
data class mtlSearchItem(
    val items: List<Item>,
)