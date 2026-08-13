package fi.nikosavola.immichwear.ui.home

sealed interface HomeUiState {
  data object Loading : HomeUiState

  data object NotConnected : HomeUiState

  data class Connected(val email: String?) : HomeUiState
}
