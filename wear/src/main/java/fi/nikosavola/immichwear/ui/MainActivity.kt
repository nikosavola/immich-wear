package fi.nikosavola.immichwear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fi.nikosavola.immichwear.ImmichApp
import fi.nikosavola.immichwear.ui.navigation.ImmichNavHost
import fi.nikosavola.immichwear.ui.navigation.ImmichRoutes
import fi.nikosavola.immichwear.ui.theme.ImmichTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    val appContainer = (application as ImmichApp).appContainer
    val startDestination =
      intent.getStringExtra(ImmichRoutes.EXTRA_START_DESTINATION) ?: ImmichRoutes.HOME
    setContent { ImmichTheme { ImmichNavHost(appContainer, startDestination = startDestination) } }
  }
}
