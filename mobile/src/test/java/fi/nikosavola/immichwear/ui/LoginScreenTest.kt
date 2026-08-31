package fi.nikosavola.immichwear.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.datalayer.LoginOutcome
import fi.nikosavola.immichwear.datalayer.LoginStats
import fi.nikosavola.immichwear.ui.theme.LoginTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private const val SERVER_URL = "https://immich.example.com"
private const val API_KEY = "test-api-key"
private const val WAIT_TIMEOUT_MS = 5_000L

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginScreenTest {
  @get:Rule val composeRule = createComposeRule()

  private fun string(resId: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId, *args)

  private fun waitForText(text: String) {
    composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  // The screen has exactly two text-input fields: server URL first, then API key.
  private fun typeServerUrlAndApiKey(serverUrl: String, apiKey: String) {
    val fields = composeRule.onAllNodes(hasSetTextAction())
    fields[0].performTextInput(serverUrl)
    fields[1].performTextInput(apiKey)
  }

  @Test
  fun `send button starts disabled and enables once both fields are filled`() {
    val viewModel = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.Success(stats = null) })
    composeRule.setContent { LoginTheme { LoginScreen(viewModel = viewModel) } }

    composeRule.onNodeWithText(string(R.string.login_send_button)).assertIsNotEnabled()

    typeServerUrlAndApiKey(SERVER_URL, API_KEY)

    composeRule.onNodeWithText(string(R.string.login_send_button)).assertIsEnabled()
  }

  @Test
  fun `a successful outcome shows the success card`() {
    val viewModel = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.Success(stats = null) })
    composeRule.setContent { LoginTheme { LoginScreen(viewModel = viewModel) } }
    typeServerUrlAndApiKey(SERVER_URL, API_KEY)

    composeRule.onNodeWithText(string(R.string.login_send_button)).performClick()

    waitForText(string(R.string.login_success))
  }

  @Test
  fun `a successful outcome with stats shows the stats line`() {
    val viewModel =
      LoginViewModel(
        sendToWatch = { _, _ ->
          LoginOutcome.Success(LoginStats(total = 42, images = 40, videos = 2))
        }
      )
    composeRule.setContent { LoginTheme { LoginScreen(viewModel = viewModel) } }
    typeServerUrlAndApiKey(SERVER_URL, API_KEY)

    composeRule.onNodeWithText(string(R.string.login_send_button)).performClick()

    waitForText(string(R.string.login_stats, 42, 40, 2))
  }

  @Test
  fun `a successful outcome with no stats shows no stats line`() {
    val viewModel = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.Success(stats = null) })
    composeRule.setContent { LoginTheme { LoginScreen(viewModel = viewModel) } }
    typeServerUrlAndApiKey(SERVER_URL, API_KEY)

    composeRule.onNodeWithText(string(R.string.login_send_button)).performClick()
    waitForText(string(R.string.login_success))

    composeRule.onAllNodesWithText("assets on your server", substring = true).assertCountEquals(0)
  }

  @Test
  fun `a failure outcome shows the watch's error message`() {
    val viewModel =
      LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.Failure("Invalid API key") })
    composeRule.setContent { LoginTheme { LoginScreen(viewModel = viewModel) } }
    typeServerUrlAndApiKey(SERVER_URL, "wrong-key")

    composeRule.onNodeWithText(string(R.string.login_send_button)).performClick()

    waitForText(string(R.string.login_failure, "Invalid API key"))
  }

  @Test
  fun `no paired watch shows the no-watch-found message`() {
    val viewModel = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.NoWatchFound })
    composeRule.setContent { LoginTheme { LoginScreen(viewModel = viewModel) } }
    typeServerUrlAndApiKey(SERVER_URL, API_KEY)

    composeRule.onNodeWithText(string(R.string.login_send_button)).performClick()

    waitForText(string(R.string.login_no_watch_found))
  }

  @Test
  fun `a send failure shows the send-failed message`() {
    val viewModel = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.SendFailed })
    composeRule.setContent { LoginTheme { LoginScreen(viewModel = viewModel) } }
    typeServerUrlAndApiKey(SERVER_URL, API_KEY)

    composeRule.onNodeWithText(string(R.string.login_send_button)).performClick()

    waitForText(string(R.string.login_send_failed))
  }

  // Regression test for the bug this session found and fixed: serverUrl/apiKey used plain
  // `remember` instead of `rememberSaveable`, silently wiping typed input on any configuration
  // change (e.g. rotating the phone) since `remember` alone doesn't survive activity recreation.
  @Test
  fun `typed fields survive a configuration change`() {
    val restorationTester = StateRestorationTester(composeRule)
    val viewModel = LoginViewModel(sendToWatch = { _, _ -> LoginOutcome.Success(stats = null) })
    restorationTester.setContent { LoginTheme { LoginScreen(viewModel = viewModel) } }
    typeServerUrlAndApiKey(SERVER_URL, API_KEY)
    composeRule.onNodeWithText(string(R.string.login_send_button)).assertIsEnabled()

    restorationTester.emulateSavedInstanceStateRestore()

    // Still enabled only if both fields' text survived the restore - the button is disabled
    // whenever either field is blank.
    composeRule.onNodeWithText(string(R.string.login_send_button)).assertIsEnabled()
  }
}
