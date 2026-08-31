package fi.nikosavola.immichwear.ui.albums

import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.ui.timeline.PagedAssetsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumDetailViewModel(private val repository: ImmichRepository, private val albumId: String) :
  PagedAssetsViewModel(fetch = { page -> repository.albumAssets(albumId, page) }) {
  // Null while the album's own metadata is still loading or failed to load; the grid state above
  // carries the real error in that case, so the header just falls back to a generic label rather
  // than duplicating an error UI.
  private val mutableAlbumName = MutableStateFlow<String?>(null)
  val albumName: StateFlow<String?> = mutableAlbumName.asStateFlow()

  init {
    loadAlbumName()
  }

  private fun loadAlbumName(): Job = viewModelScope.launch {
    when (val result = repository.album(albumId)) {
      is ImmichResult.Success -> mutableAlbumName.value = result.value.albumName
      is ImmichResult.Failure -> Unit
    }
  }
}
