package fi.nikosavola.immichwear.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssetDetailViewModel(
  private val repository: ImmichRepository,
  private val assetId: String,
  private val fetchPage: suspend (page: Int?) -> ImmichResult<TimelinePage>,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<AssetDetailUiState>(AssetDetailUiState.Loading)
  val uiState: StateFlow<AssetDetailUiState> = mutableUiState.asStateFlow()
  private val toggledIds = mutableSetOf<String>()

  init {
    load()
  }

  fun load(): Job = viewModelScope.launch {
    mutableUiState.value = AssetDetailUiState.Loading
    locateAsset()
  }

  // Fetches the caller's grid's first page to rebuild the sibling list and find assetId's
  // position in it, so the pager (see AssetDetailScreen) can page through it. The asset being
  // opened was already visible in that grid, so it's on this first page unless the server's list
  // changed underneath us since the grid loaded - in which case this falls back to fetching just
  // this one asset, disabling paging, rather than re-querying further pages to keep searching.
  //
  // The search/metadata query that finds siblings never carries exifInfo (only GET /assets/{id}
  // does), so the opened asset is fetched too. Both requests start together: the pager shows as
  // soon as the list arrives and EXIF is merged in afterwards. The single fetch doubles as the
  // fallback, so it is never requested twice.
  private suspend fun locateAsset(): Unit = coroutineScope {
    val single = async { repository.asset(assetId) }
    val firstPage = fetchPage(null)
    val index =
      (firstPage as? ImmichResult.Success)?.value?.items?.indexOfFirst { it.id == assetId } ?: -1
    if (firstPage is ImmichResult.Success && index >= 0) {
      mutableUiState.value =
        AssetDetailUiState.Loaded(
          assets = firstPage.value.items,
          currentIndex = index,
          nextPage = firstPage.value.nextPage,
        )
      // Best-effort: on failure the plain list entry stays.
      (single.await() as? ImmichResult.Success)?.let { mergeExif(it.value) }
    } else {
      mutableUiState.value =
        when (val result = single.await()) {
          is ImmichResult.Success ->
            AssetDetailUiState.Loaded(assets = listOf(result.value), currentIndex = 0)
          is ImmichResult.Failure -> AssetDetailUiState.Error(result.error)
        }
    }
  }

  // Matches by id because the user may have paged, or loaded more siblings, while this was in
  // flight. Does nothing once the state has left Loaded, so a late result can't undo an error.
  private fun mergeExif(detailed: AssetDto) {
    updateLoaded { state ->
      state.copy(
        assets =
          state.assets.map { item ->
            when {
              item.id != detailed.id -> item
              detailed.id in toggledIds -> detailed.copy(isFavorite = item.isFavorite)
              else -> detailed
            }
          }
      )
    }
  }

  /**
   * Called whenever the sibling pager settles on a new page, so [AssetDetailUiState.Loaded.asset]
   * (what the favorite toggle and details panel act on) tracks what's actually on screen, and to
   * prefetch once the user reaches the end of what's loaded so far.
   */
  fun onPageSettled(index: Int): Job = viewModelScope.launch {
    val state = mutableUiState.value
    if (state is AssetDetailUiState.Loaded) {
      updateLoaded { it.copy(currentIndex = index) }
      if (index == state.assets.lastIndex && state.nextPage != null && !state.isLoadingMore) {
        updateLoaded { it.copy(isLoadingMore = true) }
        val result = fetchPage(state.nextPage)
        updateLoaded { current ->
          when (result) {
            is ImmichResult.Success ->
              current.copy(
                assets = (current.assets + result.value.items).distinctBy { it.id },
                nextPage = result.value.nextPage,
                isLoadingMore = false,
              )
            is ImmichResult.Failure -> current.copy(isLoadingMore = false, nextPage = null)
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
      updateLoaded { it.copy(isTogglingFavorite = true) }
      val succeeded = repository.setFavorite(asset.id, newValue) is ImmichResult.Success
      if (succeeded) toggledIds.add(asset.id)
      updateLoaded { it.withFavorite(asset.id, newValue.takeIf { succeeded }) }
    }
  }

  private fun AssetDetailUiState.Loaded.withFavorite(id: String, value: Boolean?) =
    copy(
      assets = assets.map { if (it.id == id && value != null) it.copy(isFavorite = value) else it },
      isTogglingFavorite = false,
    )

  // Applies to whatever state is current rather than a snapshot taken before a suspension point,
  // so a concurrent EXIF merge or favorite toggle isn't overwritten by a stale copy.
  private fun updateLoaded(transform: (AssetDetailUiState.Loaded) -> AssetDetailUiState.Loaded) {
    mutableUiState.update { state ->
      if (state is AssetDetailUiState.Loaded) transform(state) else state
    }
  }
}
