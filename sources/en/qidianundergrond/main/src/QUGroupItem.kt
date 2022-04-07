import kotlinx.serialization.Serializable

@Serializable
data class QUGroupItem(
    val ID: String,
    val LastUpdated: Int,
    val Name: String,
    val Status: String
)