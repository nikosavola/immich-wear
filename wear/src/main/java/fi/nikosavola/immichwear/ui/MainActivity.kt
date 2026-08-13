package fi.nikosavola.immichwear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.material3.AppScaffold
import fi.nikosavola.immichwear.ImmichApp
import fi.nikosavola.immichwear.ui.settings.SettingsScreen
import fi.nikosavola.immichwear.ui.settings.SettingsViewModel
import fi.nikosavola.immichwear.ui.theme.ImmichTheme

// Hosts SettingsScreen directly for now; replaced by a full nav graph once the Timeline and
// Albums screens exist (there's nowhere else to navigate to yet).
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appContainer = (application as ImmichApp).appContainer

    setContent {
      val viewModel: SettingsViewModel =
        viewModel(
          factory =
            viewModelFactory {
              initializer {
                SettingsViewModel(
                  repository = appContainer.repository,
                  settingsStore = appContainer.settingsStore,
                )
              }
            }
        )
      ImmichTheme { AppScaffold { SettingsScreen(viewModel = viewModel) } }
    }
  }
}
