package fi.nikosavola.immichwear.ui.detail

import fi.nikosavola.immichwear.data.ImmichError
import fi.nikosavola.immichwear.data.api.dto.AssetDto

sealed interface AssetDetailUiState {
  data object Loading : AssetDetailUiState

  data class Error(val error: ImmichError) : AssetDetailUiState

  /**
   * [assets] is the sibling list re-fetched from the same source (timeline/album/favorites) the
   * user opened this asset from, used only to page to the next/previous photo - not for the
   * currently-viewed asset's own metadata, which always comes from [asset]. [nextPage] lets
   * [fi.nikosavola.immichwear.ui.detail.AssetDetailViewModel.onPageSettled] fetch further pages
   * once [currentIndex] reaches the end of what's loaded so far.
   */
  data class Loaded(
    val assets: List<AssetDto>,
    val currentIndex: Int,
    val nextPage: Int? = null,
    val isLoadingMore: Boolean = false,
    val isTogglingFavorite: Boolean = false,
  ) : AssetDetailUiState {
    val asset: AssetDto
      get() = assets[currentIndex]
  }
}
