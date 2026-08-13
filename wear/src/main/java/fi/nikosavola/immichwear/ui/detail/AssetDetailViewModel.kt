package fi.nikosavola.immichwear.ui.detail

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

class AssetDetailViewModel(
  private val repository: ImmichRepository,
  private val settingsPrimed: Deferred<Settings>,
  private val assetId: String,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<AssetDetailUiState>(AssetDetailUiState.Loading)
  val uiState: StateFlow<AssetDetailUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  fun load(): Job = viewModelScope.launch {
    settingsPrimed.await()
    mutableUiState.value = AssetDetailUiState.Loading
    when (val result = repository.asset(assetId)) {
      is ImmichResult.Success -> mutableUiState.value = AssetDetailUiState.Loaded(result.value)
      is ImmichResult.Failure -> mutableUiState.value = AssetDetailUiState.Error(result.error)
    }
  }

  /**
   * No-op if the asset hasn't loaded yet or a toggle is already in flight. On failure, silently
   * reverts to the previous value rather than surfacing a separate error UI - the same
   * deliberate-simplicity call as Timeline's failed load-more.
   */
  fun toggleFavorite(): Job = viewModelScope.launch {
    val state = mutableUiState.value
    if (state is AssetDetailUiState.Loaded && !state.isTogglingFavorite) {
      val newValue = !state.asset.isFavorite
      mutableUiState.value = state.copy(isTogglingFavorite = true)
      mutableUiState.value =
        when (repository.setFavorite(assetId, newValue)) {
          is ImmichResult.Success ->
            state.copy(asset = state.asset.copy(isFavorite = newValue), isTogglingFavorite = false)
          is ImmichResult.Failure -> state.copy(isTogglingFavorite = false)
        }
    }
  }
}
