package fi.nikosavola.immichwear.ui.navigation

/**
 * Route names as constants (not scattered string literals) so new destinations extend this cleanly.
 */
object ImmichRoutes {
  const val HOME = "home"
  const val SETTINGS = "settings"
  const val TIMELINE = "timeline"
  const val ASSET_DETAIL_PATTERN = "assetDetail/{assetId}"

  fun assetDetail(assetId: String) = "assetDetail/$assetId"
}
