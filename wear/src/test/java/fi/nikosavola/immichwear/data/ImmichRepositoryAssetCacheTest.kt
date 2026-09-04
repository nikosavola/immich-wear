package fi.nikosavola.immichwear.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fi.nikosavola.immichwear.data.api.createImmichClients
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

private const val API_KEY = "test-api-key"
private const val EMAIL = "user@example.com"

/**
 * [ImmichRepository]'s offline-cache behavior specifically - see [ImmichRepositoryTest] for
 * everything else, which uses the default [NoOpAssetCache] and is unaffected by this.
 */
@RunWith(RobolectricTestRunner::class)
class ImmichRepositoryAssetCacheTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var server: MockWebServer
  private lateinit var settingsStore: SettingsStore
  private lateinit var assetCache: AssetCache
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
    assetCache = FileAssetCache(tempFolder.newFolder())
    val clients =
      createImmichClients(
        apiKey = settingsStore.apiKeySupplier,
        serverBaseUrl = settingsStore.serverUrlSupplier,
      )
    repository = ImmichRepository(clients.api, settingsStore, assetCache)
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private suspend fun connect() {
    server.enqueue(MockResponse().setBody("""{"id": "u1", "email": "$EMAIL"}"""))
    repository.connect(server.url("/").toString(), API_KEY)
    server.takeRequest()
  }

  private fun assetsPage(vararg ids: String): MockResponse {
    val items =
      ids.joinToString(",") {
        """{"id": "$it", "type": "IMAGE", "originalFileName": "$it.jpg",""" +
          """ "localDateTime": "2026-01-01T00:00:00Z"}"""
      }
    return MockResponse().setBody("""{"assets": {"items": [$items], "nextPage": null}}""")
  }

  @Test
  fun `a successful timeline fetch is not marked as from cache`() = runTest {
    connect()
    server.enqueue(assetsPage("a1"))

    val result = repository.timeline() as ImmichResult.Success

    assertFalse(result.fromCache)
  }

  @Test
  fun `timeline falls back to the cache when offline`() = runTest {
    connect()
    server.enqueue(assetsPage("a1", "a2"))
    repository.timeline() // populates the cache
    server.shutdown() // every further request now fails at the transport layer, like being offline

    val result = repository.timeline()

    assertTrue(result is ImmichResult.Success)
    val page = (result as ImmichResult.Success).value
    assertEquals(listOf("a1", "a2"), page.items.map { it.id })
    assertNull(page.nextPage)
    assertTrue(result.fromCache)
  }

  @Test
  fun `favorites falls back to its own cache, not timeline's`() = runTest {
    connect()
    server.enqueue(assetsPage("timeline-1"))
    repository.timeline()
    server.enqueue(assetsPage("fav-1"))
    repository.favorites()
    server.shutdown()

    val result = repository.favorites()

    val page = (result as ImmichResult.Success).value
    assertEquals(listOf("fav-1"), page.items.map { it.id })
  }

  @Test
  fun `timeline without a prior successful fetch has nothing to fall back to`() = runTest {
    connect()
    server.shutdown()

    val result = repository.timeline()

    assertEquals(ImmichResult.Failure(ImmichError.Offline), result)
  }

  @Test
  fun `a load-more page failure does not fall back to the cached first page`() = runTest {
    connect()
    server.enqueue(MockResponse().setBody("""{"assets": {"items": [], "nextPage": "2"}}"""))
    repository.timeline() // page 1
    server.shutdown()

    val result = repository.timeline(page = 2)

    assertEquals(ImmichResult.Failure(ImmichError.Offline), result)
  }

  @Test
  fun `an empty first page is not cached, so an account with nothing to show still reports offline`() =
    runTest {
      connect()
      server.enqueue(MockResponse().setBody("""{"assets": {"items": [], "nextPage": null}}"""))
      repository.favorites() // e.g. an account with zero favorites
      server.shutdown()

      val result = repository.favorites()

      // Not a "successful" empty list: that would hide both the offline indicator and the retry
      // button a real failure gets, behind what looks like a normal empty state.
      assertEquals(ImmichResult.Failure(ImmichError.Offline), result)
    }

  @Test
  fun `a live empty fetch invalidates a previously cached non-empty page`() = runTest {
    connect()
    server.enqueue(assetsPage("fav-1")) // e.g. one favorite, cached
    repository.favorites()
    // The user then un-favorites it - the server now authoritatively reports zero favorites.
    server.enqueue(MockResponse().setBody("""{"assets": {"items": [], "nextPage": null}}"""))
    repository.favorites()
    server.shutdown()

    val result = repository.favorites()

    // Must not resurrect "fav-1" from the stale cache: the live empty answer already invalidated
    // it, so this is a real (if unlikely, immediately-offline) failure, not a fallback.
    assertEquals(ImmichResult.Failure(ImmichError.Offline), result)
  }

  @Test
  fun `a 5xx response also falls back to the cache, like being offline`() = runTest {
    connect()
    server.enqueue(assetsPage("a1"))
    repository.timeline()
    server.enqueue(MockResponse().setResponseCode(503))

    val result = repository.timeline()

    assertTrue(result is ImmichResult.Success)
    assertTrue((result as ImmichResult.Success).fromCache)
  }

  @Test
  fun `a 4xx response does not fall back to the cache`() = runTest {
    connect()
    server.enqueue(assetsPage("a1"))
    repository.timeline()
    server.enqueue(MockResponse().setResponseCode(404))

    val result = repository.timeline()

    assertEquals(ImmichResult.Failure(ImmichError.Http(404)), result)
  }

  // A test for "a sign-out mid-fetch doesn't let that fetch's response overwrite the cache after
  // clearCache() ran" was deliberately not added: reliably forcing that interleaving against
  // Robolectric's coroutine test dispatcher (launch{} vs. MockWebServer's real request timing)
  // proved too fragile to land without either changing production code purely for testability or
  // accepting a slow, still-not-fully-deterministic real-time race in the test itself. The fix
  // (ImmichRepository.cacheGeneration, captured before fetch() and re-checked before save()) is a
  // standard compare-and-skip guard against exactly this race - see its kdoc.

  @Test
  fun `signOut clears the cache`() = runTest {
    connect()
    server.enqueue(assetsPage("a1"))
    repository.timeline()

    repository.signOut()

    assertNull(assetCache.load("timeline"))
  }

  @Test
  fun `connecting to a server clears any cache left over from a previous account`() = runTest {
    connect()
    server.enqueue(assetsPage("a1"))
    repository.timeline()
    assertEquals(listOf("a1"), assetCache.load("timeline")?.map { it.id })

    connect() // simulates signing into a different (or the same) server again

    assertNull(assetCache.load("timeline"))
  }
}
