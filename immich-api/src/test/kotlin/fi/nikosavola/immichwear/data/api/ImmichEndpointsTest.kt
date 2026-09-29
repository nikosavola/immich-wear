package fi.nikosavola.immichwear.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the two wire values in [AssetThumbnailSize] and the URL shape [thumbnailUrl] produces - both
 * are part of what this module publishes, and a silent drift here would be a thumbnail request the
 * server rejects rather than a compile error.
 */
class ImmichEndpointsTest {
  @Test
  fun `thumbnail url pairs the placeholder host with the size's wire value`() {
    assertEquals(
      "http://immich.invalid/api/assets/asset-1/thumbnail?size=thumbnail",
      thumbnailUrl("asset-1", AssetThumbnailSize.THUMBNAIL),
    )
    assertEquals(
      "http://immich.invalid/api/assets/asset-1/thumbnail?size=preview",
      thumbnailUrl("asset-1", AssetThumbnailSize.PREVIEW),
    )
  }

  @Test
  fun `placeholder base url parses as an absolute http url`() {
    val parsed = requireNotNull(PLACEHOLDER_BASE_URL.toHttpUrlOrNull())
    assertEquals("immich.invalid", parsed.host)
  }
}
