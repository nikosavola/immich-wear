package fi.nikosavola.immichwear.ui.albums

import fi.nikosavola.immichwear.data.ImmichError
import fi.nikosavola.immichwear.data.api.dto.AlbumDto

sealed interface AlbumsUiState {
  data object Loading : AlbumsUiState

  data class Error(val error: ImmichError) : AlbumsUiState

  data class Loaded(val albums: List<AlbumDto>) : AlbumsUiState
}
