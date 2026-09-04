package fi.nikosavola.immichwear.ui.timeline

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.Text
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.data.FileAssetCache
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

private const val API_KEY = "test-api-key"
private const val WAIT_TIMEOUT_MS = 5_000L
private const val CLICKED_MARKER = "clicked-marker"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimelineScreenTest {
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

  private fun connect() = runBlocking {
    settingsStore.setServerUrl(server.url("/").toString())
    settingsStore.setApiKey(API_KEY)
  }

  private fun string(@StringRes resId: Int, vararg formatArgs: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId, *formatArgs)

  private fun waitForText(text: String) {
    composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForContentDescription(description: String) {
    composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
      composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun assetJson(id: String, type: String = "IMAGE") =
    """{"id": "$id", "type": "$type", "originalFileName": "$id.jpg",""" +
      """ "localDateTime": "2026-01-01T00:00:00Z"}"""

  @Test
  fun `an empty timeline shows the empty-state message`() {
    connect()
    server.enqueue(MockResponse().setBody("""{"assets": {"items": [], "nextPage": null}}"""))
    val viewModel = PagedAssetsViewModel(repository::timeline)

    composeRule.setContent {
      TimelineScreen(viewModel = viewModel, onAssetClick = {}, onNavigateToSettings = {})
    }

    waitForText(string(R.string.timeline_empty))
  }

  @Test
  fun `a load failure on the first page shows an error with a retry button`() {
    connect()
    server.enqueue(MockResponse().setResponseCode(500))
    val viewModel = PagedAssetsViewModel(repository::timeline)

    composeRule.setContent {
      TimelineScreen(viewModel = viewModel, onAssetClick = {}, onNavigateToSettings = {})
    }

    waitForText(string(R.string.error_http, 500))
    assertTrue(
      composeRule
        .onAllNodesWithText(string(R.string.retry_button))
        .fetchSemanticsNodes()
        .isNotEmpty()
    )
  }

  @Test
  fun `tapping a thumbnail invokes onAssetClick with its id`() {
    connect()
    server.enqueue(
      MockResponse()
        .setBody("""{"assets": {"items": [${assetJson("asset-1")}], "nextPage": null}}""")
    )
    val viewModel = PagedAssetsViewModel(repository::timeline)
    var clickedAssetId by mutableStateOf<String?>(null)

    composeRule.setContent {
      TimelineScreen(
        viewModel = viewModel,
        onAssetClick = { clickedAssetId = it },
        onNavigateToSettings = {},
      )
      if (clickedAssetId != null) Text(text = CLICKED_MARKER)
    }
    waitForContentDescription("asset-1.jpg")

    // The grid has no accessible text per cell, so this locates the thumbnail via its content
    // description instead (set to the asset's file name in AssetThumbnail).
    composeRule.onNodeWithContentDescription("asset-1.jpg").performClick()

    waitForText(CLICKED_MARKER)
    assertTrue(clickedAssetId == "asset-1")
  }

  @Test
  fun `a load-more button appears when a next page exists, and loads it on click`() {
    connect()
    server.enqueue(
      MockResponse()
        .setBody("""{"assets": {"items": [${assetJson("asset-1")}], "nextPage": "2"}}""")
    )
    server.enqueue(
      MockResponse()
        .setBody("""{"assets": {"items": [${assetJson("asset-2")}], "nextPage": null}}""")
    )
    val viewModel = PagedAssetsViewModel(repository::timeline)

    composeRule.setContent {
      TimelineScreen(viewModel = viewModel, onAssetClick = {}, onNavigateToSettings = {})
    }
    waitForText(string(R.string.timeline_load_more_button))

    composeRule.onNodeWithText(string(R.string.timeline_load_more_button)).performClick()

    waitForContentDescription("asset-2.jpg")
  }

  @Test
  fun `an offline fallback shows the cached-photos banner, and tapping it retries the fetch`() {
    connect()
    // A separate, cache-backed repository: the shared `repository` from setUp() uses the default
    // NoOpAssetCache, since most tests here don't care about caching.
    val cachedRepository =
      ImmichRepository(
        createImmichClients(
            apiKey = settingsStore.apiKeySupplier,
            serverBaseUrl = settingsStore.serverUrlSupplier,
          )
          .api,
        settingsStore,
        FileAssetCache(tempFolder.newFolder()),
      )
    server.enqueue(
      MockResponse()
        .setBody("""{"assets": {"items": [${assetJson("asset-1")}], "nextPage": null}}""")
    )
    runBlocking { cachedRepository.timeline() } // populates the cache
    // A 500 (looksOffline treats 5xx like a transport failure - see ImmichRepository) rather than
    // disconnecting the socket or shutting the server down: those leave the client's retry/timeout
    // behavior to chase, whereas an HTTP error response is immediate and deterministic, and the
    // server stays alive to serve the retry below.
    server.enqueue(MockResponse().setResponseCode(500))
    val viewModel = PagedAssetsViewModel(cachedRepository::timeline)

    composeRule.setContent {
      TimelineScreen(viewModel = viewModel, onAssetClick = {}, onNavigateToSettings = {})
    }
    waitForText(string(R.string.asset_grid_offline_cached))
    // The cached photo itself is still shown alongside the banner, not replaced by an error state.
    waitForContentDescription("asset-1.jpg")

    server.enqueue(
      MockResponse()
        .setBody(
          """{"assets": {"items": [${assetJson("asset-1")}, ${assetJson("asset-2")}],""" +
            """ "nextPage": null}}"""
        )
    )
    composeRule.onNodeWithText(string(R.string.asset_grid_offline_cached)).performClick()

    // A fresh, non-cached fetch succeeded: the banner is gone and the new item is visible.
    waitForContentDescription("asset-2.jpg")
    assertTrue(
      composeRule
        .onAllNodesWithText(string(R.string.asset_grid_offline_cached))
        .fetchSemanticsNodes()
        .isEmpty()
    )
  }
}
