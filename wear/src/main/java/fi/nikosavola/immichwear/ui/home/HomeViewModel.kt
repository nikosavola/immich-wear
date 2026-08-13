package fi.nikosavola.immichwear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Keeps the settings collection alive through a brief backgrounding (e.g. navigating to Timeline
// and back) instead of restarting it from scratch every time Home re-enters composition.
private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Derived directly from [SettingsStore.settings] (not a one-shot read) so returning to Home after
 * connecting or signing out in Settings reflects the change immediately, with no manual refresh.
 */
class HomeViewModel(settingsStore: SettingsStore) : ViewModel() {
  val uiState: StateFlow<HomeUiState> =
    settingsStore.settings
      .map { settings ->
        if (settings.serverUrl != null && settings.apiKey != null) {
          HomeUiState.Connected
        } else {
          HomeUiState.NotConnected
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        HomeUiState.Loading,
      )
}
