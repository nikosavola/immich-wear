package fi.nikosavola.immichwear.ui.navigation

/**
 * Route names as constants (not scattered string literals) so new destinations extend this cleanly.
 */
object ImmichRoutes {
  const val HOME = "home"
  const val SETTINGS = "settings"
  const val TIMELINE = "timeline"
  const val FAVORITES = "favorites"
  const val ALBUMS = "albums"
  const val ALBUM_DETAIL_PATTERN = "albumDetail/{albumId}"

  // `source` tells AssetDetailDestination which paginated list to re-query for the sibling assets
  // used by next/previous paging - "timeline", "favorites", "memory", or "album:<albumId>" for a
  // specific album's grid. See AssetSource in NavGraph.kt.
  const val ASSET_DETAIL_PATTERN = "assetDetail/{source}/{assetId}"
  const val SOURCE_TIMELINE = "timeline"
  const val SOURCE_FAVORITES = "favorites"
  const val SOURCE_MEMORY = "memory"
  private const val SOURCE_ALBUM_PREFIX = "album:"

  fun assetDetailFromTimeline(assetId: String) = assetDetail(SOURCE_TIMELINE, assetId)

  fun assetDetailFromFavorites(assetId: String) = assetDetail(SOURCE_FAVORITES, assetId)

  fun assetDetailFromMemory(assetId: String) = assetDetail(SOURCE_MEMORY, assetId)

  fun assetDetailFromAlbum(albumId: String, assetId: String) =
    assetDetail("$SOURCE_ALBUM_PREFIX$albumId", assetId)

  /**
   * Null unless [source] was built by [assetDetailFromAlbum], in which case this is its albumId.
   */
  fun albumIdFromSource(source: String): String? =
    source.takeIf { it.startsWith(SOURCE_ALBUM_PREFIX) }?.removePrefix(SOURCE_ALBUM_PREFIX)

  fun albumDetail(albumId: String) = "albumDetail/$albumId"

  private fun assetDetail(source: String, assetId: String) = "assetDetail/$source/$assetId"
}
