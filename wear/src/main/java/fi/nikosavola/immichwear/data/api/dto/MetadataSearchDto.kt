package fi.nikosavola.immichwear.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MetadataSearchRequest(
  val size: Int,
  val order: String = "desc",
  // Null (all albums) for the timeline; a single-element list to scope to one album, since the
  // album-by-id endpoint does not return its assets inline in this server version.
  val albumIds: List<String>? = null,
  // Present so the timeline can move past whatever page the caller requests; absent (null) on the
  // very first page.
  val page: Int? = null,
)

@Serializable data class SearchMetadataResponse(val assets: SearchAssetResponse)

@Serializable
data class SearchAssetResponse(
  val items: List<AssetDto>,
  // The server sends this as the next page *number* formatted as a string; null means this was
  // the last page. ImmichRepository parses it defensively (stops pagination rather than crashing
  // if a future server version changes this to a real opaque cursor).
  val nextPage: String? = null,
)
