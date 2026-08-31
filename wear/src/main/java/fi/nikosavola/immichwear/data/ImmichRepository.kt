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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

private const val HTTP_UNAUTHORIZED = 401

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
 */
class ImmichRepository(
  private val api: ImmichApi,
  private val settingsStore: SettingsStore,
  private val settingsPrimed: Deferred<Settings> = CompletableDeferred(Settings()),
) {
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
      settingsStore.setServerUrl(normalizedUrl)
      settingsStore.setApiKey(trimmedApiKey)
      settingsStore.setEmail(userResult.value.email)
    }
    return userResult
  }

  suspend fun signOut() {
    settingsStore.clear()
  }

  /**
   * Fetches one page of the recent-photos timeline, newest first. [page] is the opaque page number
   * from a previous [TimelinePage.nextPage]; null fetches the first page.
   */
  suspend fun timeline(page: Int? = null): ImmichResult<TimelinePage> = configured {
    api
      .searchMetadata(MetadataSearchRequest(size = TIMELINE_PAGE_SIZE, page = page))
      .assets
      .toTimelinePage()
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

  /** Fetches one page of favorited assets, newest first. */
  suspend fun favorites(page: Int? = null): ImmichResult<TimelinePage> = configured {
    api
      .searchMetadata(
        MetadataSearchRequest(size = TIMELINE_PAGE_SIZE, isFavorite = true, page = page)
      )
      .assets
      .toTimelinePage()
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

private fun SearchAssetResponse.toTimelinePage() =
  TimelinePage(items = items, nextPage = nextPage?.toIntOrNull())
