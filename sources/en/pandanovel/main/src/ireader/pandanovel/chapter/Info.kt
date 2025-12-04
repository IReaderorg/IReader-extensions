package ireader.pandanovel.chapter

import kotlinx.serialization.Serializable

@Serializable
data class Info(
    val id: Int,
    val bookId: Int,
    val author: String,
    val name: String,
    val content: String,
    val status: Int,
    val goodNum: Int,
    val createdAt: String,
    val updatedAt: String,
    val chapterUrl: String,
    val currentChapterCount: String?,
    val isEncryption: Int,
    val isSort: Int
)
