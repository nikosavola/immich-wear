package fi.nikosavola.immichwear.data.api.dto

import kotlinx.serialization.Serializable

@Serializable data class AssetStatsResponseDto(val total: Int, val images: Int, val videos: Int)
