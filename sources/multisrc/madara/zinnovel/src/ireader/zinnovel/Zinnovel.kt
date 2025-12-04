package ireader.zinnovel

import tachiyomix.annotations.MadaraSource

/**
 * 📖 Zinnovel - Zero-code Madara source with custom paths
 */
@MadaraSource(
    name = "Zinnovel",
    baseUrl = "https://zinnovel.com",
    lang = "en",
    id = 54,
    novelsPath = "manga",
    novelPath = "manga",
    chapterPath = "novel"
)
object ZinnovelConfig
