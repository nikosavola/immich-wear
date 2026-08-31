package fi.nikosavola.immichwear.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fi.nikosavola.immichwear.R

@Composable
fun TimelineScreen(
  viewModel: PagedAssetsViewModel,
  onAssetClick: (assetId: String) -> Unit,
  onNavigateToSettings: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberTransformingLazyColumnState()

  ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item { ListHeader { Text(text = stringResource(R.string.timeline_title)) } }
      assetGridItems(
        uiState = uiState,
        emptyMessageRes = R.string.timeline_empty,
        onAssetClick = onAssetClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::load,
        onGoToSettings = onNavigateToSettings,
      )
    }
  }
}
