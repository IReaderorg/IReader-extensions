package ireader.mtlnation

data class Data(
    val content: String,
    val created_at: String,
    val id: Int,
    val index: Int,
    val novel: String?,
    val novel_id: Int,
    val slug: String,
    val title: String,
    val updated_at: String,
    val word_count: Int
)
