package fi.nikosavola.immichwear.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.Settings
import fi.nikosavola.immichwear.data.api.dto.AssetDto
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
    fetch(page = null, existing = emptyList())
  }

  /** No-op if already loading, or if the previous page was the last one. */
  fun loadMore(): Job = viewModelScope.launch {
    val state = mutableUiState.value
    if (state !is TimelineUiState.Loaded || state.nextPage == null || state.isLoadingMore)
      return@launch
    mutableUiState.value = state.copy(isLoadingMore = true)
    fetch(page = state.nextPage, existing = state.items)
  }

  private suspend fun fetch(page: Int?, existing: List<AssetDto>) {
    when (val result = repository.timeline(page)) {
      is ImmichResult.Success -> {
        mutableUiState.value =
          TimelineUiState.Loaded(
            items = existing + result.value.items,
            nextPage = result.value.nextPage,
          )
      }
      is ImmichResult.Failure -> {
        mutableUiState.value =
          if (existing.isEmpty()) {
            TimelineUiState.Error(result.error)
          } else {
            TimelineUiState.Loaded(items = existing, nextPage = null)
          }
      }
    }
  }
}
