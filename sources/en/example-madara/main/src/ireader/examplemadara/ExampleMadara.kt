package ireader.examplemadara

import tachiyomix.annotations.MadaraSource
import tachiyomix.annotations.SourceMeta

/**
 * Example Madara-based source using the @MadaraSource annotation.
 * 
 * Instead of manually creating a class that extends Madara,
 * KSP generates the source class from this configuration.
 * 
 * This reduces boilerplate from ~20 lines to just annotations!
 */
@MadaraSource(
    name = "ExampleMadara",
    baseUrl = "https://example-madara.com",
    lang = "en",
    id = 888888,
    novelsPath = "novel",
    novelPath = "novel",
    chapterPath = "novel"
)
@SourceMeta(
    description = "Example Madara theme source",
    nsfw = false,
    tags = ["english", "madara", "example"]
)
object ExampleMadaraConfig

// The KSP processor will generate:
// - ExampleMadaraGenerated class that extends Madara
// - Proper constructor with Dependencies
// - All required overrides (name, id, lang, baseUrl)
