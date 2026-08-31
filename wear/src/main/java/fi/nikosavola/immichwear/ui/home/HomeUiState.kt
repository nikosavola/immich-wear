package fi.nikosavola.immichwear.ui.home

sealed interface HomeUiState {
  data object Loading : HomeUiState

  data object NotConnected : HomeUiState

  // heroAssetId and memory are both null while still loading, or if there's nothing to show -
  // either is fine to just show the plain nav list without a preview, not worth a separate
  // loading/error state. Unlike heroAssetId (which always has a Recent-photos nav item to fall
  // back to), memory has no standalone destination, so its card is simply omitted when null.
  data class Connected(val heroAssetId: String? = null, val memory: HomeMemoryPreview? = null) :
    HomeUiState
}

/** One "on this day" memory to preview on Home: its first asset, and how long ago it was taken. */
data class HomeMemoryPreview(val assetId: String, val yearsAgo: Int)
