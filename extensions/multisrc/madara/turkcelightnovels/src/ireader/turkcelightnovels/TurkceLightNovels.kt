package ireader.turkcelightnovels

import tachiyomix.annotations.MadaraSource

/**
 * 🇹🇷 TurkceLightNovels - Zero-code Madara source!
 * 
 * Turkish novel site using Madara theme with custom paths.
 */
@MadaraSource(
    name = "TurkceLightNovels",
    baseUrl = "https://turkcelightnovels.com",
    lang = "tu",
    id = 76,
    // Custom paths for this site
    novelsPath = "light-novel",
    novelPath = "light-novel",
    chapterPath = "light-novel"
)
object TurkceLightNovelsConfig
