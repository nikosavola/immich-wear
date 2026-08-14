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
  // Only present on the single-asset GET /assets/{id} response, not on search/metadata results -
  // the asset detail screen's metadata panel is the only consumer.
  val exifInfo: ExifInfoDto? = null,
)

@Serializable
data class ExifInfoDto(
  val make: String? = null,
  val model: String? = null,
  val lensModel: String? = null,
  val exifImageWidth: Int? = null,
  val exifImageHeight: Int? = null,
  val city: String? = null,
  val country: String? = null,
  val fileSizeInByte: Long? = null,
  val fNumber: Double? = null,
  // Immich already formats this as a fraction string, e.g. "1/250", not a raw number.
  val exposureTime: String? = null,
  val iso: Int? = null,
  val focalLength: Double? = null,
)
