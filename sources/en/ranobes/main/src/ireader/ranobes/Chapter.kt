package ireader.ranobes

import kotlinx.serialization.Serializable


@Serializable
data class Chapter(
    val date: String,
    val id: String,
    val showDate: String,
    val title: String
)
