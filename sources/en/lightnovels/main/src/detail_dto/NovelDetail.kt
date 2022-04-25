package detail_dto

data class NovelDetail(
    val buildId: String,
    val gsp: Boolean,
    val isFallback: Boolean,
    val nextExport: Boolean,
    val page: String,
    val props: Props,
    val query: Query,
    val runtimeConfig: RuntimeConfig
)