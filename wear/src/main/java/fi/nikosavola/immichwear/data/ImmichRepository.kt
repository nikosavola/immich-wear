package fi.nikosavola.immichwear.data

import fi.nikosavola.immichwear.data.api.ImmichApi
import fi.nikosavola.immichwear.data.api.dto.AlbumDto
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.MetadataSearchRequest
import fi.nikosavola.immichwear.data.api.dto.SearchAssetResponse
import fi.nikosavola.immichwear.data.api.dto.UpdateAssetRequest
import fi.nikosavola.immichwear.data.api.dto.UserDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
 */
class ImmichRepository(private val api: ImmichApi, private val settingsStore: SettingsStore) {
  /**
   * Normalizes and persists [serverUrlInput] and [apiKey], then validates them with `GET
   * /users/me`. Never leaves an unvalidated or rejected server/key pair persisted - including if
   * the caller is cancelled (e.g. the Settings screen is swipe-dismissed) while the validation
   * request is still in flight: the rollback below runs under [NonCancellable] specifically so a
   * cancellation can't skip it and strand invalid credentials.
   */
  suspend fun connect(serverUrlInput: String, apiKey: String): ImmichResult<UserDto> {
    val normalizedUrl =
      normalizeServerUrl(serverUrlInput)
        ?: return ImmichResult.Failure(ImmichError.InvalidServerUrl)
    // A phone-clipboard paste routinely carries a trailing newline, which OkHttp rejects as an
    // invalid header value - surfacing as a confusing "offline" error for what is really a
    // whitespace problem.
    val trimmedApiKey = apiKey.trim()

    settingsStore.setServerUrl(normalizedUrl)
    settingsStore.setApiKey(trimmedApiKey)

    val userResult =
      try {
        runCatchingImmich { api.getCurrentUser() }
      } catch (e: CancellationException) {
        withContext(NonCancellable) {
          settingsStore.setServerUrl(null)
          settingsStore.setApiKey(null)
        }
        throw e
      }

    return when (userResult) {
      is ImmichResult.Failure -> {
        withContext(NonCancellable) {
          settingsStore.setServerUrl(null)
          settingsStore.setApiKey(null)
        }
        userResult
      }
      is ImmichResult.Success -> {
        settingsStore.setEmail(userResult.value.email)
        userResult
      }
    }
  }

  suspend fun signOut() {
    settingsStore.clear()
  }

  /**
   * Fetches one page of the recent-photos timeline, newest first. [page] is the opaque page number
   * from a previous [TimelinePage.nextPage]; null fetches the first page.
   */
  suspend fun timeline(page: Int? = null): ImmichResult<TimelinePage> =
    when (val configured = requireConfigured()) {
      is ImmichResult.Failure -> configured
      is ImmichResult.Success ->
        runCatchingImmich {
          api
            .searchMetadata(MetadataSearchRequest(size = TIMELINE_PAGE_SIZE, page = page))
            .assets
            .toTimelinePage()
        }
    }

  suspend fun albums(): ImmichResult<List<AlbumDto>> =
    when (val configured = requireConfigured()) {
      is ImmichResult.Failure -> configured
      is ImmichResult.Success -> runCatchingImmich { api.getAlbums() }
    }

  suspend fun album(albumId: String): ImmichResult<AlbumDto> =
    when (val configured = requireConfigured()) {
      is ImmichResult.Failure -> configured
      is ImmichResult.Success -> runCatchingImmich { api.getAlbum(albumId) }
    }

  /**
   * Fetches one page of an album's assets, newest first. The album-by-id endpoint does not return
   * assets inline on this server version, so this scopes the same search used by [timeline] with
   * `albumIds`.
   */
  suspend fun albumAssets(albumId: String, page: Int? = null): ImmichResult<TimelinePage> =
    when (val configured = requireConfigured()) {
      is ImmichResult.Failure -> configured
      is ImmichResult.Success ->
        runCatchingImmich {
          api
            .searchMetadata(
              MetadataSearchRequest(
                size = TIMELINE_PAGE_SIZE,
                albumIds = listOf(albumId),
                page = page,
              )
            )
            .assets
            .toTimelinePage()
        }
    }

  /** Fetches one page of favorited assets, newest first. */
  suspend fun favorites(page: Int? = null): ImmichResult<TimelinePage> =
    when (val configured = requireConfigured()) {
      is ImmichResult.Failure -> configured
      is ImmichResult.Success ->
        runCatchingImmich {
          api
            .searchMetadata(
              MetadataSearchRequest(size = TIMELINE_PAGE_SIZE, isFavorite = true, page = page)
            )
            .assets
            .toTimelinePage()
        }
    }

  /** Fetches full metadata for one asset, including its current favorite state. */
  suspend fun asset(assetId: String): ImmichResult<AssetDto> =
    when (val configured = requireConfigured()) {
      is ImmichResult.Failure -> configured
      is ImmichResult.Success -> runCatchingImmich { api.getAssetInfo(assetId) }
    }

  suspend fun setFavorite(assetId: String, isFavorite: Boolean): ImmichResult<Unit> =
    when (val configured = requireConfigured()) {
      is ImmichResult.Failure -> configured
      is ImmichResult.Success ->
        runCatchingImmich { api.updateAsset(assetId, UpdateAssetRequest(isFavorite)) }
    }

  private suspend fun requireConfigured(): ImmichResult<Unit> {
    val settings = settingsStore.currentSettings()
    return if (settings.serverUrl != null && settings.apiKey != null) {
      ImmichResult.Success(Unit)
    } else {
      ImmichResult.Failure(ImmichError.NotConfigured)
    }
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
