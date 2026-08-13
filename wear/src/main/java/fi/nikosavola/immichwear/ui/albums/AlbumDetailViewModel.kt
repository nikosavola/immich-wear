package fi.nikosavola.immichwear.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.Settings
import fi.nikosavola.immichwear.ui.timeline.TimelineUiState
import fi.nikosavola.immichwear.ui.timeline.loadPagedAssets
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumDetailViewModel(
  private val repository: ImmichRepository,
  private val settingsPrimed: Deferred<Settings>,
  private val albumId: String,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
  val uiState: StateFlow<TimelineUiState> = mutableUiState.asStateFlow()

  // Null while the album's own metadata is still loading or failed to load; the grid state above
  // carries the real error in that case, so the header just falls back to a generic label rather
  // than duplicating an error UI.
  private val mutableAlbumName = MutableStateFlow<String?>(null)
  val albumName: StateFlow<String?> = mutableAlbumName.asStateFlow()

  init {
    load()
    loadAlbumName()
  }

  fun load(): Job = viewModelScope.launch {
    settingsPrimed.await()
    mutableUiState.value = TimelineUiState.Loading
    mutableUiState.value =
      loadPagedAssets(page = null, existing = emptyList()) { page ->
        repository.albumAssets(albumId, page)
      }
  }

  fun loadMore(): Job = viewModelScope.launch {
    val state = mutableUiState.value
    if (state !is TimelineUiState.Loaded || state.nextPage == null || state.isLoadingMore)
      return@launch
    mutableUiState.value = state.copy(isLoadingMore = true)
    mutableUiState.value =
      loadPagedAssets(page = state.nextPage, existing = state.items) { page ->
        repository.albumAssets(albumId, page)
      }
  }

  private fun loadAlbumName(): Job = viewModelScope.launch {
    settingsPrimed.await()
    when (val result = repository.album(albumId)) {
      is ImmichResult.Success -> mutableAlbumName.value = result.value.albumName
      is ImmichResult.Failure -> Unit
    }
  }
}
