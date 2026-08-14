package fi.nikosavola.immichwear.tile

import android.graphics.Bitmap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import coil3.BitmapImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import fi.nikosavola.immichwear.ImmichApp
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.data.api.AssetThumbnailSize
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.thumbnailUrl
import fi.nikosavola.immichwear.di.AppContainer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// v1 scope: always a random photo from Favorites, falling back to the recent-photos timeline if
// the user has no favorites yet. Choosing a specific photo or a specific album is a natural
// follow-up that needs its own tile configuration surface - out of scope for this pass.
private val FRESHNESS_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(1)
private const val TILE_IMAGE_SIZE_PX = 300
private const val PHOTO_RESOURCE_ID_PREFIX = "photo:"

// The server's thumbnail isn't guaranteed to be square, and the tile's InlineImageResource has one
// fixed width/height - stretching a non-square source with createScaledBitmap would distort it, so
// crop to the largest centered square first (a "zoomed to fill" crop, not a stretch).
internal fun centerCropSquare(bitmap: Bitmap): Bitmap {
  val side = minOf(bitmap.width, bitmap.height)
  val x = (bitmap.width - side) / 2
  val y = (bitmap.height - side) / 2
  return Bitmap.createBitmap(bitmap, x, y, side, side)
}

/**
 * A swipeable Tile surface showing a random favorited photo (or, absent any favorites, a random
 * recent photo). Bound on demand by the system, possibly with the main app process already evicted,
 * so settings and photos are always read fresh here rather than trusted from any in-memory
 * ViewModel.
 */
class ImmichTileService : TileService() {
  // SupervisorJob: an exception from one onTileRequest's coroutine must not cancel this scope's
  // Job and silently take every subsequent tile refresh down with it.
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  // Set by onTileRequest just before it returns, read moments later by onTileResourcesRequest for
  // the same refresh cycle - the system always pairs the two calls per bind, so a single mutable
  // field (not a cache keyed by request) is enough to hand the picked photo's bytes across.
  @Volatile private var pendingPhoto: PendingPhoto? = null

  private class PendingPhoto(val resourceId: String, val rgb565: ByteArray)

  override fun onTileRequest(
    requestParams: RequestBuilders.TileRequest
  ): ListenableFuture<TileBuilders.Tile> {
    val future = CompletableListenableFuture<TileBuilders.Tile>()
    val appContainer = (applicationContext as ImmichApp).appContainer
    // LAZY, plus wiring future.onCancel before starting: guarantees the Job is assigned before the
    // coroutine body can run, so a cancel() racing the launch can never see a null job.
    val job =
      serviceScope.launch(start = CoroutineStart.LAZY) {
        try {
          future.set(buildTile(appContainer))
        } catch (e: CancellationException) {
          throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
          // Deliberately generic: buildTile can throw from OkHttp/Coil decoding, from the
          // protolayout builders, or from anything else in its call graph, and any of those must
          // complete the future exceptionally rather than escape this launch and crash the whole
          // app process from a background tile refresh.
          future.setException(e)
        }
      }
    future.onCancel = { job.cancel() }
    job.start()
    return future
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onTileResourcesRequest(
    requestParams: RequestBuilders.ResourcesRequest
  ): ListenableFuture<ResourceBuilders.Resources> {
    val future = CompletableListenableFuture<ResourceBuilders.Resources>()
    val photo = pendingPhoto
    val resourcesBuilder =
      ResourceBuilders.Resources.Builder().setVersion(photo?.resourceId.orEmpty())
    if (photo != null) {
      resourcesBuilder.addIdToImageMapping(
        photo.resourceId,
        inlineImage(photo.rgb565, TILE_IMAGE_SIZE_PX),
      )
    }
    future.set(resourcesBuilder.build())
    return future
  }

  private suspend fun buildTile(appContainer: AppContainer): TileBuilders.Tile {
    val settings = appContainer.settingsStore.currentSettings()
    val rootLayout =
      if (settings.serverUrl == null || settings.apiKey == null) {
        pendingPhoto = null
        messageLayout(this, getString(R.string.home_not_connected))
      } else {
        val asset = pickRandomAsset(appContainer)
        val bitmap = asset?.let { fetchThumbnail(appContainer, it) }
        if (asset != null && bitmap != null) {
          val resourceId = "$PHOTO_RESOURCE_ID_PREFIX${asset.id}"
          pendingPhoto = PendingPhoto(resourceId, toRgb565Bytes(bitmap))
          photoLayout(this, resourceId)
        } else {
          pendingPhoto = null
          messageLayout(this, getString(R.string.error_offline))
        }
      }

    return TileBuilders.Tile.Builder()
      .setResourcesVersion(pendingPhoto?.resourceId.orEmpty())
      .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
      .setTileTimeline(
        TimelineBuilders.Timeline.Builder()
          .addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
              .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootLayout).build())
              .build()
          )
          .build()
      )
      .build()
  }

  // Null if fetching both favorites and the timeline failed, or the library is empty.
  private suspend fun pickRandomAsset(appContainer: AppContainer): AssetDto? {
    val favorites = randomFrom(appContainer.repository.favorites())
    return favorites ?: randomFrom(appContainer.repository.timeline())
  }

  private fun randomFrom(result: ImmichResult<TimelinePage>): AssetDto? =
    when (result) {
      is ImmichResult.Success -> result.value.items.randomOrNull()
      is ImmichResult.Failure -> null
    }

  private suspend fun fetchThumbnail(appContainer: AppContainer, asset: AssetDto): Bitmap? {
    val request =
      ImageRequest.Builder(this).data(thumbnailUrl(asset.id, AssetThumbnailSize.THUMBNAIL)).build()
    val result = appContainer.imageLoader.execute(request)
    val bitmap = ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap ?: return null
    return Bitmap.createScaledBitmap(
      centerCropSquare(bitmap),
      TILE_IMAGE_SIZE_PX,
      TILE_IMAGE_SIZE_PX,
      true,
    )
  }

  /**
   * Minimal settable [ListenableFuture]: this module only has Guava's API-only `listenablefuture`
   * artifact on its classpath, not the full Guava library that ships
   * [com.google.common.util.concurrent.SettableFuture], so bridging a suspend result into
   * TileService's Future-based contract needs a small local implementation instead. Backed by
   * [CompletableFuture] rather than reinventing listener bookkeeping.
   */
  private class CompletableListenableFuture<T> : ListenableFuture<T> {
    private val delegate = CompletableFuture<T>()

    // Set by the caller right after construction, before this future is returned to the system:
    // lets cancel() actually stop the in-flight coroutine doing the real work, not just the
    // CompletableFuture wrapper around it.
    var onCancel: () -> Unit = {}

    fun set(value: T) {
      delegate.complete(value)
    }

    fun setException(t: Throwable) {
      delegate.completeExceptionally(t)
    }

    override fun addListener(listener: Runnable, executor: Executor) {
      delegate.whenCompleteAsync({ _, _ -> listener.run() }, executor)
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
      val cancelled = delegate.cancel(mayInterruptIfRunning)
      if (cancelled) onCancel()
      return cancelled
    }

    override fun isCancelled(): Boolean = delegate.isCancelled

    override fun isDone(): Boolean = delegate.isDone

    override fun get(): T = delegate.get()

    override fun get(timeout: Long, unit: TimeUnit): T = delegate.get(timeout, unit)
  }
}
