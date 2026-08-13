package fi.nikosavola.immichwear.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.Settings
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumsViewModel(
  private val repository: ImmichRepository,
  private val settingsPrimed: Deferred<Settings>,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
  val uiState: StateFlow<AlbumsUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  fun load(): Job = viewModelScope.launch {
    settingsPrimed.await()
    mutableUiState.value = AlbumsUiState.Loading
    mutableUiState.value =
      when (val result = repository.albums()) {
        is ImmichResult.Success -> AlbumsUiState.Loaded(result.value)
        is ImmichResult.Failure -> AlbumsUiState.Error(result.error)
      }
  }
}
