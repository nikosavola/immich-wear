package fi.nikosavola.immichwear.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.ImmichError

/** Maps a repository failure to the string resource shown in an error state. */
@StringRes
fun errorMessageRes(error: ImmichError): Int =
  when (error) {
    is ImmichError.Unauthorized -> R.string.error_unauthorized
    is ImmichError.Offline -> R.string.error_offline
    is ImmichError.Http -> R.string.error_http
    is ImmichError.ParseError -> R.string.error_parse
    is ImmichError.InvalidServerUrl -> R.string.error_invalid_server_url
    is ImmichError.NotConfigured -> R.string.error_not_configured
  }

/**
 * True when [error] means the stored server/API key is missing or rejected, so the only useful
 * action is routing to Settings rather than offering a retry of the same request.
 */
fun requiresReconnect(error: ImmichError): Boolean =
  error is ImmichError.Unauthorized || error is ImmichError.NotConfigured

/** [ImmichError.Http] is the one variant whose message carries a format argument. */
@Composable
fun errorMessage(error: ImmichError): String =
  if (error is ImmichError.Http) {
    stringResource(errorMessageRes(error), error.code)
  } else {
    stringResource(errorMessageRes(error))
  }
