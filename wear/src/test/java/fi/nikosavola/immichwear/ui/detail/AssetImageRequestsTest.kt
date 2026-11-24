package fi.nikosavola.immichwear.ui.detail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.Image
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.target
import fi.nikosavola.immichwear.data.api.AssetThumbnailSize
import fi.nikosavola.immichwear.data.api.thumbnailUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssetImageRequestsTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val fetcher = RecordingFetcherFactory()
  private val loader = ImageLoader.Builder(context).components { add(fetcher) }.build()

  private fun loadGridThumbnail(assetId: String): SuccessResult {
    val request =
      ImageRequest.Builder(context)
        .data(thumbnailUrl(assetId, AssetThumbnailSize.THUMBNAIL))
        .size(250)
        .build()
    return runBlocking { loader.execute(request) } as SuccessResult
  }

  @Test
  fun `the thumbnail memory key helper matches the key Coil stores a grid thumbnail under`() {
    val result = loadGridThumbnail("a1")

    assertEquals(thumbnailMemoryCacheKey("a1"), result.memoryCacheKey)
    assertNotNull(loader.memoryCache?.get(thumbnailMemoryCacheKey("a1")))
  }

  @Test
  fun `a cached grid thumbnail is handed to the preview request as its placeholder`() {
    val thumbnail = loadGridThumbnail("a1")
    var placeholder: Image? = null
    val request =
      previewRequest(context, "a1").newBuilder().target(onStart = { placeholder = it }).build()

    runBlocking { loader.execute(request) }

    assertEquals(thumbnail.image, placeholder)
  }

  @Test
  fun `a preview request has no placeholder when the thumbnail is not cached`() {
    var placeholder: Image? = null
    val request =
      previewRequest(context, "a1").newBuilder().target(onStart = { placeholder = it }).build()

    runBlocking { loader.execute(request) }

    assertNull(placeholder)
  }

  @Test
  fun `prefetching a preview fetches it but leaves the memory cache alone`() {
    runBlocking { loader.execute(prefetchPreviewRequest(context, "a1")) }

    assertEquals(listOf(thumbnailUrl("a1", AssetThumbnailSize.PREVIEW)), fetcher.urls)
    assertEquals(0, loader.memoryCache?.keys?.size)
  }
}
