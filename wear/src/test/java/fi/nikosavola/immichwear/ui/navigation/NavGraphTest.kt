package fi.nikosavola.immichwear.ui.navigation

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.di.AppContainer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private const val WAIT_TIMEOUT_MS = 5_000L

// Covers only the startDestination override MainActivity threads through from the tile's
// signed-out click extra (see TileLayoutsTest) - the rest of the nav graph's routes are exercised
// end-to-end by EndToEndFlowTest and the individual screen tests.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavGraphTest {
  @get:Rule val composeRule = createComposeRule()

  private fun appContainer(): AppContainer =
    AppContainer(ApplicationProvider.getApplicationContext(), apiKeyCipher = FakeApiKeyCipher())

  private fun string(resId: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId)

  private fun waitForText(text: String) {
    composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun `defaults to Home`() {
    composeRule.setContent { ImmichNavHost(appContainer()) }

    waitForText(string(R.string.home_not_connected))
  }

  @Test
  fun `an explicit start destination opens straight to Settings`() {
    composeRule.setContent {
      ImmichNavHost(appContainer(), startDestination = ImmichRoutes.SETTINGS)
    }

    waitForText(string(R.string.settings_title))
  }
}
