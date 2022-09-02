package ireader.wnmtl.explore

import kotlinx.serialization.Serializable

@Serializable
data class Result(
    val authorId: Int,
    val authorPseudonym: String,
    val coverImgUrl: String,
    val createTime: String,
    val gender: Int,
    val genre: Int,
    val genreName: String,
    val id: Int,
    val language: String,
    val lastUpdateChapterId: Int,
    val lastUpdateChapterOrder: Int,
    val lastUpdateChapterTitle: String,
    val lastUpdateTime: Long,
    val ratingCounts: Double,
    val readCounts: Int,
    val shareFlag: Boolean,
    val source: String,
    val sourceOrgId: Int,
    val status: Int,
    val synopsis: String,
    val tag: String,
    val title: String,
    val type: String,
    val updateTime: String,
    val version: Int,
    val wordCounts: Int
)
