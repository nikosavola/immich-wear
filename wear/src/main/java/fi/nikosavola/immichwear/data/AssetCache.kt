package fi.nikosavola.immichwear.data

import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.immichJson
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

/**
 * Best-effort disk cache for the most recent page of a paginated asset feed (timeline, favorites),
 * so the grid isn't empty on a cold start with no connectivity - see [ImmichRepository]. Every
 * operation swallows its own I/O/serialization failures: a corrupt or missing cache should fall
 * back to "nothing cached", never crash or surface as a user-facing error.
 */
interface AssetCache {
  suspend fun save(key: String, items: List<AssetDto>)

  /** Null if nothing is cached for [key], or the cached data can't be read back. */
  suspend fun load(key: String): List<AssetDto>?

  /**
   * Removes only [key]'s entry, unlike [clear]. Used when a live fetch authoritatively reports an
   * empty feed - see [ImmichRepository.cachedFirstPage] - so a stale non-empty entry from before
   * the user deleted/unfavorited everything can't outlive that answer and resurface later while
   * offline.
   */
  suspend fun remove(key: String)

  /**
   * Called on sign-out and on connecting to a (possibly different) server - see
   * [ImmichRepository] - so one account's cached photos never surface under another.
   */
  suspend fun clear()
}

/**
 * Stores each key as its own JSON file under [cacheDir], which callers must not share with other
 * unrelated cached data.
 */
class FileAssetCache(private val cacheDir: File) : AssetCache {
  // Every function below catches only IOException/SerializationException, never a blanket
  // Throwable/Exception: CancellationException must always propagate (see runCatchingImmich for
  // the same convention elsewhere in this repo) or a cancelled caller would see a silent "nothing
  // cached" instead of actually being cancelled.
  override suspend fun save(key: String, items: List<AssetDto>): Unit =
    withContext(Dispatchers.IO) {
      try {
        cacheDir.mkdirs()
        // file(key) validates the key - do this before touching any File, including the temp one
        // below, so an invalid key can't slip through via the temp-file path.
        val destination = file(key)
        // Write to a temp file and rename over the real one, rather than writeText (truncate +
        // write) directly to it: a concurrent save/load on the same key (plausible - Home's hero
        // fetch, the tile service, and a detail-screen open can all race) or a process death
        // mid-write would otherwise risk a load() reading a half-written, corrupt file.
        val tempFile = File(destination.parentFile, "${destination.name}.${UUID.randomUUID()}.tmp")
        tempFile.writeText(immichJson.encodeToString(items))
        // renameTo returns false rather than throwing on failure. On Android's own Linux kernel
        // this already atomically replaces an existing destination, but File.renameTo isn't
        // guaranteed to do that on every JVM host (e.g. Windows), so delete-then-retry once before
        // giving up - and clean up the temp file ourselves either way, since nothing else will.
        if (!tempFile.renameTo(destination)) {
          destination.delete()
          if (!tempFile.renameTo(destination)) tempFile.delete()
        }
      } catch (e: IOException) {
        // Best-effort: a failed save just means the next offline fallback has nothing to load.
      } catch (e: SerializationException) {
        // Ditto - encodeToString shouldn't realistically fail for these plain-data DTOs, but a
        // save must never crash a live fetch just because caching it didn't work.
      }
    }

  override suspend fun load(key: String): List<AssetDto>? =
    withContext(Dispatchers.IO) {
      try {
        immichJson.decodeFromString<List<AssetDto>>(file(key).readText())
      } catch (e: IOException) {
        null
      } catch (e: SerializationException) {
        null
      }
    }

  override suspend fun remove(key: String): Unit =
    withContext(Dispatchers.IO) {
      // File.delete() returns false rather than throwing (e.g. if the key was never cached), so
      // there's nothing to catch here - a no-op removal is exactly the desired outcome.
      file(key).delete()
    }

  override suspend fun clear(): Unit =
    withContext(Dispatchers.IO) {
      try {
        cacheDir.deleteRecursively()
      } catch (e: IOException) {
        // Best-effort: leftover files just mean stale-but-harmless disk usage.
      }
    }

  // Guards against path traversal (e.g. a future key like "../../settings") - every call site
  // today passes one of ImmichRepository's own hardcoded cache-key constants, but this fails fast
  // rather than silently reading/writing outside cacheDir if that ever stops being true.
  private fun file(key: String): File {
    require(key.isNotEmpty() && key.all { it.isLetterOrDigit() }) { "Invalid cache key: $key" }
    return File(cacheDir, "$key.json")
  }
}

/**
 * No caching, no offline fallback - [ImmichRepository]'s default so most tests and call sites that
 * don't care about caching don't need to wire up storage.
 */
object NoOpAssetCache : AssetCache {
  override suspend fun save(key: String, items: List<AssetDto>) = Unit

  override suspend fun load(key: String): List<AssetDto>? = null

  override suspend fun remove(key: String) = Unit

  override suspend fun clear() = Unit
}
