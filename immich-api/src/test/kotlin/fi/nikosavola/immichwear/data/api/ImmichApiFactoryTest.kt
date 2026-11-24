package fi.nikosavola.immichwear.data.api

import fi.nikosavola.immichwear.data.api.dto.MetadataSearchRequest
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

private const val API_KEY = "test-api-key"

class ImmichApiFactoryTest {
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

  @Test
  fun `ping hits the server root with the api key header`() {
    runTest {
      val clients =
        createImmichClients(apiKey = { API_KEY }, serverBaseUrl = { server.url("/").toString() })
      server.enqueue(MockResponse().setBody("""{"res": "pong"}"""))

      val response = clients.api.ping()

      assertEquals("pong", response.res)
      val recorded = server.takeRequest()
      assertEquals("GET", recorded.method)
      assertEquals("/api/server/ping", recorded.path)
      assertEquals(API_KEY, recorded.getHeader("x-api-key"))
    }
  }

  @Test
  fun `null api key omits the header`() {
    runTest {
      val clients =
        createImmichClients(apiKey = { null }, serverBaseUrl = { server.url("/").toString() })
      server.enqueue(MockResponse().setBody("""{"res": "pong"}"""))

      clients.api.ping()

      assertNull(server.takeRequest().getHeader("x-api-key"))
    }
  }

  @Test
  fun `a reverse-proxy path prefix on the server url is preserved`() {
    runTest {
      val clients =
        createImmichClients(
          apiKey = { API_KEY },
          serverBaseUrl = { server.url("/immich/").toString() },
        )
      server.enqueue(MockResponse().setBody("""{"res": "pong"}"""))

      clients.api.ping()

      assertEquals("/immich/api/server/ping", server.takeRequest().path)
    }
  }

  @Test
  fun `an unconfigured server url leaves the request pointed at the unreachable placeholder host`() {
    runTest {
      val clients = createImmichClients(apiKey = { API_KEY }, serverBaseUrl = { null })

      try {
        clients.api.ping()
        error("expected a failure resolving the placeholder host")
      } catch (e: IOException) {
        // Expected: PLACEHOLDER_BASE_URL's host is not a real address.
      }
    }
  }

  @Test
  fun `searchMetadata posts the request body to search-metadata`() {
    runTest {
      val clients =
        createImmichClients(apiKey = { API_KEY }, serverBaseUrl = { server.url("/").toString() })
      server.enqueue(MockResponse().setBody("""{"assets": {"items": [], "nextPage": null}}"""))

      val request = MetadataSearchRequest(size = 30)
      val response = clients.api.searchMetadata(request)

      assertEquals(0, response.assets.items.size)
      val recorded = server.takeRequest()
      assertEquals("POST", recorded.method)
      assertEquals("/api/search/metadata", recorded.path)
      assertEquals("""{"size":30,"order":"desc"}""", recorded.body.readUtf8())
    }
  }

  @Test
  fun `a redirect is surfaced as an http error instead of followed`() {
    runTest {
      val clients =
        createImmichClients(apiKey = { API_KEY }, serverBaseUrl = { server.url("/").toString() })
      server.enqueue(
        MockResponse()
          .setResponseCode(302)
          .setHeader("Location", server.url("/redirected").toString())
      )
      // Would be served next if the client incorrectly followed the redirect above.
      server.enqueue(MockResponse().setBody("""{"res": "pong"}"""))

      try {
        clients.api.ping()
        error("expected the 302 to surface as an HttpException")
      } catch (e: HttpException) {
        assertEquals(302, e.code())
      }
      // Only the first request (the redirect response itself) should have gone out; following it
      // would carry the x-api-key header to whatever host Location names.
      assertEquals(1, server.requestCount)
    }
  }

  @Test
  fun `the image client does not follow redirects either`() {
    val clients =
      createImmichClients(apiKey = { API_KEY }, serverBaseUrl = { server.url("/").toString() })
    server.enqueue(
      MockResponse()
        .setResponseCode(302)
        .setHeader("Location", server.url("/redirected").toString())
    )
    server.enqueue(MockResponse().setBody("followed"))

    clients.okHttpClient
      .newCall(Request.Builder().url(server.url("/api/assets/x")).build())
      .execute()
      .use { assertEquals(302, it.code) }

    assertEquals(1, server.requestCount)
    assertEquals(API_KEY, server.takeRequest().getHeader("x-api-key"))
  }

  @Test
  fun `an api call is not queued behind a saturated image dispatcher`() {
    val release = CountDownLatch(1)
    val imageHits = CountDownLatch(IMAGE_HOST_LIMIT)
    server.dispatcher =
      object : okhttp3.mockwebserver.Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
          if (request.path?.startsWith("/api/assets/") == true) {
            imageHits.countDown()
            release.await(10, TimeUnit.SECONDS)
            return MockResponse().setBody("img")
          }
          return MockResponse().setBody("""{"res": "pong"}""")
        }
      }
    val clients =
      createImmichClients(apiKey = { API_KEY }, serverBaseUrl = { server.url("/").toString() })
    val imageClient = clients.okHttpClient
    val noop =
      object : Callback {
        override fun onFailure(call: Call, e: IOException) = Unit

        override fun onResponse(call: Call, response: Response) = response.close()
      }

    try {
      repeat(IMAGE_HOST_LIMIT + 1) { i ->
        // Same placeholder host as API calls, which is what the dispatcher's per-host limit sees.
        imageClient
          .newCall(
            Request.Builder().url(thumbnailUrl("asset-$i", AssetThumbnailSize.THUMBNAIL)).build()
          )
          .enqueue(noop)
      }
      assertTrue(imageHits.await(10, TimeUnit.SECONDS))
      assertEquals(IMAGE_HOST_LIMIT, imageClient.dispatcher.runningCallsCount())
      assertEquals(1, imageClient.dispatcher.queuedCallsCount())

      val response = runBlocking { withTimeout(5_000) { clients.api.ping() } }

      assertEquals("pong", response.res)
    } finally {
      release.countDown()
    }
  }
}

private const val IMAGE_HOST_LIMIT = 5
