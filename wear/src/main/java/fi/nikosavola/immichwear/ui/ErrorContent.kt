package fi.nikosavola.immichwear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.ImmichError

/**
 * Shared error display for every screen: the message plus either a retry button, or a
 * go-to-Settings button when [ImmichError] means the stored server/API key is missing or rejected.
 */
@Composable
fun ErrorContent(
  error: ImmichError,
  onRetry: () -> Unit,
  onGoToSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = errorMessage(error),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
    )
    if (requiresReconnect(error)) {
      Button(onClick = onGoToSettings, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.go_to_settings_button))
      }
    } else {
      Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.retry_button))
      }
    }
  }
}
