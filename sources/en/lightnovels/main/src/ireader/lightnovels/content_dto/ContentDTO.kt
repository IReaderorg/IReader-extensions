package ireader.lightnovels.content_dto

data class ContentDTO(
    val buildId: String,
    val gssp: Boolean,
    val isFallback: Boolean,
    val page: String,
    val props: Props,
    val query: Query,
    val runtimeConfig: RuntimeConfig
)