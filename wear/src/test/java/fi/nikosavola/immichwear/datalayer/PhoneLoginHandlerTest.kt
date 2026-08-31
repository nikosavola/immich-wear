package fi.nikosavola.immichwear.datalayer

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.api.createImmichClients
import fi.nikosavola.immichwear.data.api.immichJson
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val EMAIL = "user@example.com"

@RunWith(RobolectricTestRunner::class)
class PhoneLoginHandlerTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var server: MockWebServer
  private lateinit var settingsStore: SettingsStore
  private lateinit var repository: ImmichRepository
  private lateinit var handler: PhoneLoginHandler

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
    handler = PhoneLoginHandler(repository, ApplicationProvider.getApplicationContext<Context>())
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun request(serverUrl: String, apiKey: String) =
    immichJson
      .encodeToString(
        LoginRequest.serializer(),
        LoginRequest(serverUrl = serverUrl, apiKey = apiKey),
      )
      .encodeToByteArray()

  private fun result(payload: ByteArray) =
    immichJson.decodeFromString(LoginResult.serializer(), payload.decodeToString())

  @Test
  fun `a valid request connects and replies with success and library stats`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    server.enqueue(MockResponse().setBody("""{"total": 7, "images": 5, "videos": 2}"""))

    val reply = result(handler.handle(request(server.url("/").toString(), "phone-key")))

    assertTrue(reply.success)
    assertNull(reply.errorMessage)
    assertEquals(LoginStats(total = 7, images = 5, videos = 2), reply.stats)
    assertEquals(server.url("/").toString(), settingsStore.currentSettings().serverUrl)
  }

  @Test
  fun `a failed stats fetch still replies with success, just without stats`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    server.enqueue(MockResponse().setResponseCode(500))

    val reply = result(handler.handle(request(server.url("/").toString(), "phone-key")))

    assertTrue(reply.success)
    assertNull(reply.stats)
  }

  @Test
  fun `a rejected key replies with failure and does not persist credentials`() = runTest {
    server.enqueue(MockResponse().setResponseCode(401))

    val reply = result(handler.handle(request(server.url("/").toString(), "wrong-key")))

    assertFalse(reply.success)
    assertEquals("Invalid API key. Please connect again.", reply.errorMessage)
    assertNull(settingsStore.currentSettings().apiKey)
  }

  @Test
  fun `a malformed payload replies with failure instead of throwing`() = runTest {
    val reply = result(handler.handle("not json".encodeToByteArray()))

    assertFalse(reply.success)
  }
}
