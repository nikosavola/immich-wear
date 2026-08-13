package fi.nikosavola.immichwear.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var dataStore: DataStore<Preferences>
  private lateinit var store: SettingsStore

  @Before
  fun setUp() {
    dataStore =
      PreferenceDataStoreFactory.create(
        produceFile = { tempFolder.newFile("settings.preferences_pb") }
      )
    store = SettingsStore(dataStore, FakeApiKeyCipher())
  }

  @Test
  fun `all fields round-trip through settings`() = runTest {
    store.setServerUrl("https://immich.example.com/")
    store.setApiKey("key-1")
    store.setEmail("user@example.com")

    val settings = store.currentSettings()

    assertEquals("https://immich.example.com/", settings.serverUrl)
    assertEquals("key-1", settings.apiKey)
    assertEquals("user@example.com", settings.email)
  }

  @Test
  fun `unset fields default to null`() = runTest {
    val settings = store.currentSettings()

    assertNull(settings.serverUrl)
    assertNull(settings.apiKey)
    assertNull(settings.email)
  }

  @Test
  fun `clear resets every field`() = runTest {
    store.setServerUrl("https://immich.example.com/")
    store.setApiKey("key-1")
    store.setEmail("user@example.com")

    store.clear()

    val settings = store.currentSettings()
    assertNull(settings.serverUrl)
    assertNull(settings.apiKey)
    assertNull(settings.email)
  }

  @Test
  fun `setApiKey with null removes the stored key`() = runTest {
    store.setApiKey("key-1")
    store.setApiKey(null)

    assertNull(store.currentSettings().apiKey)
  }

  @Test
  fun `apiKeySupplier and serverUrlSupplier reflect latest values synchronously`() = runTest {
    assertNull(store.apiKeySupplier())
    assertNull(store.serverUrlSupplier())

    store.setApiKey("key-1")
    store.setServerUrl("https://immich.example.com/")

    // No `.first()`/collection on `settings` happened here: the setters alone must keep the
    // synchronous suppliers current, since that is the whole point of the cache bridge.
    assertEquals("key-1", store.apiKeySupplier())
    assertEquals("https://immich.example.com/", store.serverUrlSupplier())
  }

  @Test
  fun `api key is passed through apiKeyCipher before it reaches the DataStore`() = runTest {
    store.setApiKey("secret-key")

    val raw = dataStore.data.first()[stringPreferencesKey("api_key")]

    assertNotEquals("secret-key", raw)
    assertEquals("secret-key", FakeApiKeyCipher().decrypt(raw!!))
  }

  @Test
  fun `server url is stored in plaintext, not through apiKeyCipher`() = runTest {
    store.setServerUrl("https://immich.example.com/")

    val raw = dataStore.data.first()[stringPreferencesKey("server_url")]

    assertEquals("https://immich.example.com/", raw)
  }

  @Test
  fun `suppliers are primed by reading settings after a cold start`() = runTest {
    // DataStore allows only one live instance per file, so this reuses the same DataStore to
    // simulate the scenario without opening the file a second time: a fresh SettingsStore
    // wrapping already-populated data starts with un-primed in-memory caches until something
    // reads `settings`, exactly as it would after a real process restart.
    val dataStore =
      PreferenceDataStoreFactory.create(
        produceFile = { tempFolder.newFile("restart.preferences_pb") }
      )
    SettingsStore(dataStore, FakeApiKeyCipher()).apply {
      setApiKey("saved-key")
      setServerUrl("https://immich.example.com/")
    }

    val restarted = SettingsStore(dataStore, FakeApiKeyCipher())
    assertNull(restarted.apiKeySupplier())
    assertNull(restarted.serverUrlSupplier())

    assertEquals("saved-key", restarted.currentSettings().apiKey)
    assertEquals("saved-key", restarted.apiKeySupplier())
    assertEquals("https://immich.example.com/", restarted.serverUrlSupplier())
  }
}
