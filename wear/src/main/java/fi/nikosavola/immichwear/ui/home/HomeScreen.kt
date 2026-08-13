package fi.nikosavola.immichwear.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import fi.nikosavola.immichwear.R

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
            ) {
              Text(text = stringResource(R.string.settings_connect_button))
            }
          }
        }
        is HomeUiState.Connected -> {
          item {
            Button(
              onClick = onNavigateToTimeline,
              modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
            ) {
              Text(text = stringResource(R.string.timeline_title))
            }
          }
          item {
            Button(
              onClick = onNavigateToAlbums,
              modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
            ) {
              Text(text = stringResource(R.string.albums_title))
            }
          }
          item {
            Button(
              onClick = onNavigateToFavorites,
              modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
            ) {
              Text(text = stringResource(R.string.favorites_title))
            }
          }
          item {
            Button(
              onClick = onNavigateToSettings,
              modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
            ) {
              Text(text = stringResource(R.string.settings_title))
            }
          }
        }
      }
    }
  }
}
