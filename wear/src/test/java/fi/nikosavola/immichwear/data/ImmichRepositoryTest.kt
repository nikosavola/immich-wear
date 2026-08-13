package fi.nikosavola.immichwear.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fi.nikosavola.immichwear.data.api.createImmichClients
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val API_KEY = "test-api-key"
private const val EMAIL = "user@example.com"

@RunWith(RobolectricTestRunner::class)
class ImmichRepositoryTest {
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

  @Test
  fun `connect validates and persists the server url and api key`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))

    val result = repository.connect(server.url("/").toString(), API_KEY)

    assertTrue(result is ImmichResult.Success)
    val recorded = server.takeRequest()
    assertEquals("/api/users/me", recorded.path)
    assertEquals(API_KEY, recorded.getHeader("x-api-key"))

    val settings = settingsStore.currentSettings()
    assertEquals(server.url("/").toString(), settings.serverUrl)
    assertEquals(API_KEY, settings.apiKey)
    assertEquals(EMAIL, settings.email)
  }

  @Test
  fun `connect with an invalid server url fails without touching the network`() = runTest {
    val result = repository.connect("not a url at all", API_KEY)

    assertEquals(ImmichResult.Failure(ImmichError.InvalidServerUrl), result)
    assertEquals(0, server.requestCount)
    assertNull(settingsStore.currentSettings().serverUrl)
  }

  @Test
  fun `connect with a rejected key does not leave credentials persisted`() = runTest {
    server.enqueue(MockResponse().setResponseCode(401))

    val result = repository.connect(server.url("/").toString(), "wrong-key")

    assertEquals(ImmichResult.Failure(ImmichError.Unauthorized), result)
    val settings = settingsStore.currentSettings()
    assertNull(settings.serverUrl)
    assertNull(settings.apiKey)
  }

  @Test
  fun `signOut clears every persisted field`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)

    repository.signOut()

    val settings = settingsStore.currentSettings()
    assertNull(settings.serverUrl)
    assertNull(settings.apiKey)
    assertNull(settings.email)
  }
}
