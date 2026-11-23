package fi.nikosavola.immichwear.ui.detail

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred

/**
 * Stands in for the network: records every URL Coil asks to fetch and returns a tiny bitmap. A URL
 * with an entry in [gates] stays pending until that gate completes.
 */
internal class RecordingFetcherFactory : Fetcher.Factory<Uri> {
  val urls = CopyOnWriteArrayList<String>()
  val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

  override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
    val url = data.toString()
    return object : Fetcher {
      override suspend fun fetch(): FetchResult {
        urls.add(url)
        gates[url]?.await()
        return ImageFetchResult(
          image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImage(),
          isSampled = false,
          dataSource = DataSource.NETWORK,
        )
      }
    }
  }
}
