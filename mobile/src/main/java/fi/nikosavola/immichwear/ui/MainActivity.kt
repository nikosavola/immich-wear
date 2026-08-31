package fi.nikosavola.immichwear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.nikosavola.immichwear.datalayer.WatchLoginSender
import fi.nikosavola.immichwear.ui.theme.LoginTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: LoginViewModel =
        viewModel(
          factory =
            viewModelFactory {
              initializer { LoginViewModel(WatchLoginSender(applicationContext)::send) }
            }
        )
      LoginTheme { LoginScreen(viewModel = viewModel) }
    }
  }
}
