package fi.nikosavola.immichwear.ui.home

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

private const val WAIT_TIMEOUT_MS = 5_000L
private const val MARKER = "nav-marker"
private const val NO_ASSETS_RESPONSE = """{"assets": {"items": [], "nextPage": null}}"""

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
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

  private fun string(@StringRes resId: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId)

  private fun waitForText(text: String) {
    composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun `not connected shows a prompt and connect button navigating to settings`() {
    val viewModel = HomeViewModel(settingsStore, repository)
    var navigatedToSettings by mutableStateOf(false)

    composeRule.setContent {
      HomeScreen(
        viewModel = viewModel,
        onNavigateToTimeline = {},
        onNavigateToAlbums = {},
        onNavigateToFavorites = {},
        onNavigateToSettings = { navigatedToSettings = true },
      )
      if (navigatedToSettings) Text(text = MARKER)
    }
    waitForText(string(R.string.home_not_connected))

    composeRule.onNodeWithText(string(R.string.settings_connect_button)).performClick()

    waitForText(MARKER)
    assertTrue(navigatedToSettings)
  }

  @Test
  fun `connected shows Recent photos and Albums rows that navigate`() {
    runBlocking {
      settingsStore.setServerUrl(server.url("/").toString())
      settingsStore.setApiKey("key")
    }
    server.enqueue(MockResponse().setBody(NO_ASSETS_RESPONSE)) // hero-photo fetch, empty library
    val viewModel = HomeViewModel(settingsStore, repository)
    var navigatedTo by mutableStateOf<String?>(null)

    composeRule.setContent {
      HomeScreen(
        viewModel = viewModel,
        onNavigateToTimeline = { navigatedTo = "timeline" },
        onNavigateToAlbums = { navigatedTo = "albums" },
        onNavigateToFavorites = { navigatedTo = "favorites" },
        onNavigateToSettings = { navigatedTo = "settings" },
      )
      if (navigatedTo != null) Text(text = MARKER)
    }
    waitForText(string(R.string.timeline_title))

    composeRule.onNodeWithText(string(R.string.albums_title)).performClick()

    waitForText(MARKER)
    assertTrue(navigatedTo == "albums")
  }

  @Test
  fun `connected shows a Favorites row that navigates`() {
    runBlocking {
      settingsStore.setServerUrl(server.url("/").toString())
      settingsStore.setApiKey("key")
    }
    server.enqueue(MockResponse().setBody(NO_ASSETS_RESPONSE)) // hero-photo fetch, empty library
    val viewModel = HomeViewModel(settingsStore, repository)
    var navigatedTo by mutableStateOf<String?>(null)

    composeRule.setContent {
      HomeScreen(
        viewModel = viewModel,
        onNavigateToTimeline = { navigatedTo = "timeline" },
        onNavigateToAlbums = { navigatedTo = "albums" },
        onNavigateToFavorites = { navigatedTo = "favorites" },
        onNavigateToSettings = { navigatedTo = "settings" },
      )
      if (navigatedTo != null) Text(text = MARKER)
    }
    waitForText(string(R.string.favorites_title))

    composeRule.onNodeWithText(string(R.string.favorites_title)).performClick()

    waitForText(MARKER)
    assertTrue(navigatedTo == "favorites")
  }

  @Test
  fun `connected with a recent photo shows a photo-backed Recent photos card that navigates`() {
    runBlocking {
      settingsStore.setServerUrl(server.url("/").toString())
      settingsStore.setApiKey("key")
    }
    server.enqueue(
      MockResponse()
        .setBody(
          """{"assets": {"items": [{"id": "a1", "type": "IMAGE",""" +
            """ "originalFileName": "a1.jpg", "isFavorite": false,""" +
            """ "localDateTime": "2026-01-01T00:00:00Z"}], "nextPage": null}}"""
        )
    )
    val viewModel = HomeViewModel(settingsStore, repository)
    var navigatedTo by mutableStateOf<String?>(null)

    composeRule.setContent {
      HomeScreen(
        viewModel = viewModel,
        onNavigateToTimeline = { navigatedTo = "timeline" },
        onNavigateToAlbums = { navigatedTo = "albums" },
        onNavigateToFavorites = { navigatedTo = "favorites" },
        onNavigateToSettings = { navigatedTo = "settings" },
      )
      if (navigatedTo != null) Text(text = MARKER)
    }
    waitForText(string(R.string.timeline_title))

    composeRule.onNodeWithText(string(R.string.timeline_title)).performClick()

    waitForText(MARKER)
    assertTrue(navigatedTo == "timeline")
  }
}
