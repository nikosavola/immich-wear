package fi.nikosavola.immichwear.ui.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.api.createImmichClients
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

private const val TIMEOUT_MS = 5_000L
private const val SETTLE_MS = 300L

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var server: MockWebServer
  private lateinit var settingsStore: SettingsStore
  private lateinit var repository: ImmichRepository

  // Opened by the test to let the gated memories responses through.
  private val memoriesGate = CountDownLatch(1)
  private val memoriesRequests = AtomicInteger()
  private val memoriesArrived = CountDownLatch(2)

  @Before
  fun setUp() {
    // The ViewModel's work resumes from OkHttp threads, so avoid a looper-bound Main here.
    Dispatchers.setMain(Dispatchers.Default)
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
    memoriesGate.countDown()
    server.shutdown()
    Dispatchers.resetMain()
  }

  private fun heroBody(id: String) =
    """{"assets": {"items": [{"id": "$id", "type": "IMAGE", "originalFileName": "$id.jpg",""" +
      """ "isFavorite": false, "localDateTime": "2026-01-01T00:00:00Z"}], "nextPage": null}}"""

  private fun memoriesBody(id: String) =
    """[{"data": {"year": 2020}, "assets": [{"id": "$id", "type": "IMAGE",""" +
      """ "originalFileName": "$id.jpg", "isFavorite": false,""" +
      """ "localDateTime": "2020-01-01T00:00:00Z"}]}]"""

  // The hero id and memory id are derived from the api key header, so a response can be traced to
  // the settings generation that requested it. Memories block on the gate until the test opens it.
  private fun serve() {
    server.dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
          val key = request.getHeader("x-api-key").orEmpty()
          return when {
            request.path == "/api/search/metadata" -> {
              MockResponse().setBody(heroBody("hero-$key"))
            }
            request.path?.startsWith("/api/memories") == true -> {
              memoriesRequests.incrementAndGet()
              memoriesArrived.countDown()
              memoriesGate.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
              MockResponse().setBody(memoriesBody("memory-$key"))
            }
            else -> {
              MockResponse().setResponseCode(404)
            }
          }
        }
      }
  }

  private fun connect(apiKey: String) = runBlocking {
    settingsStore.setServerUrl(server.url("/").toString())
    settingsStore.setApiKey(apiKey)
  }

  @Test
  fun `hero is published while the memories request is still in flight`() = runBlocking {
    serve()
    connect("k1")
    val viewModel = HomeViewModel(settingsStore, repository)

    val state = withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.hasHero() } }

    assertEquals("hero-k1", (state as HomeUiState.Connected).heroAssetId)
    assertNull(state.memory)
    assertEquals(1, memoriesGate.count)

    memoriesGate.countDown()
    val full = withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.hasMemory() } }
    assertEquals("hero-k1", (full as HomeUiState.Connected).heroAssetId)
    assertEquals("memory-k1", full.memory?.assetId)
  }

  @Test
  fun `a settings change cancels in-flight work and drops its late results`() = runBlocking {
    serve()
    connect("k1")
    val viewModel = HomeViewModel(settingsStore, repository)
    // The first generation's memories request is now blocked at the server.
    withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.hasHero() } }
    assertTrue(memoriesRequests.get() >= 1)

    settingsStore.setApiKey("k2")
    // Second generation's memories request has arrived too, so the first one is truly in flight.
    assertTrue(memoriesArrived.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
    withTimeout(TIMEOUT_MS) {
      viewModel.uiState.first { (it as? HomeUiState.Connected)?.heroAssetId == "hero-k2" }
    }

    memoriesGate.countDown()
    withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.hasMemory() } }
    // Give a wrongly surviving k1 fetch time to land; it must not overwrite or appear.
    Thread.sleep(SETTLE_MS)

    val state = viewModel.uiState.value as HomeUiState.Connected
    assertEquals("hero-k2", state.heroAssetId)
    assertEquals("memory-k2", state.memory?.assetId)
  }

  private fun HomeUiState.hasHero() = (this as? HomeUiState.Connected)?.heroAssetId != null

  private fun HomeUiState.hasMemory() = (this as? HomeUiState.Connected)?.memory != null
}
