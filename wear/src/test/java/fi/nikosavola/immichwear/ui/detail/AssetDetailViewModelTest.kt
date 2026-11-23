package fi.nikosavola.immichwear.ui.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.data.api.createImmichClients
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.AssetTypeEnum
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
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

private const val TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailViewModelTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var server: MockWebServer
  private lateinit var repository: ImmichRepository

  // Held closed to keep GET /assets/a1 (the EXIF fetch) in flight for as long as a test needs.
  private val assetGate = CountDownLatch(1)
  private val assetRequestArrived = CountDownLatch(1)
  private val assetGets = AtomicInteger()
  private var assetResponse = { MockResponse().setBody(exifJson(isFavorite = false)) }

  @Before
  fun setUp() {
    Dispatchers.setMain(Dispatchers.Unconfined)
    server = MockWebServer()
    server.dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
          when {
            request.method == "GET" && request.path == "/api/assets/a1" -> {
              assetGets.incrementAndGet()
              assetRequestArrived.countDown()
              assetGate.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
              assetResponse()
            }
            request.method == "PUT" && request.path == "/api/assets/a1" -> {
              MockResponse().setBody("")
            }
            else -> {
              MockResponse().setResponseCode(404)
            }
          }
      }
    server.start()
    val settingsStore =
      SettingsStore(
        PreferenceDataStoreFactory.create(
          produceFile = { tempFolder.newFile("settings.preferences_pb") }
        ),
        FakeApiKeyCipher(),
      )
    runBlocking {
      settingsStore.setServerUrl(server.url("/").toString())
      settingsStore.setApiKey("test-api-key")
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
    assetGate.countDown()
    server.shutdown()
    Dispatchers.resetMain()
  }

  private fun exifJson(isFavorite: Boolean) =
    """{"id": "a1", "type": "IMAGE", "originalFileName": "a1.jpg", "isFavorite": $isFavorite,""" +
      """ "localDateTime": "2026-01-01T00:00:00Z", "exifInfo": {"make": "Fujifilm"}}"""

  private fun asset(id: String) =
    AssetDto(
      id = id,
      type = AssetTypeEnum.IMAGE,
      originalFileName = "$id.jpg",
      localDateTime = "2026-01-01T00:00:00Z",
    )

  private fun page(vararg ids: String) =
    ImmichResult.Success(TimelinePage(items = ids.map(::asset), nextPage = null))

  private fun viewModel(fetch: suspend (Int?) -> ImmichResult<TimelinePage> = { page("a1") }) =
    AssetDetailViewModel(repository, "a1", fetch)

  private suspend fun AssetDetailViewModel.awaitLoaded(
    predicate: (AssetDetailUiState.Loaded) -> Boolean = { true }
  ): AssetDetailUiState.Loaded =
    withTimeout(TIMEOUT_MS) {
      uiState.first { it is AssetDetailUiState.Loaded && predicate(it) }
        as AssetDetailUiState.Loaded
    }

  @Test
  fun `the EXIF fetch starts while the sibling list is still loading`() = runBlocking {
    val list = CompletableDeferred<ImmichResult<TimelinePage>>()
    val viewModel = viewModel { list.await() }

    assertTrue(assetRequestArrived.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
    assertEquals(AssetDetailUiState.Loading, viewModel.uiState.value)

    list.complete(page("a1"))
    assetGate.countDown()
    assertEquals(
      "Fujifilm",
      viewModel.awaitLoaded { it.asset.exifInfo != null }.asset.exifInfo?.make,
    )
  }

  @Test
  fun `Loaded is shown without waiting for the EXIF fetch, which merges in later`() = runBlocking {
    val viewModel = viewModel()

    val early = viewModel.awaitLoaded()
    assertTrue(assetRequestArrived.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
    assertNull(early.asset.exifInfo)

    assetGate.countDown()
    val merged = viewModel.awaitLoaded { it.asset.exifInfo != null }
    assertEquals("Fujifilm", merged.asset.exifInfo?.make)
    assertEquals(1, assetGets.get())
  }

  @Test
  fun `late EXIF merges into the opened asset by id after the user paged away`() = runBlocking {
    val viewModel = viewModel { page("a0", "a1", "a2") }
    viewModel.awaitLoaded()

    viewModel.onPageSettled(2).join()
    assetGate.countDown()

    val merged = viewModel.awaitLoaded { it.assets[1].exifInfo != null }
    assertEquals(2, merged.currentIndex)
    assertNull(merged.assets[0].exifInfo)
    assertNull(merged.assets[2].exifInfo)
  }

  @Test
  fun `a favorite toggled before EXIF arrives is not reverted by it`() = runBlocking {
    val viewModel = viewModel()
    viewModel.awaitLoaded()

    viewModel.toggleFavorite().join()
    assertTrue(viewModel.awaitLoaded().asset.isFavorite)

    assetGate.countDown() // the late GET still says isFavorite = false
    val merged = viewModel.awaitLoaded { it.asset.exifInfo != null }
    assertTrue(merged.asset.isFavorite)
  }

  @Test
  fun `a failed EXIF fetch keeps the plain list entry`() = runBlocking {
    assetResponse = { MockResponse().setResponseCode(500) }
    val viewModel = viewModel()
    viewModel.awaitLoaded()

    assetGate.countDown()
    withTimeout(TIMEOUT_MS) { viewModel.load().join() }

    val state = viewModel.awaitLoaded()
    assertNull(state.asset.exifInfo)
    assertEquals(listOf("a1"), state.assets.map { it.id })
  }

  @Test
  fun `an asset missing from the list falls back to the single fetch without requesting it twice`() =
    runBlocking {
      assetGate.countDown()
      val viewModel = viewModel { page("other") }

      val state = viewModel.awaitLoaded()

      assertEquals(listOf("a1"), state.assets.map { it.id })
      assertEquals("Fujifilm", state.asset.exifInfo?.make)
      assertEquals(1, assetGets.get())
    }

  @Test
  fun `a failed single fetch with no usable list is an error`() = runBlocking {
    assetGate.countDown()
    assetResponse = { MockResponse().setResponseCode(500) }
    val viewModel = viewModel {
      ImmichResult.Failure(fi.nikosavola.immichwear.data.ImmichError.NotConfigured)
    }

    val state =
      withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it is AssetDetailUiState.Error } }

    assertTrue(state is AssetDetailUiState.Error)
  }
}
