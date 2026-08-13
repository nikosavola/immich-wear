package fi.nikosavola.immichwear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fi.nikosavola.immichwear.ImmichApp
import fi.nikosavola.immichwear.ui.navigation.ImmichNavHost
import fi.nikosavola.immichwear.ui.theme.ImmichTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appContainer = (application as ImmichApp).appContainer
    setContent { ImmichTheme { ImmichNavHost(appContainer) } }
  }
}
