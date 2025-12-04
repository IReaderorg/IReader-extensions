package tachiyomix.annotations

/**
 * Annotation to configure test generation for a source.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateTests(
    /** Generate unit tests */
    val unitTests: Boolean = true,
    /** Generate integration tests (network tests) */
    val integrationTests: Boolean = false,
    /** Test data for search tests */
    val searchQuery: String = "test",
    /** Expected minimum results for search */
    val minSearchResults: Int = 1
)

/**
 * Annotation to provide test fixtures/data for a source.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class TestFixture(
    /** Known working novel URL for testing */
    val novelUrl: String = "",
    /** Known working chapter URL for testing */
    val chapterUrl: String = "",
    /** Expected novel title (for validation) */
    val expectedTitle: String = "",
    /** Expected author (for validation) */
    val expectedAuthor: String = ""
)

/**
 * Annotation to skip certain tests for a source.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SkipTests(
    /** Skip search tests */
    val search: Boolean = false,
    /** Skip chapter list tests */
    val chapters: Boolean = false,
    /** Skip content tests */
    val content: Boolean = false,
    /** Reason for skipping */
    val reason: String = ""
)

/**
 * Annotation to define expected behavior for tests.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class TestExpectations(
    /** Minimum novels expected from latest listing */
    val minLatestNovels: Int = 1,
    /** Minimum chapters expected per novel */
    val minChapters: Int = 1,
    /** Whether source supports pagination */
    val supportsPagination: Boolean = true,
    /** Whether source requires login for some features */
    val requiresLogin: Boolean = false
)
