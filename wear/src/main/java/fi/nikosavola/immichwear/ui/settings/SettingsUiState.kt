package fi.nikosavola.immichwear.ui.settings

import fi.nikosavola.immichwear.data.ImmichError

sealed interface SettingsUiState {
  data object Loading : SettingsUiState

  /** [error] is set only after a rejected connect attempt; null on a plain signed-out screen. */
  data class SignedOut(val error: ImmichError? = null) : SettingsUiState

  data object Connecting : SettingsUiState

  /** Transient: shown briefly after a connect attempt resolves, then replaced automatically. */
  data class ConnectResult(val success: Boolean) : SettingsUiState

  data class SignedIn(val email: String?, val serverUrl: String) : SettingsUiState
}
