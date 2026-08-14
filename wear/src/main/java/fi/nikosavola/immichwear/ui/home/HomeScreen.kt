package fi.nikosavola.immichwear.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.rememberAsyncImagePainter
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.api.AssetThumbnailSize
import fi.nikosavola.immichwear.data.api.thumbnailUrl

@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onNavigateToTimeline: () -> Unit,
  onNavigateToAlbums: () -> Unit,
  onNavigateToFavorites: () -> Unit,
  onNavigateToSettings: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()

  ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item { ListHeader { Text(text = stringResource(R.string.app_name)) } }
      when (val state = uiState) {
        is HomeUiState.Loading -> {
          item { Text(text = stringResource(R.string.loading)) }
        }
        is HomeUiState.NotConnected -> {
          item { Text(text = stringResource(R.string.home_not_connected)) }
          item {
            Button(
              onClick = onNavigateToSettings,
              modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
              transformation = SurfaceTransformation(transformationSpec),
            ) {
              Text(text = stringResource(R.string.settings_connect_button))
            }
          }
        }
        is HomeUiState.Connected -> {
          connectedHomeItems(
            transformationSpec = transformationSpec,
            heroAssetId = state.heroAssetId,
            onNavigateToTimeline = onNavigateToTimeline,
            onNavigateToAlbums = onNavigateToAlbums,
            onNavigateToFavorites = onNavigateToFavorites,
            onNavigateToSettings = onNavigateToSettings,
          )
        }
      }
    }
  }
}

private fun TransformingLazyColumnScope.connectedHomeItems(
  transformationSpec: TransformationSpec,
  heroAssetId: String?,
  onNavigateToTimeline: () -> Unit,
  onNavigateToAlbums: () -> Unit,
  onNavigateToFavorites: () -> Unit,
  onNavigateToSettings: () -> Unit,
) {
  item {
    if (heroAssetId != null) {
      TitleCard(
        onClick = onNavigateToTimeline,
        containerPainter =
          rememberAsyncImagePainter(
            model = thumbnailUrl(heroAssetId, AssetThumbnailSize.THUMBNAIL),
            contentScale = ContentScale.Crop,
          ),
        title = { Text(text = stringResource(R.string.timeline_title)) },
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
      )
    } else {
      Button(
        onClick = onNavigateToTimeline,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
      ) {
        Text(text = stringResource(R.string.timeline_title))
      }
    }
  }
  item {
    FilledTonalButton(
      onClick = onNavigateToAlbums,
      modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
      transformation = SurfaceTransformation(transformationSpec),
    ) {
      Text(text = stringResource(R.string.albums_title))
    }
  }
  item {
    FilledTonalButton(
      onClick = onNavigateToFavorites,
      modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
      transformation = SurfaceTransformation(transformationSpec),
    ) {
      Text(text = stringResource(R.string.favorites_title))
    }
  }
  item {
    FilledTonalButton(
      onClick = onNavigateToSettings,
      modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
      transformation = SurfaceTransformation(transformationSpec),
    ) {
      Text(text = stringResource(R.string.settings_title))
    }
  }
}
