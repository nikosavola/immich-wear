package fi.nikosavola.immichwear.ui.detail

import fi.nikosavola.immichwear.data.ImmichError
import fi.nikosavola.immichwear.data.api.dto.AssetDto

sealed interface AssetDetailUiState {
  data object Loading : AssetDetailUiState

  data class Error(val error: ImmichError) : AssetDetailUiState

  data class Loaded(val asset: AssetDto, val isTogglingFavorite: Boolean = false) :
    AssetDetailUiState
}
