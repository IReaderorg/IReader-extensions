package ireader.wuxiaworld

import kotlinx.serialization.Serializable

@Serializable
data class PopularDTO(
    val items: List<Item>,
    val result: Boolean,
    val total: Int
)