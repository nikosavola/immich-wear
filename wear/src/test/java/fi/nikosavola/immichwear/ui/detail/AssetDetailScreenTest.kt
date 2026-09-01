package fi.nikosavola.immichwear.ui.detail

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.data.api.createImmichClients
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.AssetTypeEnum
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

// A fetchPage fake that returns no siblings, forcing AssetDetailViewModel to fall back to fetching
// just the single requested asset - the scenario for tests unrelated to next/previous paging.
private val noSiblings: suspend (Int?) -> ImmichResult<TimelinePage> = {
  ImmichResult.Success(TimelinePage(items = emptyList(), nextPage = null))
}

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

  private fun string(@StringRes resId: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId)

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

  private fun assetJson(isFavorite: Boolean = false, type: String = "IMAGE") =
    """{"id": "a1", "type": "$type", "originalFileName": "a1.jpg", "isFavorite": $isFavorite,""" +
      """ "localDateTime": "2026-01-01T00:00:00Z"}"""

  private fun asset(id: String) =
    AssetDto(
      id = id,
      type = AssetTypeEnum.IMAGE,
      originalFileName = "$id.jpg",
      localDateTime = "2026-01-01T00:00:00Z",
    )

  @Test
  fun `swiping left over the photo reveals the details panel with the favorite toggle`() {
    server.enqueue(MockResponse().setBody(assetJson(isFavorite = false)))
    server.enqueue(MockResponse().setBody(""))
    val viewModel = AssetDetailViewModel(repository, "a1", noSiblings)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }
    waitForContentDescription("a1.jpg")

    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { swipeLeft() }
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
    val viewModel = AssetDetailViewModel(repository, "a1", noSiblings)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }

    waitForText(string(R.string.asset_detail_video_unsupported))
  }

  @Test
  fun `a load failure shows an error with a go-to-settings option`() {
    server.enqueue(MockResponse().setResponseCode(401))
    val viewModel = AssetDetailViewModel(repository, "a1", noSiblings)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }

    waitForText(string(R.string.error_unauthorized))
  }

  @Test
  fun `swiping up moves to the next sibling asset`() {
    val fetch: suspend (Int?) -> ImmichResult<TimelinePage> = {
      ImmichResult.Success(TimelinePage(items = listOf(asset("a1"), asset("a2")), nextPage = null))
    }
    server.enqueue(MockResponse().setBody(assetJson())) // exif-enrichment GET for the found asset
    val viewModel = AssetDetailViewModel(repository, "a1", fetch)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }
    waitForContentDescription("a1.jpg")

    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { swipeUp() }

    waitForContentDescription("a2.jpg")
  }

  @Test
  fun `swiping down at the first asset stays on it`() {
    val fetch: suspend (Int?) -> ImmichResult<TimelinePage> = {
      ImmichResult.Success(TimelinePage(items = listOf(asset("a1"), asset("a2")), nextPage = null))
    }
    server.enqueue(MockResponse().setBody(assetJson())) // exif-enrichment GET for the found asset
    val viewModel = AssetDetailViewModel(repository, "a1", fetch)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }
    waitForContentDescription("a1.jpg")

    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { swipeDown() }

    waitForContentDescription("a1.jpg")
  }

  @Test
  fun `opening an asset found among its siblings still enriches it with EXIF details`() {
    // The search/metadata response used to locate siblings never carries exifInfo - only the
    // single-asset GET this enqueues does. Regression test for that enrichment fetch.
    val fetch: suspend (Int?) -> ImmichResult<TimelinePage> = {
      ImmichResult.Success(TimelinePage(items = listOf(asset("a1")), nextPage = null))
    }
    server.enqueue(
      MockResponse()
        .setBody(
          """{"id": "a1", "type": "IMAGE", "originalFileName": "a1.jpg", "isFavorite": false,""" +
            """ "localDateTime": "2026-01-01T00:00:00Z",""" +
            """ "exifInfo": {"make": "Fujifilm", "model": "X-T5"}}"""
        )
    )
    val viewModel = AssetDetailViewModel(repository, "a1", fetch)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }
    waitForContentDescription("a1.jpg")

    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { swipeLeft() }

    waitForText("Fujifilm X-T5")
  }

  @Test
  fun `an asset missing from the first page falls back to fetching it directly, without paging further`() {
    var fetchCalls = 0
    val fetch: suspend (Int?) -> ImmichResult<TimelinePage> = {
      fetchCalls++
      ImmichResult.Success(TimelinePage(items = listOf(asset("other")), nextPage = 2))
    }
    server.enqueue(MockResponse().setBody(assetJson())) // the single-asset fallback fetch
    val viewModel = AssetDetailViewModel(repository, "a1", fetch)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }
    waitForContentDescription("a1.jpg")

    assertEquals(1, fetchCalls)
  }

  @Test
  fun `double-tapping the photo zooms in, and double-tapping again zooms back out`() {
    val fetch: suspend (Int?) -> ImmichResult<TimelinePage> = {
      ImmichResult.Success(TimelinePage(items = listOf(asset("a1"), asset("a2")), nextPage = null))
    }
    server.enqueue(MockResponse().setBody(assetJson())) // exif-enrichment GET for the found asset
    val viewModel = AssetDetailViewModel(repository, "a1", fetch)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }
    waitForContentDescription("a1.jpg")

    // Zoomed in: a single-finger swipe up should pan the photo, not advance to the next one.
    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { doubleClick() }
    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { swipeUp() }
    waitForContentDescription("a1.jpg")

    // Double-tapping again zooms back to 1x, so the same swipe now advances to the next photo.
    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { doubleClick() }
    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { swipeUp() }
    waitForContentDescription("a2.jpg")
  }

  @Test
  fun `pinching to zoom in consumes a following swipe as pan, not next-photo navigation`() {
    val fetch: suspend (Int?) -> ImmichResult<TimelinePage> = {
      ImmichResult.Success(TimelinePage(items = listOf(asset("a1"), asset("a2")), nextPage = null))
    }
    server.enqueue(MockResponse().setBody(assetJson())) // exif-enrichment GET for the found asset
    val viewModel = AssetDetailViewModel(repository, "a1", fetch)

    composeRule.setContent { AssetDetailScreen(viewModel = viewModel, onNavigateToSettings = {}) }
    waitForContentDescription("a1.jpg")

    // Two-finger pinch-out: start close together at the center, spread apart to zoom in.
    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput {
      down(0, center - Offset(20f, 0f))
      down(1, center + Offset(20f, 0f))
      moveTo(0, center - Offset(150f, 0f))
      moveTo(1, center + Offset(150f, 0f))
      up(0)
      up(1)
    }

    // Now zoomed in: a single-finger swipe up should pan the photo, not advance to the next one.
    composeRule.onNodeWithContentDescription("a1.jpg").performTouchInput { swipeUp() }

    waitForContentDescription("a1.jpg")
  }
}
