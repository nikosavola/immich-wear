package fi.nikosavola.immichwear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.datalayer.LoginOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
  data object Idle : LoginUiState

  data object Sending : LoginUiState

  data class Result(val outcome: LoginOutcome) : LoginUiState
}

/**
 * [sendToWatch] is injected as a plain suspend function (not a concrete `WatchLoginSender`) so
 * tests can supply a fake without touching Play Services - same pattern as :wear's
 * AssetDetailViewModel taking a `fetchPage` lambda instead of an `ImmichRepository`.
 */
class LoginViewModel(
  private val sendToWatch: suspend (serverUrl: String, apiKey: String) -> LoginOutcome
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
  val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

  fun send(serverUrl: String, apiKey: String): Job = viewModelScope.launch {
    mutableUiState.value = LoginUiState.Sending
    mutableUiState.value = LoginUiState.Result(sendToWatch(serverUrl, apiKey))
  }

  fun reset() {
    mutableUiState.value = LoginUiState.Idle
  }
}
