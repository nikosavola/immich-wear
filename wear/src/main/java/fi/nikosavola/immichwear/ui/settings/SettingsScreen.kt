package fi.nikosavola.immichwear.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.ui.errorMessage

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()

  ScreenScaffold(scrollState = scrollState) { contentPadding ->
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(contentPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(text = stringResource(R.string.settings_title))
      when (val state = uiState) {
        is SettingsUiState.Loading -> {
          Text(text = stringResource(R.string.loading))
        }
        is SettingsUiState.Connecting -> {
          Text(text = stringResource(R.string.settings_connecting))
        }
        is SettingsUiState.SignedIn -> {
          SignedInContent(state = state, onSignOut = viewModel::signOut)
        }
        is SettingsUiState.SignedOut -> {
          SignedOutContent(state = state, onConnect = viewModel::connect)
        }
      }
    }
  }
}

@Composable
private fun SignedInContent(state: SettingsUiState.SignedIn, onSignOut: () -> Unit) {
  val email = state.email
  if (email != null && email.isNotBlank()) {
    Text(text = stringResource(R.string.settings_signed_in_account, email))
  }
  Text(text = state.serverUrl)
  Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
    Text(text = stringResource(R.string.settings_sign_out_button))
  }
}

@Composable
private fun SignedOutContent(
  state: SettingsUiState.SignedOut,
  onConnect: (serverUrl: String, apiKey: String) -> Unit,
) {
  var serverUrlInput by remember { mutableStateOf("") }
  var apiKeyInput by remember { mutableStateOf("") }
  val clipboardManager = LocalClipboardManager.current

  state.error?.let { error -> Text(text = errorMessage(error)) }

  Text(text = stringResource(R.string.settings_server_url_label))
  // BasicTextField, not a material3 text field: a watch gets the system IME, and Wear OS 3+
  // additionally offers phone remote input automatically. Unstyled BasicTextField draws no
  // boundary and defaults to black text, invisible on the dark Wear theme, so both are supplied
  // explicitly.
  BasicTextField(
    value = serverUrlInput,
    onValueChange = { serverUrlInput = it },
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 8.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
        .padding(8.dp),
    singleLine = true,
    textStyle =
      MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
  )

  Text(text = stringResource(R.string.settings_api_key_label))
  // visualTransformation masks the key on screen (and in screenshots/recordings) the same way a
  // password field would; the clipboard-paste flow below is the primary input path, so there is
  // no "show" toggle to verify what was typed.
  BasicTextField(
    value = apiKeyInput,
    onValueChange = { apiKeyInput = it },
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 8.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
        .padding(8.dp)
        .semantics { password() },
    singleLine = true,
    textStyle =
      MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
  )

  // A watch's Wireless debugging pairing already implies a paired phone, and Wear OS syncs the
  // system clipboard between them, so pasting a key/URL copied on the phone works without any
  // Data Layer code. Long-press-to-paste on BasicTextField is not reliably discoverable on a
  // small round screen, so this button reads the clipboard directly as a visible alternative.
  Text(text = stringResource(R.string.settings_clipboard_hint))
  FilledTonalButton(
    onClick = {
      clipboardManager.getText()?.let { pasted ->
        if (serverUrlInput.isBlank()) serverUrlInput = pasted.text else apiKeyInput = pasted.text
      }
    },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(text = stringResource(R.string.settings_paste_button))
  }
  Button(
    onClick = { onConnect(serverUrlInput, apiKeyInput) },
    modifier = Modifier.fillMaxWidth(),
    enabled = serverUrlInput.isNotBlank() && apiKeyInput.isNotBlank(),
  ) {
    Text(text = stringResource(R.string.settings_connect_button))
  }
}
