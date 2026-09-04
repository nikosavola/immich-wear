package fi.nikosavola.immichwear.data

import fi.nikosavola.immichwear.data.api.ImmichApi
import fi.nikosavola.immichwear.data.api.createImmichClients
import fi.nikosavola.immichwear.data.api.dto.AlbumDto
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.AssetStatsResponseDto
import fi.nikosavola.immichwear.data.api.dto.MemoryDto
import fi.nikosavola.immichwear.data.api.dto.MetadataSearchRequest
import fi.nikosavola.immichwear.data.api.dto.SearchAssetResponse
import fi.nikosavola.immichwear.data.api.dto.UpdateAssetRequest
import fi.nikosavola.immichwear.data.api.dto.UserDto
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_SERVER_ERROR = 500

// Small enough that a single page renders quickly on a watch radio, large enough that scrolling
// to the bottom of the first page is uncommon.
internal const val TIMELINE_PAGE_SIZE = 30

/**
 * One page of the recent-photos timeline. [nextPage] is null when there is nothing more to load.
 */
data class TimelinePage(val items: List<AssetDto>, val nextPage: Int?)

/**
 * The single repository for Immich data: wraps [api] with settings-backed server address/identity.
 * Every public function returns [ImmichResult] instead of throwing for the expected failure modes
 * (auth, offline, parse). Coroutine cancellation is the one exception: every catch block below
 * rethrows [CancellationException] before anything else, so a cancelled caller sees cancellation,
 * not a wrapped "offline" failure.
 *
 * [settingsPrimed] is awaited before every request that depends on settings, so a cold start can't
 * race [api]'s synchronous credential suppliers - see [fi.nikosavola.immichwear.di.AppContainer].
 * Defaults to an already-completed value for callers (mainly tests) that don't need to prime
 * anything.
 *
 * [assetCache] backs [timeline] and [favorites] with a disk-based offline fallback for their first
 * page only - see [cachedFirstPage]. Defaults to [NoOpAssetCache] for callers that don't need one.
 */
