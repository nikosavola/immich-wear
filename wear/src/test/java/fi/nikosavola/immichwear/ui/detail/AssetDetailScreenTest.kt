package fi.nikosavola.immichwear.ui.detail

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.api.createImmichClients
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private const val API_KEY = "test-api-key"
private const val WAIT_TIMEOUT_MS = 5_000L

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssetDetailScreenTest {
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
    runBlocking {
      settingsStore.setServerUrl(server.url("/").toString())
      settingsStore.setApiKey(API_KEY)
    }
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

  private fun settingsPrimed() =
    CompletableDeferred(runBlocking { settingsStore.currentSettings() })

  private fun string(@StringRes resId: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId)

  private fun waitForText(text: String) {
    composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun assetJson(isFavorite: Boolean = false, type: String = "IMAGE") =
    """{"id": "a1", "type": "$type", "originalFileName": "a1.jpg", "isFavorite": $isFavorite,""" +
      """ "localDateTime": "2026-01-01T00:00:00Z"}"""

  @Test
  fun `a non-favorited asset shows the outline heart, and tapping it favorites`() {
    server.enqueue(MockResponse().setBody(assetJson(isFavorite = false)))
    server.enqueue(MockResponse().setBody(assetJson(isFavorite = true)))
    val viewModel = AssetDetailViewModel(repository, settingsPrimed(), "a1")

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }
    waitForText("♡")

    composeRule.onNodeWithText("♡").performClick()

    waitForText("♥")
    server.takeRequest() // GET /assets/a1
    val putRequest = server.takeRequest()
    assertEquals("PUT", putRequest.method)
    assertEquals("""{"isFavorite":true}""", putRequest.body.readUtf8())
  }

  @Test
  fun `a video asset shows the unsupported-playback message`() {
    server.enqueue(MockResponse().setBody(assetJson(type = "VIDEO")))
    val viewModel = AssetDetailViewModel(repository, settingsPrimed(), "a1")

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }

    waitForText(string(R.string.asset_detail_video_unsupported))
  }

  @Test
  fun `a load failure shows an error with a go-to-settings option`() {
    server.enqueue(MockResponse().setResponseCode(401))
    val viewModel = AssetDetailViewModel(repository, settingsPrimed(), "a1")

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }

    waitForText(string(R.string.error_unauthorized))
  }
}
