package fi.nikosavola.immichwear.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.Settings
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Bounds the re-query loop in locateAsset(): the asset being opened was already visible in the
// grid the user tapped it from, so it's normally on the first page - this only guards against an
// unbounded fetch loop if the server's list changed underneath us and the id is never found.
private const val MAX_LOCATE_PAGES = 20

class AssetDetailViewModel(
  private val repository: ImmichRepository,
  private val settingsPrimed: Deferred<Settings>,
  private val assetId: String,
  private val fetchPage: suspend (page: Int?) -> ImmichResult<TimelinePage>,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<AssetDetailUiState>(AssetDetailUiState.Loading)
  val uiState: StateFlow<AssetDetailUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  fun load(): Job = viewModelScope.launch {
    settingsPrimed.await()
    mutableUiState.value = AssetDetailUiState.Loading
    mutableUiState.value = locateAsset()
  }

  // Re-runs fetchPage (the same paginated query the caller's grid used) to rebuild the sibling
  // list and find assetId's position in it, so next()/previous() can page through it. Falls back
  // to fetching just this one asset - disabling next/previous - if it isn't found within
  // MAX_LOCATE_PAGES pages (or fetchPage fails outright).
  private suspend fun locateAsset(): AssetDetailUiState {
    var assets = emptyList<AssetDto>()
    var page: Int? = null
    var attempt = 0
    var keepSearching = true
    while (keepSearching && attempt < MAX_LOCATE_PAGES) {
      when (val result = fetchPage(page)) {
        is ImmichResult.Failure -> {
          keepSearching = false
        }
        is ImmichResult.Success -> {
          assets = (assets + result.value.items).distinctBy { it.id }
          val index = assets.indexOfFirst { it.id == assetId }
          if (index >= 0) {
            return AssetDetailUiState.Loaded(
              // The search/metadata query that finds siblings for paging never carries exifInfo
              // (only GET /assets/{id} does) - enrich just the opened asset with a follow-up
              // fetch so the details panel has EXIF to show. Best-effort: on failure this just
              // keeps the plain entry from the list, same as this class's other silent fallbacks.
              assets = withExifInfo(assets, index, assetId),
              currentIndex = index,
              nextPage = result.value.nextPage,
            )
          }
          page = result.value.nextPage
          keepSearching = page != null
        }
      }
      attempt++
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

  fun previous() {
    val state = mutableUiState.value
    if (state is AssetDetailUiState.Loaded && state.hasPrevious) {
      mutableUiState.value = state.copy(currentIndex = state.currentIndex - 1)
    }
  }

  /** No-op if already loading another page, or if this was already the last asset. */
  fun next(): Job = viewModelScope.launch {
    val state = mutableUiState.value
    if (state is AssetDetailUiState.Loaded && !state.isLoadingMore) {
      if (state.currentIndex + 1 < state.assets.size) {
        mutableUiState.value = state.copy(currentIndex = state.currentIndex + 1)
      } else if (state.nextPage != null) {
        advanceToNextPage(state)
      }
    }
  }

  private suspend fun advanceToNextPage(state: AssetDetailUiState.Loaded) {
    mutableUiState.value = state.copy(isLoadingMore = true)
    mutableUiState.value =
      when (val result = fetchPage(state.nextPage)) {
        is ImmichResult.Success -> {
          val merged = (state.assets + result.value.items).distinctBy { it.id }
          val advanced = merged.size > state.assets.size
          state.copy(
            assets = merged,
            currentIndex = if (advanced) state.currentIndex + 1 else state.currentIndex,
            nextPage = result.value.nextPage,
            isLoadingMore = false,
          )
        }
        is ImmichResult.Failure -> {
          state.copy(isLoadingMore = false, nextPage = null)
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
