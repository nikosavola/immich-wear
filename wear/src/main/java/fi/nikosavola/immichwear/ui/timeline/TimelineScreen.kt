package fi.nikosavola.immichwear.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.ui.ErrorContent

private const val GRID_COLUMNS = 3

@Composable
fun TimelineScreen(
  viewModel: TimelineViewModel,
  onAssetClick: (assetId: String) -> Unit,
  onNavigateToSettings: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()

  ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item { ListHeader { Text(text = stringResource(R.string.timeline_title)) } }
      when (val state = uiState) {
        is TimelineUiState.Loading -> {
          item { Text(text = stringResource(R.string.loading)) }
        }
        is TimelineUiState.Error -> {
          item {
            ErrorContent(
              error = state.error,
              onRetry = viewModel::load,
              onGoToSettings = onNavigateToSettings,
            )
          }
        }
        is TimelineUiState.Loaded -> {
          if (state.items.isEmpty()) {
            item { Text(text = stringResource(R.string.timeline_empty)) }
          } else {
            items(items = state.items.chunked(GRID_COLUMNS), key = { row -> row.first().id }) { row
              ->
              AssetRow(
                row = row,
                onAssetClick = onAssetClick,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
              )
            }
            if (state.nextPage != null) {
              item {
                Button(onClick = viewModel::loadMore, enabled = !state.isLoadingMore) {
                  Text(
                    text =
                      stringResource(
                        if (state.isLoadingMore) R.string.loading
                        else R.string.timeline_load_more_button
                      )
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AssetRow(
  row: List<AssetDto>,
  onAssetClick: (assetId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    row.forEach { asset ->
      AssetThumbnail(
        asset = asset,
        onClick = { onAssetClick(asset.id) },
        modifier = Modifier.weight(1f),
      )
    }
    repeat(GRID_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
  }
}
