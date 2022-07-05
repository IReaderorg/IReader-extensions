package ireader.pandanovel.chapter

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class Data (

	@SerializedName("selectList") val selectList : List<String>,
	@SerializedName("pages") val pages : Int,
	@SerializedName("list") val list : List<Info>,
	@SerializedName("selectNumList") val selectNumList : List<Int>
)