class ImmichRepository(
  private val api: ImmichApi,
  private val settingsStore: SettingsStore,
  private val assetCache: AssetCache = NoOpAssetCache,
  private val settingsPrimed: Deferred<Settings> = CompletableDeferred(Settings()),
) {
  // Bumped by clearCache() (sign-out, connecting to a - possibly different - server) so a fetch
  // already in flight for the previous account can't write its response into the cache after
  // clearing: cachedFirstPage captures the generation before fetching and skips the save if it has
  // since moved on. An AtomicInteger, not a plain var, since ViewModels on different screens can
  // call timeline()/favorites() concurrently from different coroutines.
  private val cacheGeneration = AtomicInteger()

  // Guards clearCache() and cachedFirstPage's generation-check-then-save as one critical section:
  // without this, a save() could still start after the check saw a matching generation but finish
  // after a concurrent clearCache() already ran, recreating the very cache entry that clear() just
  // deleted. Never held across fetch() itself - only the network call is allowed to run unlocked,
  // so unrelated fetches on other screens don't serialize behind each other.
  private val cacheLock = Mutex()

  /**
   * Validates [serverUrlInput] and [apiKey] with `GET /users/me` against a throwaway client before
   * persisting anything, so a rejected or offline attempt never touches [settingsStore].
   */
  suspend fun connect(serverUrlInput: String, apiKey: String): ImmichResult<UserDto> {
    val normalizedUrl =
      normalizeServerUrl(serverUrlInput)
        ?: return ImmichResult.Failure(ImmichError.InvalidServerUrl)
    // A phone-clipboard paste routinely carries a trailing newline, which OkHttp rejects as an
    // invalid header value - surfacing as a confusing "offline" error for what is really a
    // whitespace problem.
    val trimmedApiKey = apiKey.trim()

    val probe = createImmichClients(apiKey = { trimmedApiKey }, serverBaseUrl = { normalizedUrl })
    val userResult = runCatchingImmich { probe.api.getCurrentUser() }
    if (userResult is ImmichResult.Success) {
      // A different account's cached photos must never surface as this account's offline
      // fallback, even briefly before the first real fetch completes.
      clearCache()
      settingsStore.setServerUrl(normalizedUrl)
      settingsStore.setApiKey(trimmedApiKey)
      settingsStore.setEmail(userResult.value.email)
    }
    return userResult
  }

  suspend fun signOut() {
    clearCache()
    settingsStore.clear()
  }

  private suspend fun clearCache() {
    cacheLock.withLock {
      cacheGeneration.incrementAndGet()
      assetCache.clear()
    }
  }

  /**
   * Fetches one page of the recent-photos timeline, newest first. [page] is the opaque page number
   * from a previous [TimelinePage.nextPage]; null fetches the first page.
   *
   * The first page is cached to disk on success and served back (with
   * [ImmichResult.Success.fromCache] set) if the live fetch fails while offline - see
   * [cachedFirstPage].
   */
  suspend fun timeline(page: Int? = null): ImmichResult<TimelinePage> =
    cachedFirstPage(cacheKey = TIMELINE_CACHE_KEY, page = page) {
      configured {
        api
          .searchMetadata(MetadataSearchRequest(size = TIMELINE_PAGE_SIZE, page = page))
          .assets
          .toTimelinePage()
      }
    }

  suspend fun albums(): ImmichResult<List<AlbumDto>> = configured { api.getAlbums() }

  suspend fun album(albumId: String): ImmichResult<AlbumDto> = configured { api.getAlbum(albumId) }

  /**
   * Fetches one page of an album's assets, newest first. The album-by-id endpoint does not return
   * assets inline on this server version, so this scopes the same search used by [timeline] with
   * `albumIds`.
   */
  suspend fun albumAssets(albumId: String, page: Int? = null): ImmichResult<TimelinePage> =
    configured {
      api
        .searchMetadata(
          MetadataSearchRequest(size = TIMELINE_PAGE_SIZE, albumIds = listOf(albumId), page = page)
        )
        .assets
        .toTimelinePage()
    }

  /** Fetches one page of favorited assets, newest first. Cached like [timeline]. */
  suspend fun favorites(page: Int? = null): ImmichResult<TimelinePage> =
    cachedFirstPage(cacheKey = FAVORITES_CACHE_KEY, page = page) {
      configured {
        api
          .searchMetadata(
            MetadataSearchRequest(size = TIMELINE_PAGE_SIZE, isFavorite = true, page = page)
          )
          .assets
          .toTimelinePage()
      }
    }

  /**
   * Today's "on this day" memories - one entry per past year with a match, each carrying its own
   * assets. Not paginated: the server returns the whole day's set in one response.
   */
  suspend fun memories(): ImmichResult<List<MemoryDto>> = configured {
    api.getMemories(LocalDate.now().toString())
  }

  /** Library-wide photo/video counts, e.g. for the companion phone app's post-login summary. */
  suspend fun assetStatistics(): ImmichResult<AssetStatsResponseDto> = configured {
    api.getAssetStatistics()
  }

  /** Fetches full metadata for one asset, including its current favorite state. */
  suspend fun asset(assetId: String): ImmichResult<AssetDto> = configured {
    api.getAssetInfo(assetId)
  }

  suspend fun setFavorite(assetId: String, isFavorite: Boolean): ImmichResult<Unit> = configured {
    api.updateAsset(assetId, UpdateAssetRequest(isFavorite))
  }

  private suspend fun <T> configured(block: suspend () -> T): ImmichResult<T> {
    settingsPrimed.await()
    val settings = settingsStore.currentSettings()
    if (settings.serverUrl == null || settings.apiKey == null) {
      return ImmichResult.Failure(ImmichError.NotConfigured)
    }
    return runCatchingImmich(block)
  }

  // Only the first page (page == null) is ever cached or falls back: a failed load-more should
  // not silently rewind the grid to stale cached content the user has already scrolled past. A
  // cache hit forces TimelinePage.nextPage to null, since a cached page can't be paginated further.
  // An empty page is never saved but instead invalidates any existing entry (e.g. every favorite
  // got removed), and an empty (or missing) cache is always treated as a miss: an account with
  // zero favorites would otherwise "successfully" fall back to a stale non-empty list while
  // offline, hiding both the offline indicator and the retry action a real failure gets.
  private suspend fun cachedFirstPage(
    cacheKey: String,
    page: Int?,
    fetch: suspend () -> ImmichResult<TimelinePage>,
  ): ImmichResult<TimelinePage> {
    if (page != null) return fetch()
    val generationBeforeFetch = cacheGeneration.get()
    val result = fetch()
    return when {
      result is ImmichResult.Success -> {
        // A sign-out/reconnect that ran while this fetch was in flight already cleared the cache
        // for a reason - writing this (possibly different account's) response back in would defeat
        // that. Locked jointly with clearCache() so a clear can't land between this check and the
        // save/remove actually completing.
        cacheLock.withLock {
          if (cacheGeneration.get() == generationBeforeFetch) {
            if (result.value.items.isNotEmpty()) {
              assetCache.save(cacheKey, result.value.items)
            } else {
              // An authoritative "there's nothing here" must invalidate whatever was cached
              // before (e.g. every favorite got removed) - otherwise a later offline fallback
              // would resurrect items the server has already told us are gone.
              assetCache.remove(cacheKey)
            }
          }
        }
        result
      }
      result is ImmichResult.Failure && result.error.looksOffline -> {
        val cached = assetCache.load(cacheKey)?.takeIf { it.isNotEmpty() }
        if (cached != null) {
          ImmichResult.Success(TimelinePage(items = cached, nextPage = null), fromCache = true)
        } else {
          result
        }
      }
      else -> {
        result
      }
    }
  }

  private companion object {
    const val TIMELINE_CACHE_KEY = "timeline"
    const val FAVORITES_CACHE_KEY = "favorites"
  }
}

internal suspend fun <T> runCatchingImmich(block: suspend () -> T): ImmichResult<T> =
  try {
    ImmichResult.Success(block())
  } catch (e: CancellationException) {
    // Coroutine cancellation must propagate, never be swallowed as an API failure.
    throw e
  } catch (e: HttpException) {
    ImmichResult.Failure(httpError(e.code()))
  } catch (e: SerializationException) {
    ImmichResult.Failure(ImmichError.ParseError)
  } catch (e: IOException) {
    ImmichResult.Failure(ImmichError.Offline)
  }

internal fun httpError(code: Int): ImmichError =
  if (code == HTTP_UNAUTHORIZED) ImmichError.Unauthorized else ImmichError.Http(code)

// Besides an outright transport failure, a self-hosted server behind a flaky reverse proxy
// returning 5xx is, in practice, indistinguishable from "unreachable" to the person looking at it
// - both get the same offline-cache fallback and caption.
private val ImmichError.looksOffline: Boolean
  get() {
    val isServerError = this is ImmichError.Http && code >= HTTP_SERVER_ERROR
    return this == ImmichError.Offline || isServerError
  }

private fun SearchAssetResponse.toTimelinePage() =
  TimelinePage(items = items, nextPage = nextPage?.toIntOrNull())
