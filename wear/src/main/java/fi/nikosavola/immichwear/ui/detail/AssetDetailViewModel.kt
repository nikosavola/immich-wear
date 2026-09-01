package fi.nikosavola.immichwear.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AssetDetailViewModel(
  private val repository: ImmichRepository,
  private val assetId: String,
  private val fetchPage: suspend (page: Int?) -> ImmichResult<TimelinePage>,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<AssetDetailUiState>(AssetDetailUiState.Loading)
  val uiState: StateFlow<AssetDetailUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  fun load(): Job = viewModelScope.launch {
    mutableUiState.value = AssetDetailUiState.Loading
    mutableUiState.value = locateAsset()
  }

  // Fetches the caller's grid's first page to rebuild the sibling list and find assetId's
  // position in it, so the pager (see AssetDetailScreen) can page through it. The asset being
  // opened was already visible in that grid, so it's on this first page unless the server's list
  // changed underneath us since the grid loaded - in which case this falls back to fetching just
  // this one asset, disabling paging, rather than re-querying further pages to keep searching.
  private suspend fun locateAsset(): AssetDetailUiState {
    val firstPage = fetchPage(null)
    if (firstPage is ImmichResult.Success) {
      val index = firstPage.value.items.indexOfFirst { it.id == assetId }
      if (index >= 0) {
        return AssetDetailUiState.Loaded(
          // The search/metadata query that finds siblings for paging never carries exifInfo (only
          // GET /assets/{id} does) - enrich just the opened asset with a follow-up fetch so the
          // details panel has EXIF to show. Best-effort: on failure this just keeps the plain
          // entry from the list, same as this class's other silent fallbacks.
          assets = withExifInfo(firstPage.value.items, index, assetId),
          currentIndex = index,
          nextPage = firstPage.value.nextPage,
        )
      }
    }
    return when (val single = repository.asset(assetId)) {
      is ImmichResult.Success ->
        AssetDetailUiState.Loaded(assets = listOf(single.value), currentIndex = 0)
      is ImmichResult.Failure -> AssetDetailUiState.Error(single.error)
    }
  }

  private suspend fun withExifInfo(
    assets: List<AssetDto>,
    index: Int,
    assetId: String,
  ): List<AssetDto> =
    when (val result = repository.asset(assetId)) {
      is ImmichResult.Success -> assets.toMutableList().apply { set(index, result.value) }
      is ImmichResult.Failure -> assets
    }

  /**
   * Called whenever the sibling pager settles on a new page, so [AssetDetailUiState.Loaded.asset]
   * (what the favorite toggle and details panel act on) tracks what's actually on screen, and to
   * prefetch once the user reaches the end of what's loaded so far.
   */
  fun onPageSettled(index: Int): Job = viewModelScope.launch {
    val state = mutableUiState.value
    if (state is AssetDetailUiState.Loaded) {
      mutableUiState.value = state.copy(currentIndex = index)
      if (index == state.assets.lastIndex && state.nextPage != null && !state.isLoadingMore) {
        mutableUiState.value = state.copy(currentIndex = index, isLoadingMore = true)
        mutableUiState.value =
          when (val result = fetchPage(state.nextPage)) {
            is ImmichResult.Success -> {
              val merged = (state.assets + result.value.items).distinctBy { it.id }
              state.copy(
                currentIndex = index,
                assets = merged,
                nextPage = result.value.nextPage,
                isLoadingMore = false,
              )
            }
            is ImmichResult.Failure -> {
              state.copy(currentIndex = index, isLoadingMore = false, nextPage = null)
            }
          }
      }
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
      val asset = state.asset
      val newValue = !asset.isFavorite
      mutableUiState.value = state.copy(isTogglingFavorite = true)
      mutableUiState.value =
        when (repository.setFavorite(asset.id, newValue)) {
          is ImmichResult.Success ->
            state.copy(
              assets =
                state.assets.mapIndexed { index, item ->
                  if (index == state.currentIndex) item.copy(isFavorite = newValue) else item
                },
              isTogglingFavorite = false,
            )
          is ImmichResult.Failure -> state.copy(isTogglingFavorite = false)
        }
    }
  }
}
