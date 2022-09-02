package ireader.pandanovel.chapter

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterDTO(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: Data
)
