package fi.nikosavola.immichwear.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.api.createImmichClients
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private const val API_KEY = "test-api-key"
private const val EMAIL = "user@example.com"
private const val WAIT_TIMEOUT_MS = 5_000L

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var server: MockWebServer
  private lateinit var settingsStore: SettingsStore
  private lateinit var repository: ImmichRepository

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    settingsStore =
      SettingsStore(
        PreferenceDataStoreFactory.create(
          produceFile = { tempFolder.newFile("settings.preferences_pb") }
        ),
        FakeApiKeyCipher(),
      )
    val clients =
      createImmichClients(
        apiKey = settingsStore.apiKeySupplier,
        serverBaseUrl = settingsStore.serverUrlSupplier,
      )
    repository = ImmichRepository(clients.api, settingsStore)
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun string(@StringRes resId: Int, vararg formatArgs: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId, *formatArgs)

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
  fun `signed-out screen shows both fields and a disabled connect button`() {
    val viewModel = SettingsViewModel(repository, settingsStore)

    composeRule.setContent { SettingsScreen(viewModel = viewModel) }

    waitForText(string(R.string.settings_server_url_label))
    composeRule.onNodeWithText(string(R.string.settings_connect_button)).assertIsNotEnabled()
  }

  @Test
  fun `connect button stays disabled until both fields are filled`() {
    val viewModel = SettingsViewModel(repository, settingsStore)
    composeRule.setContent { SettingsScreen(viewModel = viewModel) }
    waitForText(string(R.string.settings_server_url_label))

    composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("immich.example.com")

    composeRule.onNodeWithText(string(R.string.settings_connect_button)).assertIsNotEnabled()
  }

  @Test
  fun `entering a server url and api key enables the connect button`() {
    val viewModel = SettingsViewModel(repository, settingsStore)
    composeRule.setContent { SettingsScreen(viewModel = viewModel) }
    waitForText(string(R.string.settings_server_url_label))

    typeServerUrlAndApiKey("immich.example.com", "secret-key")

    composeRule.onNodeWithText(string(R.string.settings_connect_button)).assertIsEnabled()
  }

  @Test
  fun `connecting successfully briefly shows a success confirmation, then the signed-in screen`() {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    val viewModel = SettingsViewModel(repository, settingsStore)
    composeRule.setContent { SettingsScreen(viewModel = viewModel) }
    waitForText(string(R.string.settings_server_url_label))

    typeServerUrlAndApiKey(server.url("/").toString(), API_KEY)
    composeRule.onNodeWithText(string(R.string.settings_connect_button)).performClick()

    waitForText(string(R.string.settings_connect_success))
    waitForText(string(R.string.settings_signed_in_account, EMAIL))
  }

  @Test
  fun `a rejected connect attempt briefly shows a failure confirmation, then the error`() {
    server.enqueue(MockResponse().setResponseCode(401))
    val viewModel = SettingsViewModel(repository, settingsStore)
    composeRule.setContent { SettingsScreen(viewModel = viewModel) }
    waitForText(string(R.string.settings_server_url_label))

    val serverUrl = server.url("/").toString()
    typeServerUrlAndApiKey(serverUrl, "wrong-key")
    composeRule.onNodeWithText(string(R.string.settings_connect_button)).performClick()

    waitForText(string(R.string.settings_connect_failed))
    waitForText(string(R.string.error_unauthorized))
    // The regression this guards: fields used to be wiped by the Connecting -> SignedOut(error)
    // transition, forcing a full retype after every rejected attempt.
    composeRule.onAllNodes(hasSetTextAction())[0].assertTextContains(serverUrl)
  }

  @Test
  fun `signing out requires a confirmation before it takes effect`() {
    runBlocking {
      settingsStore.setServerUrl(server.url("/").toString())
      settingsStore.setApiKey(API_KEY)
      settingsStore.setEmail(EMAIL)
    }
    val viewModel = SettingsViewModel(repository, settingsStore)
    composeRule.setContent { SettingsScreen(viewModel = viewModel) }
    waitForText(string(R.string.settings_signed_in_account, EMAIL))

    composeRule.onNodeWithText(string(R.string.settings_sign_out_button)).performClick()
    waitForText(string(R.string.settings_sign_out_confirm_message))

    composeRule.onNodeWithText(string(R.string.settings_sign_out_button)).performClick()

    waitForText(string(R.string.settings_server_url_label))
  }

  @Test
  fun `flips to signed in when the companion app writes settings independently`() {
    val viewModel = SettingsViewModel(repository, settingsStore)
    composeRule.setContent { SettingsScreen(viewModel = viewModel) }
    waitForText(string(R.string.settings_server_url_label))

    // Simulates PhoneLoginListenerService calling repository.connect() while Settings is open -
    // not this screen's own Connect button, which already has its own coverage above.
    runBlocking {
      settingsStore.setServerUrl(server.url("/").toString())
      settingsStore.setApiKey(API_KEY)
      settingsStore.setEmail(EMAIL)
    }

    waitForText(string(R.string.settings_signed_in_account, EMAIL))
  }

  @Test
  fun `playstore flavor hides direct entry and shows the companion-app-required message`() {
    val viewModel = SettingsViewModel(repository, settingsStore)

    composeRule.setContent { SettingsScreen(viewModel = viewModel, supportsDirectLogin = false) }

    waitForText(string(R.string.settings_companion_app_required))
    composeRule.onAllNodesWithText(string(R.string.settings_server_url_label)).assertCountEquals(0)
    composeRule.onAllNodesWithText(string(R.string.settings_connect_button)).assertCountEquals(0)
  }

  @Test
  fun `direct flavor shows the companion-app hint alongside the entry fields`() {
    val viewModel = SettingsViewModel(repository, settingsStore)

    composeRule.setContent { SettingsScreen(viewModel = viewModel, supportsDirectLogin = true) }

    waitForText(string(R.string.settings_companion_app_hint))
    composeRule.onNodeWithText(string(R.string.settings_connect_button)).assertIsNotEnabled()
  }

  @Test
  fun `cancelling the sign-out confirmation stays signed in`() {
    runBlocking {
      settingsStore.setServerUrl(server.url("/").toString())
      settingsStore.setApiKey(API_KEY)
      settingsStore.setEmail(EMAIL)
    }
    val viewModel = SettingsViewModel(repository, settingsStore)
    composeRule.setContent { SettingsScreen(viewModel = viewModel) }
    waitForText(string(R.string.settings_signed_in_account, EMAIL))

    composeRule.onNodeWithText(string(R.string.settings_sign_out_button)).performClick()
    waitForText(string(R.string.settings_sign_out_confirm_message))

    composeRule.onNodeWithText(string(R.string.cancel_button)).performClick()

    waitForText(string(R.string.settings_signed_in_account, EMAIL))
  }
}
