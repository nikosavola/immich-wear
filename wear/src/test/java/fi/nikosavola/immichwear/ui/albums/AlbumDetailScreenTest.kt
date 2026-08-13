package fi.nikosavola.immichwear.ui.albums

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.api.createImmichClients
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
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
class AlbumDetailScreenTest {
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

  /**
   * AlbumDetailViewModel fires the assets search and the album-metadata fetch concurrently from
   * init{}, so pairing them with plain FIFO `enqueue()` calls (as most other tests do) is a race:
   * whichever request happens to arrive first gets whichever response was enqueued first,
   * regardless of which endpoint it actually hit. Routing by path removes that race.
   */
  private fun routeByPath(assetsBody: String, albumBody: String) {
    server.dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
          if (request.path == "/api/search/metadata") {
            MockResponse().setBody(assetsBody)
          } else {
            MockResponse().setBody(albumBody)
          }
      }
  }

  @Test
  fun `the header shows the album name once it loads, alongside its assets`() {
    routeByPath(
      assetsBody =
        """{"assets": {"items": [{"id": "a1", "type": "IMAGE", "originalFileName": "a1.jpg",""" +
          """ "localDateTime": "2026-01-01T00:00:00Z"}], "nextPage": null}}""",
      albumBody = """{"id": "al1", "albumName": "Vacation", "assetCount": 1}""",
    )
    val viewModel = AlbumDetailViewModel(repository, settingsPrimed(), "al1")

    composeRule.setContent {
      AlbumDetailScreen(viewModel = viewModel, onAssetClick = {}, onNavigateToSettings = {})
    }

    waitForText("Vacation")
  }

  @Test
  fun `an empty album shows the album-specific empty-state message`() {
    routeByPath(
      assetsBody = """{"assets": {"items": [], "nextPage": null}}""",
      albumBody = """{"id": "al1", "albumName": "Vacation", "assetCount": 0}""",
    )
    val viewModel = AlbumDetailViewModel(repository, settingsPrimed(), "al1")

    composeRule.setContent {
      AlbumDetailScreen(viewModel = viewModel, onAssetClick = {}, onNavigateToSettings = {})
    }

    waitForText(string(R.string.albums_detail_empty))
  }
}
