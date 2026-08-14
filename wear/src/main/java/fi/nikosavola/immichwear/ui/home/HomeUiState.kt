package fi.nikosavola.immichwear.ui.home

sealed interface HomeUiState {
  data object Loading : HomeUiState

  data object NotConnected : HomeUiState

  // heroAssetId is null while a recent photo is still loading, or if there isn't one - either is
  // fine to just show the plain nav list without a preview, not worth a separate loading/error
  // state.
  data class Connected(val heroAssetId: String? = null) : HomeUiState
}
