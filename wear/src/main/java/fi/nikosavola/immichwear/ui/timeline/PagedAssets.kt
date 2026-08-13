package fi.nikosavola.immichwear.ui.timeline

import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.data.api.dto.AssetDto

/**
 * Shared paging step for any [TimelineUiState]-driven grid (the all-photos Timeline and per-album
 * grids): appends a fetched page to [existing], or on failure either surfaces an error (first page)
 * or quietly stops offering more (a later page - see [TimelineUiState.Loaded]'s kdoc).
 */
internal suspend fun loadPagedAssets(
  page: Int?,
  existing: List<AssetDto>,
  fetch: suspend (Int?) -> ImmichResult<TimelinePage>,
): TimelineUiState =
  when (val result = fetch(page)) {
    is ImmichResult.Success -> {
      TimelineUiState.Loaded(
        items = existing + result.value.items,
        nextPage = result.value.nextPage,
      )
    }
    is ImmichResult.Failure -> {
      if (existing.isEmpty()) {
        TimelineUiState.Error(result.error)
      } else {
        TimelineUiState.Loaded(items = existing, nextPage = null)
      }
    }
  }
