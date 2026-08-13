package fi.nikosavola.immichwear.data.api

import fi.nikosavola.immichwear.data.api.dto.MetadataSearchRequest
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

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
}
