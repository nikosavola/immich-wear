package fi.nikosavola.immichwear

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.immichwear.ui.MainActivity
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val API_KEY = "test-api-key"
private const val EMAIL = "e2e@example.invalid"
private const val WAIT_TIMEOUT_MS = 10_000L
private const val POLL_INTERVAL_MS = 50L

/**
 * Drives the real app - real [MainActivity], real nav graph, real ViewModels and repository, real
 * DataStore - against a local [MockWebServer] instead of a live Immich server. The only production
 * seam swapped out is the API key cipher (see [TestImmichApp]): the real Android Keystore provider
 * doesn't exist under Robolectric. Everything else, including typing the mock server's own URL into
 * the Settings form, is exactly what a real user does.
 *
 * Runs as a plain JVM unit test (`./gradlew testDebugUnitTest`) - no emulator, so it's fast enough
 * to run on every CI push.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestImmichApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EndToEndFlowTest {
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun string(@StringRes resId: Int, vararg formatArgs: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId, *formatArgs)

  /**
   * A hand-rolled poll instead of the built-in `composeRule.waitUntil`: the network call behind
   * each step here completes on a real OkHttp thread and resumes the ViewModel's coroutine on a
   * real Robolectric-shadowed main looper, and `waitUntil`'s own idling doesn't reliably drain that
   * looper when the test host is a real launched Activity (as opposed to bare `setContent`).
   * Interleaving a real sleep with an explicit `waitForIdle()` does.
   */
  private fun waitUntilTrue(description: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      composeRule.waitForIdle()
      if (condition()) return
      Thread.sleep(POLL_INTERVAL_MS)
    }
    error("Timed out waiting for: $description")
  }

  private fun waitForText(text: String) =
    waitUntilTrue("text \"$text\"") {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

  private fun waitForContentDescription(description: String) =
    waitUntilTrue("content description \"$description\"") {
      composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }

  @Test
  fun `connect, browse the timeline, open an asset, and favorite it`() {
    // Home starts signed out.
    waitForText(string(R.string.home_not_connected))

    // Home -> Settings, fill in the mock server's own address and a fake key, connect.
    composeRule.onNodeWithText(string(R.string.settings_connect_button)).performClick()
    waitForText(string(R.string.settings_server_url_label))
    val fields = composeRule.onAllNodes(hasSetTextAction())
    fields[0].performTextInput(server.url("/").toString())
    fields[1].performTextInput(API_KEY)
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    composeRule.onNodeWithText(string(R.string.settings_connect_button)).performClick()
    waitForText(string(R.string.settings_signed_in_account, EMAIL))

    // Back to Home, now showing the connected menu.
    composeRule.activity.onBackPressedDispatcher.onBackPressed()
    waitForText(string(R.string.timeline_title))

    // Home -> Timeline, one asset.
    server.enqueue(
      MockResponse()
        .setBody(
          """{"assets": {"items": [{"id": "asset-1", "type": "IMAGE",""" +
            """ "originalFileName": "asset-1.jpg", "isFavorite": false,""" +
            """ "localDateTime": "2026-01-01T00:00:00Z"}], "nextPage": null}}"""
        )
    )
    composeRule.onNodeWithText(string(R.string.timeline_title)).performClick()
    waitForContentDescription("asset-1.jpg")

    // Timeline -> AssetDetail.
    server.enqueue(
      MockResponse()
        .setBody(
          """{"id": "asset-1", "type": "IMAGE", "originalFileName": "asset-1.jpg",""" +
            """ "isFavorite": false, "localDateTime": "2026-01-01T00:00:00Z"}"""
        )
    )
    composeRule.onNodeWithContentDescription("asset-1.jpg").performClick()
    waitForText("♡")

    // Toggle favorite.
    server.enqueue(
      MockResponse()
        .setBody(
          """{"id": "asset-1", "type": "IMAGE", "originalFileName": "asset-1.jpg",""" +
            """ "isFavorite": true, "localDateTime": "2026-01-01T00:00:00Z"}"""
        )
    )
    composeRule.onNodeWithText("♡").performClick()

    waitForText("♥")
  }
}
