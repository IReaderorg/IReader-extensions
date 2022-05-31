package ireader.wnmtl.chapter

import kotlinx.serialization.Serializable

@Serializable
data class Result(
    val balance: Int,
    val bookId: Int,
    val chapterOrder: Int,
    val createTime: Long,
    val id: Int,
    val paid: Boolean,
    val paywallStatus: String,
    val status: String,
    val title: String,
    val updateTime: Long,
    val wordCounts: Int
)