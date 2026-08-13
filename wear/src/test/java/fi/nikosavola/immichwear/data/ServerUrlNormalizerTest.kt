package fi.nikosavola.immichwear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlNormalizerTest {
  @Test
  fun `a bare host with no scheme defaults to https`() {
    assertEquals("https://immich.example.com/", normalizeServerUrl("immich.example.com"))
  }

  @Test
  fun `an explicit https url with a trailing slash is unchanged`() {
    assertEquals("https://immich.example.com/", normalizeServerUrl("https://immich.example.com/"))
  }

  @Test
  fun `an explicit https url without a trailing slash gains one`() {
    assertEquals("https://immich.example.com/", normalizeServerUrl("https://immich.example.com"))
  }

  @Test
  fun `an explicit http scheme is preserved, not upgraded`() {
    assertEquals("http://192.168.1.5:2283/", normalizeServerUrl("http://192.168.1.5:2283"))
  }

  @Test
  fun `a trailing api suffix is stripped`() {
    assertEquals("http://192.168.1.5:2283/", normalizeServerUrl("http://192.168.1.5:2283/api"))
  }

  @Test
  fun `a trailing api suffix with a trailing slash is stripped`() {
    assertEquals("http://192.168.1.5:2283/", normalizeServerUrl("http://192.168.1.5:2283/api/"))
  }

  @Test
  fun `a reverse-proxy path prefix survives api stripping`() {
    assertEquals(
      "https://example.com/immich/",
      normalizeServerUrl("https://example.com/immich/api"),
    )
  }

  @Test
  fun `a path segment that merely contains api as a substring is not stripped`() {
    assertEquals("https://example.com/apiary/", normalizeServerUrl("https://example.com/apiary"))
  }

  @Test
  fun `surrounding whitespace is trimmed`() {
    assertEquals("https://immich.example.com/", normalizeServerUrl("  immich.example.com  "))
  }

  @Test
  fun `blank input is invalid`() {
    assertNull(normalizeServerUrl("   "))
  }

  @Test
  fun `empty input is invalid`() {
    assertNull(normalizeServerUrl(""))
  }

  @Test
  fun `a non-http scheme is invalid`() {
    assertNull(normalizeServerUrl("ftp://immich.example.com"))
  }

  @Test
  fun `garbage input is invalid`() {
    assertNull(normalizeServerUrl("not a url at all"))
  }

  @Test
  fun `a query string is dropped`() {
    assertEquals(
      "https://immich.example.com/",
      normalizeServerUrl("https://immich.example.com/?foo=bar"),
    )
  }
}
