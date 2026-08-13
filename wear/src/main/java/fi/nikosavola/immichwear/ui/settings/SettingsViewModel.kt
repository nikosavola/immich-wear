package fi.nikosavola.immichwear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val repository: ImmichRepository,
  private val settingsStore: SettingsStore,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
  val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  /**
   * Returns the launched [Job] so tests can `join()` it instead of racing real DataStore/network
   * I/O against virtual-time advancement.
   */
  fun load(): Job = viewModelScope.launch { refresh() }

  fun connect(serverUrl: String, apiKey: String): Job = viewModelScope.launch {
    mutableUiState.value = SettingsUiState.Connecting
    when (val result = repository.connect(serverUrl, apiKey)) {
      is ImmichResult.Success -> refresh()
      is ImmichResult.Failure ->
        mutableUiState.value = SettingsUiState.SignedOut(error = result.error)
    }
  }

  fun signOut(): Job = viewModelScope.launch {
    repository.signOut()
    mutableUiState.value = SettingsUiState.SignedOut()
  }

  private suspend fun refresh() {
    val settings = settingsStore.currentSettings()
    val serverUrl = settings.serverUrl
    mutableUiState.value =
      if (serverUrl != null && settings.apiKey != null) {
        SettingsUiState.SignedIn(email = settings.email, serverUrl = serverUrl)
      } else {
        SettingsUiState.SignedOut()
      }
  }
}
