package ireader.mtlnovelmodel

import kotlinx.serialization.Serializable

@Serializable
data class mtlSearchItem(
    val items: List<Item>,
)
