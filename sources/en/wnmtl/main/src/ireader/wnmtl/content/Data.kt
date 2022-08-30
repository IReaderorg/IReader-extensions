package ireader.wnmtl.content

import kotlinx.serialization.Serializable

@Serializable
data class Data(
    val balance: Int,
    val bookId: Int,
    val bookTitle: String,
    val chapterOrder: Int,
    val content: String,
    val createTime: Long,
    val id: Int,
    val nextId: Int,
    val nextTitle: String,
    val orgId: Long?,
    val paid: Boolean,
    val paywallStatus: String,
    val preId: Int,
    val preTitle: String,
    val status: String,
    val title: String,
    val updateTime: Long,
    val wordCounts: Int
)