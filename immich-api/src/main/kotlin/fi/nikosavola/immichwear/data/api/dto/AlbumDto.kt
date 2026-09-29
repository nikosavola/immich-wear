package fi.nikosavola.immichwear.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AlbumDto(
  val id: String,
  val albumName: String,
  val assetCount: Int,
  val albumThumbnailAssetId: String? = null,
)
