package fi.nikosavola.immichwear.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.ui.errorMessage

private val FIELD_GROUP_SPACING = 16.dp
private val FIELD_HORIZONTAL_PADDING = 8.dp

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()

  // Hoisted above the `when` below, not remembered inside SignedOutContent: that composable is
  // torn down and rebuilt every time uiState cycles through Connecting, which would otherwise
  // wipe both fields - forcing a full retype - on every rejected connect attempt.
  var serverUrlInput by remember { mutableStateOf("") }
  var apiKeyInput by remember { mutableStateOf("") }

  ScreenScaffold(scrollState = scrollState) { contentPadding ->
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(contentPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(FIELD_GROUP_SPACING, Alignment.CenterVertically),
    ) {
      ListHeader { Text(text = stringResource(R.string.settings_title)) }
      when (val state = uiState) {
        is SettingsUiState.Loading -> {
          Text(text = stringResource(R.string.loading))
        }
        is SettingsUiState.Connecting -> {
          Text(text = stringResource(R.string.settings_connecting))
        }
        is SettingsUiState.ConnectResult -> {
          ConnectResultContent(success = state.success)
        }
        is SettingsUiState.SignedIn -> {
          SignedInContent(state = state, onSignOut = viewModel::signOut)
        }
        is SettingsUiState.SignedOut -> {
          SignedOutContent(
            state = state,
            serverUrlInput = serverUrlInput,
            onServerUrlInputChange = { serverUrlInput = it },
            apiKeyInput = apiKeyInput,
            onApiKeyInputChange = { apiKeyInput = it },
            onConnect = viewModel::connect,
          )
        }
      }
    }
  }
}

@Composable
private fun SignedInContent(state: SettingsUiState.SignedIn, onSignOut: () -> Unit) {
  var confirmingSignOut by remember { mutableStateOf(false) }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    val email = state.email
    if (email != null && email.isNotBlank()) {
      Text(
        text = stringResource(R.string.settings_signed_in_account, email),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
      )
    }
    Text(
      text = state.serverUrl,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
  if (confirmingSignOut) {
    Text(
      text = stringResource(R.string.settings_sign_out_confirm_message),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    FilledTonalButton(onClick = { confirmingSignOut = false }, modifier = Modifier.fillMaxWidth()) {
      Text(text = stringResource(R.string.cancel_button))
    }
    Button(
      onClick = onSignOut,
      modifier = Modifier.fillMaxWidth(),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
      Text(text = stringResource(R.string.settings_sign_out_button))
    }
  } else {
    Button(onClick = { confirmingSignOut = true }, modifier = Modifier.fillMaxWidth()) {
      Text(text = stringResource(R.string.settings_sign_out_button))
    }
  }
}

// A brief, wordless-ish confirmation after Connect resolves, before the screen moves on by itself
// (see SettingsViewModel.CONNECT_RESULT_DISPLAY_MS) - Wear OS's own system dialogs use the same
// glyph-in-a-circle pattern for a transient success/failure moment.
@Composable
private fun ConnectResultContent(success: Boolean) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = if (success) "✓" else "✕",
      style = MaterialTheme.typography.displayLarge,
      color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    Text(
      text =
        stringResource(
          if (success) R.string.settings_connect_success else R.string.settings_connect_failed
        ),
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun SignedOutContent(
  state: SettingsUiState.SignedOut,
  serverUrlInput: String,
  onServerUrlInputChange: (String) -> Unit,
  apiKeyInput: String,
  onApiKeyInputChange: (String) -> Unit,
  onConnect: (serverUrl: String, apiKey: String) -> Unit,
) {
  val clipboardManager = LocalClipboardManager.current

  state.error?.let { error ->
    Text(
      text = errorMessage(error),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
  }

  LabeledSettingsField(
    label = stringResource(R.string.settings_server_url_label),
    value = serverUrlInput,
    onValueChange = onServerUrlInputChange,
    keyboardType = KeyboardType.Uri,
  )

  LabeledSettingsField(
    label = stringResource(R.string.settings_api_key_label),
    value = apiKeyInput,
    onValueChange = onApiKeyInputChange,
    keyboardType = KeyboardType.Password,
    isPassword = true,
  )

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // A watch's Wireless debugging pairing already implies a paired phone, and Wear OS syncs the
    // system clipboard between them, so pasting a key/URL copied on the phone works without any
    // Data Layer code. Long-press-to-paste on BasicTextField is not reliably discoverable on a
    // small round screen, so this button reads the clipboard directly as a visible alternative.
    Text(
      text = stringResource(R.string.settings_clipboard_hint),
      style = MaterialTheme.typography.bodyExtraSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    FilledTonalButton(
      onClick = {
        clipboardManager.getText()?.let { pasted ->
          if (serverUrlInput.isBlank()) {
            onServerUrlInputChange(pasted.text)
          } else {
            onApiKeyInputChange(pasted.text)
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = stringResource(R.string.settings_paste_button))
    }
  }
  Button(
    onClick = { onConnect(serverUrlInput, apiKeyInput) },
    modifier = Modifier.fillMaxWidth(),
    enabled = serverUrlInput.isNotBlank() && apiKeyInput.isNotBlank(),
  ) {
    Text(text = stringResource(R.string.settings_connect_button))
  }
}

// A label above a bordered, tonal-filled BasicTextField - not a material3 text field: a watch
// gets the system IME, and Wear OS 3+ additionally offers phone remote input automatically.
// Unstyled BasicTextField draws no boundary and defaults to black text, invisible on the dark
// Wear theme, so both the fill/border and text color are supplied explicitly. The outline mirrors
// Material 3's outlined-text-field convention so this reads as an input, not a static label.
@Composable
private fun LabeledSettingsField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  keyboardType: KeyboardType,
  isPassword: Boolean = false,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = FIELD_HORIZONTAL_PADDING),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    var fieldModifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
        .padding(horizontal = 12.dp, vertical = 10.dp)
    if (isPassword) {
      fieldModifier = fieldModifier.semantics { password() }
    }
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = fieldModifier,
      singleLine = true,
      textStyle =
        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      visualTransformation =
        if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
  }
}
