package fi.nikosavola.immichwear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Derived from [SettingsStore.settings] (not a one-shot read) so returning to Home after connecting
 * or signing out in Settings reflects the change immediately, with no manual refresh. Once
 * connected, also fetches the most recent photo for Home's preview thumbnail - collectLatest
 * cancels that fetch if settings change again before it completes.
 */
class HomeViewModel(
  private val settingsStore: SettingsStore,
  private val repository: ImmichRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
  val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

  init {
    viewModelScope.launch {
      settingsStore.settings.collectLatest { settings ->
        if (settings.serverUrl != null && settings.apiKey != null) {
          mutableUiState.value = HomeUiState.Connected()
          mutableUiState.value = HomeUiState.Connected(heroAssetId = fetchHeroAssetId())
        } else {
          mutableUiState.value = HomeUiState.NotConnected
        }
      }
    }
  }

  private suspend fun fetchHeroAssetId(): String? =
    when (val result = repository.timeline()) {
      is ImmichResult.Success -> result.value.items.firstOrNull()?.id
      is ImmichResult.Failure -> null
    }
}
