package fi.nikosavola.immichwear.ui.albums

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.api.dto.AlbumDto
import fi.nikosavola.immichwear.ui.ErrorContent

@Composable
fun AlbumsScreen(
  viewModel: AlbumsViewModel,
  onAlbumClick: (albumId: String) -> Unit,
  onNavigateToSettings: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()

  ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item { ListHeader { Text(text = stringResource(R.string.albums_title)) } }
      when (val state = uiState) {
        is AlbumsUiState.Loading -> {
          item { AlbumsMessage(text = stringResource(R.string.loading)) }
        }
        is AlbumsUiState.Error -> {
          item {
            ErrorContent(
              error = state.error,
              onRetry = viewModel::load,
              onGoToSettings = onNavigateToSettings,
            )
          }
        }
        is AlbumsUiState.Loaded -> {
          if (state.albums.isEmpty()) {
            item { AlbumsMessage(text = stringResource(R.string.albums_empty)) }
          } else {
            items(items = state.albums, key = AlbumDto::id) { album ->
              AlbumRow(
                album = album,
                onClick = { onAlbumClick(album.id) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AlbumsMessage(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  )
}

@Composable
private fun AlbumRow(
  album: AlbumDto,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  transformation: SurfaceTransformation? = null,
) {
  Button(
    onClick = onClick,
    modifier = modifier,
    secondaryLabel = {
      Text(
        text =
          pluralStringResource(R.plurals.albums_asset_count, album.assetCount, album.assetCount)
      )
    },
    transformation = transformation,
  ) {
    Text(text = album.albumName)
  }
}
