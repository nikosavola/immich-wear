package fi.nikosavola.immichwear.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.TimelinePage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs any [TimelineUiState]-driven grid - the all-photos Timeline and per-album grids only differ
 * in which repository call fetches their pages, so that call is the only thing this class takes as
 * a parameter. [AlbumDetailViewModel] subclasses this to add its album-name fetch.
 */
open class PagedAssetsViewModel(
  private val fetch: suspend (page: Int?) -> ImmichResult<TimelinePage>
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
  val uiState: StateFlow<TimelineUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  fun load(): Job = viewModelScope.launch {
    mutableUiState.value = TimelineUiState.Loading
    mutableUiState.value = loadPagedAssets(page = null, existing = emptyList(), fetch = fetch)
  }

  /** No-op if already loading, or if the previous page was the last one. */
  fun loadMore(): Job = viewModelScope.launch {
    val state = mutableUiState.value
    if (state is TimelineUiState.Loaded && state.nextPage != null && !state.isLoadingMore) {
      mutableUiState.value = state.copy(isLoadingMore = true)
      mutableUiState.value =
        loadPagedAssets(page = state.nextPage, existing = state.items, fetch = fetch)
    }
  }
}
