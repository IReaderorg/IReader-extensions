package ireader.lightnovels.detail_dto

data class CachedHotNovel(
    val genres: List<Genre>,
    val id: Int,
    val slug: String,
    val title: String
)