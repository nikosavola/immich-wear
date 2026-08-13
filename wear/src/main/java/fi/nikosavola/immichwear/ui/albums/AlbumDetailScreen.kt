package fi.nikosavola.immichwear.ui.albums

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.ui.timeline.assetGridItems

@Composable
fun AlbumDetailScreen(
  viewModel: AlbumDetailViewModel,
  onAssetClick: (assetId: String) -> Unit,
  onNavigateToSettings: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val albumName by viewModel.albumName.collectAsStateWithLifecycle()
  val listState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()

  ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item { ListHeader { Text(text = albumName ?: stringResource(R.string.albums_title)) } }
      assetGridItems(
        uiState = uiState,
        transformationSpec = transformationSpec,
        emptyMessageRes = R.string.albums_detail_empty,
        onAssetClick = onAssetClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::load,
        onGoToSettings = onNavigateToSettings,
      )
    }
  }
}
