package fi.nikosavola.immichwear.ui.timeline

import fi.nikosavola.immichwear.data.ImmichError
import fi.nikosavola.immichwear.data.api.dto.AssetDto

sealed interface TimelineUiState {
  data object Loading : TimelineUiState

  data class Error(val error: ImmichError) : TimelineUiState

  /**
   * [nextPage] null means either "no more pages" or "the last load-more attempt failed" - this
   * client does not distinguish the two, so a failed load-more just quietly stops offering more
   * rather than replacing the already-loaded grid with an error screen.
   */
  data class Loaded(
    val items: List<AssetDto>,
    val nextPage: Int?,
    val isLoadingMore: Boolean = false,
    // True when [items] came from ImmichRepository's offline cache fallback rather than a live
    // fetch - see ImmichResult.Success.fromCache.
    val isFromCache: Boolean = false,
  ) : TimelineUiState
}
