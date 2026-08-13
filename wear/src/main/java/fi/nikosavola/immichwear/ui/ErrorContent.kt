package fi.nikosavola.immichwear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.ImmichError

/**
 * Shared error display for every screen: the message plus either a retry button, or a
 * go-to-Settings button when [ImmichError] means the stored server/API key is missing or rejected.
 */
@Composable
fun ErrorContent(error: ImmichError, onRetry: () -> Unit, onGoToSettings: () -> Unit) {
  Text(text = errorMessage(error))
  if (requiresReconnect(error)) {
    Button(onClick = onGoToSettings) { Text(text = stringResource(R.string.go_to_settings_button)) }
  } else {
    Button(onClick = onRetry) { Text(text = stringResource(R.string.retry_button)) }
  }
}
