package fi.nikosavola.immichwear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.datalayer.LoginOutcome

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  var serverUrl by rememberSaveable { mutableStateOf("") }
  var apiKey by rememberSaveable { mutableStateOf("") }

  Scaffold { contentPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(contentPadding)
          .padding(horizontal = 24.dp, vertical = 32.dp)
          .widthIn(max = 480.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(R.string.login_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.login_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text(text = stringResource(R.string.login_server_url_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        shape = TextFieldDefaults.shape,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = { Text(text = stringResource(R.string.login_api_key_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = TextFieldDefaults.shape,
        modifier = Modifier.fillMaxWidth(),
      )

      Button(
        onClick = { viewModel.send(serverUrl, apiKey) },
        enabled = serverUrl.isNotBlank() && apiKey.isNotBlank() && uiState != LoginUiState.Sending,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().height(56.dp),
      ) {
        Text(text = stringResource(R.string.login_send_button))
      }

      LoginResultCard(uiState)
    }
  }
}

@Composable
private fun LoginResultCard(uiState: LoginUiState) {
  when (uiState) {
    is LoginUiState.Idle -> {}
    is LoginUiState.Sending -> {
      Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CircularProgressIndicator()
        Text(
          text = stringResource(R.string.login_sending),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    is LoginUiState.Result -> {
      OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors =
          CardDefaults.outlinedCardColors(
            containerColor =
              if (uiState.outcome is LoginOutcome.Success) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.errorContainer
              }
          ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = resultMessage(uiState.outcome),
          style = MaterialTheme.typography.bodyLarge,
          color =
            if (uiState.outcome is LoginOutcome.Success) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onErrorContainer
            },
          modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
      }
      val stats = (uiState.outcome as? LoginOutcome.Success)?.stats
      if (stats != null) {
        Text(
          text = stringResource(R.string.login_stats, stats.total, stats.images, stats.videos),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun resultMessage(outcome: LoginOutcome): String =
  when (outcome) {
    is LoginOutcome.Success -> stringResource(R.string.login_success)
    is LoginOutcome.Failure -> stringResource(R.string.login_failure, outcome.message)
    is LoginOutcome.NoWatchFound -> stringResource(R.string.login_no_watch_found)
    is LoginOutcome.SendFailed -> stringResource(R.string.login_send_failed)
  }
