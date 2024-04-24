package ireader.mtlnovelmodel

import kotlinx.serialization.Serializable

@Serializable
data class Result(
    val cn: String,
    val permalink: String,
    val shortname: String,
    val thumbnail: String,
    val title: String,
)
