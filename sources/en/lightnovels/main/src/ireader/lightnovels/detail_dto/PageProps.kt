package ireader.lightnovels.detail_dto

data class PageProps(
    val authors: List<Author>,
    val cachedChapterResults: String,
    val cachedHotNovels: List<CachedHotNovel>,
    val cachedLatestChapters: List<CachedLatestChapter>,
    val genres: List<GenreX>,
    val novelInfo: NovelInfo,
    val tags: List<Tag>
)
