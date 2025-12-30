package ireader.testserver

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val error: String,
    val message: String,
    val stackTrace: String? = null
)

@Serializable
data class SourceInfo(
    val id: String,  // String to avoid JavaScript precision issues with large Long IDs
    val name: String,
    val lang: String,
    val baseUrl: String,
    val hasFilters: Boolean,
    val hasCommands: Boolean,
    val filters: List<FilterInfo> = emptyList(),
    val listings: List<String> = emptyList()
)

@Serializable
data class FilterInfo(
    val name: String,
    val type: String,
    val options: List<String>? = null
)

@Serializable
data class MangaResult(
    val key: String,
    val title: String,
    val cover: String,
    val author: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: String = "Unknown"
)

@Serializable
data class ChapterResult(
    val key: String,
    val name: String,
    val number: Float = -1f,
    val dateUpload: Long = 0,
    val scanlator: String = ""
)

@Serializable
data class ContentResult(
    val title: String = "",
    val content: List<String>
)

@Serializable
data class SearchResponse(
    val source: String,
    val query: String,
    val page: Int,
    val hasNextPage: Boolean,
    val results: List<MangaResult>,
    val timing: Long
)

@Serializable
data class DetailsResponse(
    val source: String,
    val url: String,
    val manga: MangaResult,
    val timing: Long
)

@Serializable
data class ChaptersResponse(
    val source: String,
    val url: String,
    val chapters: List<ChapterResult>,
    val timing: Long
)

@Serializable
data class ContentResponse(
    val source: String,
    val url: String,
    val content: ContentResult,
    val timing: Long
)

@Serializable
data class TestResult(
    val success: Boolean,
    val message: String,
    val timing: Long,
    val data: String? = null
)

@Serializable
data class SourceTestSuite(
    val source: String,
    val tests: List<TestResult>
)
