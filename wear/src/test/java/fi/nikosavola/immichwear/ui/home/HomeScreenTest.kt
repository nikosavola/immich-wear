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
import fi.nikosavola.immichwear.data.SettingsStore
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var settingsStore: SettingsStore

  @Before
  fun setUp() {
    settingsStore =
      SettingsStore(
        PreferenceDataStoreFactory.create(
          produceFile = { tempFolder.newFile("settings.preferences_pb") }
        ),
        FakeApiKeyCipher(),
      )
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
    val viewModel = HomeViewModel(settingsStore)
    var navigatedToSettings by mutableStateOf(false)

    composeRule.setContent {
      HomeScreen(
        viewModel = viewModel,
        onNavigateToTimeline = {},
        onNavigateToAlbums = {},
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
      settingsStore.setServerUrl("https://immich.example.com/")
      settingsStore.setApiKey("key")
    }
    val viewModel = HomeViewModel(settingsStore)
    var navigatedTo by mutableStateOf<String?>(null)

    composeRule.setContent {
      HomeScreen(
        viewModel = viewModel,
        onNavigateToTimeline = { navigatedTo = "timeline" },
        onNavigateToAlbums = { navigatedTo = "albums" },
        onNavigateToSettings = { navigatedTo = "settings" },
      )
      if (navigatedTo != null) Text(text = MARKER)
    }
    waitForText(string(R.string.timeline_title))

    composeRule.onNodeWithText(string(R.string.albums_title)).performClick()

    waitForText(MARKER)
    assertTrue(navigatedTo == "albums")
  }
}
