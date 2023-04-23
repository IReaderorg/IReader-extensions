package ireader.lnmtl.chapters

import kotlinx.serialization.Serializable

@Serializable
data class Data(
    val comments_count: Int?,
    val created_at: String?,
    val downloaded: Boolean?,
    val hide_ads: Int?,
    val id: Int,
    val is_special: Int?,
    val length: Int?,
    val novel_id: Int?,
    val number: Int,
    val number_special: Int?,
    val original: Int?,
    val part: Int?,
    val position: Int?,
    val ratings_count: Int?,
    val ratings_negative: Int?,
    val ratings_neutral: Int?,
    val ratings_positive: Int?,
    val retranslated_at: String?,
    val retranslations_count: Int?,
    val site_url: String?,
    val slug: String?,
    val title: String,
    val title_json: String?,
    val title_raw: String?,
    val translated_body: Boolean?,
    val translated_title: Boolean?,
    val updated_at: String?,
    val updater_id: Int?,
    val url: String,
    val volume_id: Int
)