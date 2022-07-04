package ireader.pandanovel.chapter

data class Info(
    val author: String,
    val bookId: Int,
    val chapterUrl: String,
    val content: String,
    val createdAt: String,
    val currentChapterCount: Any,
    val goodNum: Int,
    val id: Int,
    val isEncryption: Int,
    val isSort: Int,
    val name: String,
    val status: Int,
    val updatedAt: String
)