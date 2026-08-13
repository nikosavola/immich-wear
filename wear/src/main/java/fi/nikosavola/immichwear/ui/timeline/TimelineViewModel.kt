package fi.nikosavola.immichwear.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.Settings
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimelineViewModel(
  private val repository: ImmichRepository,
  private val settingsPrimed: Deferred<Settings>,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
  val uiState: StateFlow<TimelineUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  fun load(): Job = viewModelScope.launch {
    settingsPrimed.await()
    mutableUiState.value = TimelineUiState.Loading
    mutableUiState.value =
      loadPagedAssets(page = null, existing = emptyList(), fetch = repository::timeline)
  }

  /** No-op if already loading, or if the previous page was the last one. */
  fun loadMore(): Job = viewModelScope.launch {
    val state = mutableUiState.value
    if (state is TimelineUiState.Loaded && state.nextPage != null && !state.isLoadingMore) {
      mutableUiState.value = state.copy(isLoadingMore = true)
      mutableUiState.value =
        loadPagedAssets(page = state.nextPage, existing = state.items, fetch = repository::timeline)
    }
  }
}
