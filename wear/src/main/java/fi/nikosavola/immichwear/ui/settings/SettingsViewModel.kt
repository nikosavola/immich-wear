package fi.nikosavola.immichwear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// How long the transient "Connected"/"Couldn't connect" confirmation stays up before the screen
// moves on by itself - long enough to register as feedback, short enough not to feel stuck.
private const val CONNECT_RESULT_DISPLAY_MS = 900L

class SettingsViewModel(
  private val repository: ImmichRepository,
  private val settingsStore: SettingsStore,
  private val onSignedOut: () -> Unit = {},
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
  val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

  // Reactive (not a one-shot read): the companion phone app can connect independently of this
  // screen (PhoneLoginListenerService calls repository.connect() directly), so if the user has
  // Settings open when that happens, it should flip to SignedIn without a manual refresh. Skipped
  // while Connecting/ConnectResult, both of which this class already drives itself in connect()
  // below - repository.connect() writes serverUrl/apiKey speculatively before validating them, and
  // reacting to that mid-flight write here would race connect()'s own state transitions.
  init {
    viewModelScope.launch {
      settingsStore.settings.collectLatest {
        val current = mutableUiState.value
        if (current !is SettingsUiState.Connecting && current !is SettingsUiState.ConnectResult) {
          refresh()
        }
      }
    }
  }

  fun connect(serverUrl: String, apiKey: String): Job = viewModelScope.launch {
    mutableUiState.value = SettingsUiState.Connecting
    when (val result = repository.connect(serverUrl, apiKey)) {
      is ImmichResult.Success -> {
        mutableUiState.value = SettingsUiState.ConnectResult(success = true)
        delay(CONNECT_RESULT_DISPLAY_MS)
        refresh()
      }
      is ImmichResult.Failure -> {
        mutableUiState.value = SettingsUiState.ConnectResult(success = false)
        delay(CONNECT_RESULT_DISPLAY_MS)
        mutableUiState.value = SettingsUiState.SignedOut(error = result.error)
      }
    }
  }

  fun signOut(): Job = viewModelScope.launch {
    repository.signOut()
    // Cached thumbnails/previews are keyed by placeholder-host URLs that are identical across
    // servers and accounts, so without this they would silently persist on-device (and be shown
    // to) whatever connects next.
    onSignedOut()
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
