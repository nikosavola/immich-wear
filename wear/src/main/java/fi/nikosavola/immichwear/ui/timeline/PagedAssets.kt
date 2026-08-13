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
      // Pagination is by page number over a live, newest-first list: a page fetched after new
      // assets landed on the server can overlap the previous page. Deduping by id keeps that from
      // producing duplicate LazyColumn keys (a hard crash) or a visibly repeated row.
      val items = (existing + result.value.items).distinctBy { it.id }
      TimelineUiState.Loaded(items = items, nextPage = result.value.nextPage)
    }
    is ImmichResult.Failure -> {
      if (existing.isEmpty()) {
        TimelineUiState.Error(result.error)
      } else {
        TimelineUiState.Loaded(items = existing, nextPage = null)
      }
    }
  }
