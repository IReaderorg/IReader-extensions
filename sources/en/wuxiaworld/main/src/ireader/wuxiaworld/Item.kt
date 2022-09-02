package ireader.wuxiaworld

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val abbreviation: String,
    val active: Boolean,
    val authorName: String?,
    val chapterGroups: String?,
    val coverUrl: String,
    val description: String,
    val ebooks: List<String>,
    val excludedFromVipSelection: Boolean,
    val genres: List<String>,
    val id: Int,
    val isFree: Boolean,
    val karmaActive: Boolean,
    val language: String,
    val languageAbbreviation: String,
    val latestAnnouncement: Long?,
    val name: String,
    val novelHasSponsorPlans: Boolean,
    val reviewCount: Int,
    val reviewScore: Double?,
    val siteCreditsEnabled: Boolean?,
    val slug: String,
    val sponsorPlans: String?,
    val synopsis: String?,
    val tags: List<String>?,
    val teaserMessage: String?,
    val translatorId: String?,
    val translatorName: String?,
    val translatorUserName: String?,
    val userHasEbook: Boolean?,
    val userHasNovelUnlocked: Boolean?,
    val visible: Boolean?
)
