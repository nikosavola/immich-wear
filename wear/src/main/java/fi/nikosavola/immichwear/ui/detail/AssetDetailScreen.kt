package fi.nikosavola.immichwear.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.api.AssetThumbnailSize
import fi.nikosavola.immichwear.data.api.dto.AssetTypeEnum
import fi.nikosavola.immichwear.data.api.thumbnailUrl
import fi.nikosavola.immichwear.ui.ErrorContent

@Composable
fun AssetDetailScreen(viewModel: AssetDetailViewModel, onNavigateToSettings: () -> Unit) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  ScreenScaffold(scrollState = rememberScrollState()) {
    Box(modifier = Modifier.fillMaxSize()) {
      when (val state = uiState) {
        is AssetDetailUiState.Loading -> {
          Text(text = stringResource(R.string.loading), modifier = Modifier.align(Alignment.Center))
        }
        is AssetDetailUiState.Error -> {
          Column(modifier = Modifier.align(Alignment.Center)) {
            ErrorContent(
              error = state.error,
              onRetry = viewModel::load,
              onGoToSettings = onNavigateToSettings,
            )
          }
        }
        is AssetDetailUiState.Loaded -> {
          AssetDetailContent(state = state, onToggleFavorite = viewModel::toggleFavorite)
        }
      }
    }
  }
}

@Composable
private fun BoxScope.AssetDetailContent(
  state: AssetDetailUiState.Loaded,
  onToggleFavorite: () -> Unit,
) {
  val asset = state.asset
  AsyncImage(
    model = thumbnailUrl(asset.id, AssetThumbnailSize.PREVIEW),
    contentDescription = asset.originalFileName,
    contentScale = ContentScale.Fit,
    modifier = Modifier.fillMaxSize(),
  )
  if (asset.type == AssetTypeEnum.VIDEO) {
    Box(
      modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(text = "▶")
        Text(text = stringResource(R.string.asset_detail_video_unsupported))
      }
    }
  }
  EdgeButton(
    onClick = onToggleFavorite,
    modifier = Modifier.align(Alignment.BottomCenter),
    enabled = !state.isTogglingFavorite,
  ) {
    Text(text = if (asset.isFavorite) "♥" else "♡")
  }
}
