package ireader.pandanovel.chapter

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class Info (

	@SerializedName("id") val id : Int,
	@SerializedName("bookId") val bookId : Int,
	@SerializedName("author") val author : String,
	@SerializedName("name") val name : String,
	@SerializedName("content") val content : String,
	@SerializedName("status") val status : Int,
	@SerializedName("goodNum") val goodNum : Int,
	@SerializedName("createdAt") val createdAt : String,
	@SerializedName("updatedAt") val updatedAt : String,
	@SerializedName("chapterUrl") val chapterUrl : String,
	@SerializedName("currentChapterCount") val currentChapterCount : String?,
	@SerializedName("isEncryption") val isEncryption : Int,
	@SerializedName("isSort") val isSort : Int
)