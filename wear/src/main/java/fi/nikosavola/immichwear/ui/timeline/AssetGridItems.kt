package fi.nikosavola.immichwear.ui.timeline

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.ui.ErrorContent

private const val GRID_COLUMNS = 3

/**
 * Shared item content for a paginated 3-column asset grid: loading/error/empty states, rows of
 * thumbnails, and a load-more footer. Used by both the all-photos Timeline and per-album asset
 * grids, which differ only in where [uiState] comes from and the empty-state message.
 */
fun TransformingLazyColumnScope.assetGridItems(
  uiState: TimelineUiState,
  transformationSpec: TransformationSpec,
  @StringRes emptyMessageRes: Int,
  onAssetClick: (assetId: String) -> Unit,
  onLoadMore: () -> Unit,
  onRetry: () -> Unit,
  onGoToSettings: () -> Unit,
) {
  when (uiState) {
    is TimelineUiState.Loading -> {
      item { Text(text = stringResource(R.string.loading)) }
    }
    is TimelineUiState.Error -> {
      item {
        ErrorContent(error = uiState.error, onRetry = onRetry, onGoToSettings = onGoToSettings)
      }
    }
    is TimelineUiState.Loaded -> {
      if (uiState.items.isEmpty()) {
        item { Text(text = stringResource(emptyMessageRes)) }
      } else {
        items(items = uiState.items.chunked(GRID_COLUMNS), key = { row -> row.first().id }) { row ->
          AssetRow(
            row = row,
            onAssetClick = onAssetClick,
            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
          )
        }
        if (uiState.nextPage != null) {
          item {
            Button(onClick = onLoadMore, enabled = !uiState.isLoadingMore) {
              Text(
                text =
                  stringResource(
                    if (uiState.isLoadingMore) R.string.loading
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
