package fi.nikosavola.immichwear.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fi.nikosavola.immichwear.data.api.createImmichClients
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
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
  fun `connect trims a clipboard-pasted api key before persisting or sending it`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))

    repository.connect(server.url("/").toString(), "$API_KEY\n")

    assertEquals(API_KEY, server.takeRequest().getHeader("x-api-key"))
    assertEquals(API_KEY, settingsStore.currentSettings().apiKey)
  }

  @Test
  fun `cancelling connect while validating rolls back the persisted credentials`() = runTest {
    server.enqueue(
      MockResponse()
        .setBody("""{"id": "u1", "email": "$EMAIL"}""")
        .setBodyDelay(10, TimeUnit.SECONDS)
    )

    val job = launch { repository.connect(server.url("/").toString(), API_KEY) }
    // Give the request time to actually leave (assert it did), then simulate the Settings screen
    // being swipe-dismissed while validation is still in flight.
    server.takeRequest(2, TimeUnit.SECONDS)
    job.cancel()
    job.join()

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

  @Test
  fun `timeline fails with NotConfigured before a server is connected`() = runTest {
    val result = repository.timeline()

    assertEquals(ImmichResult.Failure(ImmichError.NotConfigured), result)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `timeline posts the page size and order, and parses nextPage as an int`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest() // the connect() request; not under test here.
    server.enqueue(
      MockResponse()
        .setBody(
          """{"assets": {"items": [{"id": "a1", "type": "IMAGE", "originalFileName": "a1.jpg",""" +
            """ "localDateTime": "2026-01-01T00:00:00Z"}], "nextPage": "2"}}"""
        )
    )

    val result = repository.timeline()

    assertTrue(result is ImmichResult.Success)
    val page = (result as ImmichResult.Success).value
    assertEquals(1, page.items.size)
    assertEquals("a1", page.items[0].id)
    assertEquals(2, page.nextPage)
    assertEquals("/api/search/metadata", server.takeRequest().path)
  }

  @Test
  fun `timeline treats a non-numeric nextPage as the end of the list`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.enqueue(
      MockResponse().setBody("""{"assets": {"items": [], "nextPage": "not-a-number"}}""")
    )

    val result = repository.timeline()

    assertEquals(null, (result as ImmichResult.Success).value.nextPage)
  }

  @Test
  fun `asset fails with NotConfigured before a server is connected`() = runTest {
    val result = repository.asset("a1")

    assertEquals(ImmichResult.Failure(ImmichError.NotConfigured), result)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `asset fetches by id`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
    server.enqueue(
      MockResponse()
        .setBody(
          """{"id": "a1", "type": "IMAGE", "originalFileName": "a1.jpg", "isFavorite": false,""" +
            """ "localDateTime": "2026-01-01T00:00:00Z"}"""
        )
    )

    val result = repository.asset("a1")

    assertTrue(result is ImmichResult.Success)
    assertEquals("a1", (result as ImmichResult.Success).value.id)
    assertEquals("/api/assets/a1", server.takeRequest().path)
  }

  @Test
  fun `setFavorite PUTs isFavorite to the asset endpoint`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
    server.enqueue(MockResponse().setBody(""))

    val result = repository.setFavorite("a1", true)

    assertTrue(result is ImmichResult.Success)
    val recorded = server.takeRequest()
    assertEquals("PUT", recorded.method)
    assertEquals("/api/assets/a1", recorded.path)
    assertEquals("""{"isFavorite":true}""", recorded.body.readUtf8())
  }

  @Test
  fun `albums fails with NotConfigured before a server is connected`() = runTest {
    val result = repository.albums()

    assertEquals(ImmichResult.Failure(ImmichError.NotConfigured), result)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `albums fetches the list`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
    server.enqueue(
      MockResponse().setBody("""[{"id": "al1", "albumName": "Vacation", "assetCount": 3}]""")
    )

    val result = repository.albums()

    assertTrue(result is ImmichResult.Success)
    val albums = (result as ImmichResult.Success).value
    assertEquals(1, albums.size)
    assertEquals("al1", albums[0].id)
    assertEquals("/api/albums", server.takeRequest().path)
  }

  @Test
  fun `album fetches by id`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
    server.enqueue(
      MockResponse().setBody("""{"id": "al1", "albumName": "Vacation", "assetCount": 3}""")
    )

    val result = repository.album("al1")

    assertTrue(result is ImmichResult.Success)
    assertEquals("Vacation", (result as ImmichResult.Success).value.albumName)
    assertEquals("/api/albums/al1", server.takeRequest().path)
  }

  @Test
  fun `albumAssets scopes the search to the given album id`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
    server.enqueue(MockResponse().setBody("""{"assets": {"items": [], "nextPage": null}}"""))

    repository.albumAssets("al1")

    val recorded = server.takeRequest()
    assertEquals("/api/search/metadata", recorded.path)
    assertEquals("""{"size":30,"order":"desc","albumIds":["al1"]}""", recorded.body.readUtf8())
  }

  @Test
  fun `favorites scopes the search to isFavorite`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
    server.enqueue(MockResponse().setBody("""{"assets": {"items": [], "nextPage": null}}"""))

    repository.favorites()

    val recorded = server.takeRequest()
    assertEquals("/api/search/metadata", recorded.path)
    assertEquals("""{"size":30,"order":"desc","isFavorite":true}""", recorded.body.readUtf8())
  }

  @Test
  fun `assetStatistics fails with NotConfigured before a server is connected`() = runTest {
    val result = repository.assetStatistics()

    assertEquals(ImmichResult.Failure(ImmichError.NotConfigured), result)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `assetStatistics parses the photo and video counts`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
    server.enqueue(MockResponse().setBody("""{"total": 42, "images": 30, "videos": 12}"""))

    val result = repository.assetStatistics()

    assertTrue(result is ImmichResult.Success)
    val stats = (result as ImmichResult.Success).value
    assertEquals(42, stats.total)
    assertEquals(30, stats.images)
    assertEquals(12, stats.videos)
    assertEquals("/api/assets/statistics", server.takeRequest().path)
  }

  @Test
  fun `memories fails with NotConfigured before a server is connected`() = runTest {
    val result = repository.memories()

    assertEquals(ImmichResult.Failure(ImmichError.NotConfigured), result)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `memories queries by today's date and parses each year's assets`() = runTest {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
    server.enqueue(
      MockResponse()
        .setBody(
          """[{"data": {"year": 2019}, "assets": [{"id": "a1", "type": "IMAGE",""" +
            """ "originalFileName": "a1.jpg", "localDateTime": "2019-01-01T00:00:00Z"}]}]"""
        )
    )

    val result = repository.memories()

    assertTrue(result is ImmichResult.Success)
    val memories = (result as ImmichResult.Success).value
    assertEquals(1, memories.size)
    assertEquals(2019, memories[0].data.year)
    assertEquals("a1", memories[0].assets[0].id)
    val recorded = server.takeRequest()
    assertEquals("/api/memories", recorded.requestUrl?.encodedPath)
    assertEquals(java.time.LocalDate.now().toString(), recorded.requestUrl?.queryParameter("for"))
  }
}
