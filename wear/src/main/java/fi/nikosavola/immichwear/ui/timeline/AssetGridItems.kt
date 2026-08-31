package fi.nikosavola.immichwear.ui.timeline

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.ui.ErrorContent

private const val GRID_COLUMNS = 3

/**
 * Shared item content for a paginated 3-column asset grid: loading/error/empty states, rows of
 * thumbnails, and a load-more footer. Used by both the all-photos Timeline and per-album asset
 * grids, which differ only in where [uiState] comes from and the empty-state message.
 *
 * Rows don't use [androidx.wear.compose.material3.lazy.transformedHeight]: that API shrinks the
 * row's layout slot near the screen edges without scaling the thumbnails inside it, so images bled
 * into neighboring rows while scrolling. Plain rows keep a fixed height instead.
 */
fun TransformingLazyColumnScope.assetGridItems(
  uiState: TimelineUiState,
  @StringRes emptyMessageRes: Int,
  onAssetClick: (assetId: String) -> Unit,
  onLoadMore: () -> Unit,
  onRetry: () -> Unit,
  onGoToSettings: () -> Unit,
) {
  when (uiState) {
    is TimelineUiState.Loading -> {
      item { GridMessage(text = stringResource(R.string.loading)) }
    }
    is TimelineUiState.Error -> {
      item {
        ErrorContent(error = uiState.error, onRetry = onRetry, onGoToSettings = onGoToSettings)
      }
    }
    is TimelineUiState.Loaded -> {
      if (uiState.items.isEmpty()) {
        item { GridMessage(text = stringResource(emptyMessageRes)) }
      } else {
        items(items = uiState.items.chunked(GRID_COLUMNS), key = { row -> row.first().id }) { row ->
          AssetRow(row = row, onAssetClick = onAssetClick, modifier = Modifier.fillMaxWidth())
        }
        if (uiState.nextPage != null) {
          item {
            Button(
              onClick = onLoadMore,
              modifier = Modifier.fillMaxWidth(),
              enabled = !uiState.isLoadingMore,
            ) {
              Text(
                text =
                  stringResource(
                    if (uiState.isLoadingMore) {
                      R.string.loading
                    } else {
                      R.string.timeline_load_more_button
                    }
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
private fun GridMessage(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  )
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
