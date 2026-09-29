package fi.nikosavola.immichwear.data.api.dto

import kotlinx.serialization.Serializable

@Serializable data class UpdateAssetRequest(val isFavorite: Boolean)
