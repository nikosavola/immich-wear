package fi.nikosavola.immichwear.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire values match the JSON exactly (IMAGE/VIDEO/AUDIO/OTHER), so no custom serial names needed.
 */
@Serializable
enum class AssetTypeEnum {
  IMAGE,
  VIDEO,
  AUDIO,
  OTHER,
}

@Serializable
data class AssetDto(
  val id: String,
  val type: AssetTypeEnum,
  val originalFileName: String,
  val isFavorite: Boolean = false,
  // Preferred over fileCreatedAt for display: already adjusted to the timezone the photo was
  // taken in, matching what the Immich mobile/web clients show.
  val localDateTime: String,
  // Milliseconds; null for static images. Only used to distinguish videos from images at a
  // glance, not to render a formatted duration label - out of scope for v1.
  val duration: Int? = null,
  val thumbhash: String? = null,
)
