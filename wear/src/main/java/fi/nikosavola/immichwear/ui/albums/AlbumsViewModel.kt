package fi.nikosavola.immichwear.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumsViewModel(private val repository: ImmichRepository) : ViewModel() {
  private val mutableUiState = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
  val uiState: StateFlow<AlbumsUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  fun load(): Job = viewModelScope.launch {
    mutableUiState.value = AlbumsUiState.Loading
    mutableUiState.value =
      when (val result = repository.albums()) {
        is ImmichResult.Success -> AlbumsUiState.Loaded(result.value)
        is ImmichResult.Failure -> AlbumsUiState.Error(result.error)
      }
  }
}
