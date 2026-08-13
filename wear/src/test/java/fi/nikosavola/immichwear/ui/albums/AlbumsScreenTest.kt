package fi.nikosavola.immichwear.ui.albums

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.Text
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private const val API_KEY = "test-api-key"
private const val WAIT_TIMEOUT_MS = 5_000L
private const val MARKER = "clicked-marker"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumsScreenTest {
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

  @Test
  fun `an empty album list shows the empty-state message`() {
    server.enqueue(MockResponse().setBody("[]"))
    val viewModel = AlbumsViewModel(repository, settingsPrimed())

    composeRule.setContent {
      AlbumsScreen(viewModel = viewModel, onAlbumClick = {}, onNavigateToSettings = {})
    }

    waitForText(string(R.string.albums_empty))
  }

  @Test
  fun `tapping an album invokes onAlbumClick with its id`() {
    server.enqueue(
      MockResponse().setBody("""[{"id": "al1", "albumName": "Vacation", "assetCount": 3}]""")
    )
    val viewModel = AlbumsViewModel(repository, settingsPrimed())
    var clickedAlbumId by mutableStateOf<String?>(null)

    composeRule.setContent {
      AlbumsScreen(
        viewModel = viewModel,
        onAlbumClick = { clickedAlbumId = it },
        onNavigateToSettings = {},
      )
      if (clickedAlbumId != null) Text(text = MARKER)
    }
    waitForText("Vacation")

    composeRule.onNodeWithText("Vacation").performClick()

    waitForText(MARKER)
    assertTrue(clickedAlbumId == "al1")
  }
}
